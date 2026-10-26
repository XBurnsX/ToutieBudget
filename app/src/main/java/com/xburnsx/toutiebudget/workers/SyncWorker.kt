package com.xburnsx.toutiebudget.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.xburnsx.toutiebudget.data.room.daos.SyncJobDao
import com.xburnsx.toutiebudget.data.room.entities.SyncJob
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.di.UrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Worker de synchronisation : lit la file SyncJob et envoie les modifications vers PocketBase.
 *
 * - Un seul envoi à la fois dans l'application (verrou), sans jamais annuler un envoi en cours (SJ2).
 * - Les tâches d'un utilisateur ne partent qu'avec la session de ce même utilisateur (SJ7, LD7).
 * - Erreurs classées (SJ10) : réseau / 408 / 429 / 5xx = réessayées plus tard avec délai croissant ;
 *   400 / 403 / 404 / 422 = tâche bloquée, à corriger puis « Réessayer » manuellement.
 * - Chaque essai est noté (code, erreur nettoyée, nombre d'essais) sans le contenu de la tâche (SJ11).
 * - Ordre respecté par fiche : si une tâche échoue, les suivantes de la même fiche attendent (SJ5).
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val syncJobDao: SyncJobDao = AppModule.provideSyncJobDao(context)
    private val client = PocketBaseClient
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        .create()

    /** Résultat d'un envoi. [code] = code HTTP, null si erreur réseau. */
    private data class Resultat(val ok: Boolean, val code: Int?, val message: String?)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        verrou.withLock {
            try {
                envoyerFile()
            } catch (e: Exception) {
                Log.e(TAG, "Synchronisation interrompue (${e.javaClass.simpleName})")
                SyncWorkManager.planifierNouvelEssai(applicationContext, 1)
                Result.success()
            }
        }
    }

    private suspend fun envoyerFile(): Result {
        if (!isNetworkAvailable()) {
            // Le travail « réseau » se déclenchera au retour d'Internet
            SyncWorkManager.planifierSynchronisationAutomatique(applicationContext)
            return Result.success()
        }

        // Charger l'authentification sauvegardée (le worker peut tourner app fermée)
        client.chargerAuthentificationSauvegardee(applicationContext)
        val token = client.obtenirToken()
        val utilisateurId = client.obtenirUtilisateurConnecte()?.id
        if (token == null || utilisateurId == null) {
            // Pas de session : rien n'est envoyé ; la file attend la reconnexion de son utilisateur
            return Result.success()
        }
        val urlBase = UrlResolver.obtenirUrlActive()

        val dejaEssayees = mutableSetOf<String>()
        var echecsReessayables = 0
        var plusGrandNombreEssais = 0

        // Reprendre la file tant que de nouvelles tâches arrivent pendant l'envoi
        while (!isStopped) {
            val taches = syncJobDao.getTachesAEnvoyer(utilisateurId).filter { it.id !in dejaEssayees }
            if (taches.isEmpty()) break

            val fichesEnAttente = mutableSetOf<String>()
            for (tache in taches) {
                if (isStopped) break
                dejaEssayees += tache.id
                val fiche = cleFiche(tache)
                if (fiche != null && fiche in fichesEnAttente) continue // une tâche plus ancienne de cette fiche a échoué

                val resultat = try {
                    when (tache.action) {
                        "CREATE" -> traiterCreation(tache, urlBase, token)
                        "UPDATE" -> traiterMiseAJour(tache, urlBase, token)
                        "DELETE" -> traiterSuppression(tache, urlBase, token)
                        else -> Resultat(false, null, "Action inconnue : ${tache.action}")
                    }
                } catch (e: IOException) {
                    Resultat(false, null, "Réseau indisponible")
                } catch (e: Exception) {
                    Resultat(false, null, e.javaClass.simpleName)
                }

                val maintenant = System.currentTimeMillis()
                if (resultat.ok) {
                    syncJobDao.enregistrerEssai(tache.id, "COMPLETED", null, resultat.code, maintenant, false)
                    continue
                }

                if (fiche != null) fichesEnAttente += fiche
                val bloquee = estErreurPermanente(resultat.code, tache.action)
                syncJobDao.enregistrerEssai(
                    tache.id, "FAILED", resultat.message ?: "Erreur inconnue", resultat.code, maintenant, bloquee
                )
                if (!bloquee) {
                    echecsReessayables++
                    plusGrandNombreEssais = maxOf(plusGrandNombreEssais, tache.tentatives + 1)
                }
                if (resultat.code == 401) {
                    // Session expirée ou révoquée : inutile d'envoyer le reste avec ce jeton
                    return Result.success()
                }
            }
        }

        if (echecsReessayables > 0) {
            SyncWorkManager.planifierNouvelEssai(applicationContext, plusGrandNombreEssais)
        }
        return Result.success()
    }

    /** 400 / 403 / 404 / 422 ne se règlent pas en réessayant (SJ10). 401 = session, réessayée après reconnexion. */
    private fun estErreurPermanente(code: Int?, action: String): Boolean = when (code) {
        null, 401, 408, 429 -> false
        in 500..599 -> false
        404 -> action != "DELETE"
        else -> code in 400..499
    }

    /** Identifiant de la fiche touchée, pour garder l'ordre des tâches d'une même fiche. */
    private fun cleFiche(tache: SyncJob): String? =
        tache.recordId.ifBlank { idDepuisJson(tache.dataJson) ?: "" }.ifBlank { null }?.let { "${tache.type}/$it" }

    /**
     * Traite une tâche de création (CREATE)
     */
    private fun traiterCreation(syncJob: SyncJob, urlBase: String, token: String): Resultat {
        val collection = SyncCollections.collectionPourType(syncJob.type)
        val url = "$urlBase/api/collections/$collection/records"

        val request = Request.Builder()
            .url(url)
            .post(syncJob.dataJson.toRequestBody(JSON))
            .addHeader("Authorization", "Bearer $token")
            .build()
        val premier = executer(request)
        if (premier.ok) return premier

        // Une tâche CREATE peut être rejouée si le worker a été coupé après l'envoi
        // mais avant de marquer la tâche COMPLETED. Si la fiche existe déjà sur le
        // serveur (400 sur l'id), on applique les données en PATCH au lieu d'échouer en boucle (SJ1).
        if (premier.code == 400) {
            val recordId = syncJob.recordId.ifBlank { idDepuisJson(syncJob.dataJson) ?: "" }
            if (recordId.isNotBlank() && ficheExiste(urlBase, collection, recordId, token)) {
                val patch = Request.Builder()
                    .url("$url/$recordId")
                    .patch(syncJob.dataJson.toRequestBody(JSON))
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                return executer(patch)
            }
        }
        return premier
    }

    /**
     * Traite une tâche de mise à jour (UPDATE)
     */
    private fun traiterMiseAJour(syncJob: SyncJob, urlBase: String, token: String): Resultat {
        val collection = SyncCollections.collectionPourType(syncJob.type)
        // Anciennes tâches sans recordId : prendre l'ID du JSON
        val recordId = syncJob.recordId.ifBlank { idDepuisJson(syncJob.dataJson) ?: "" }
        if (recordId.isBlank()) return Resultat(false, 400, "Identifiant de fiche manquant")

        // Allocations : envoyer les valeurs calculées par Room telles quelles, sans les champs nuls
        val corps = if (syncJob.type == "ALLOCATION_MENSUELLE") {
            val dataMap = gson.fromJson(syncJob.dataJson, Map::class.java)
            gson.toJson(dataMap.filterValues { it != null })
        } else {
            syncJob.dataJson
        }

        val request = Request.Builder()
            .url("$urlBase/api/collections/$collection/records/$recordId")
            .patch(corps.toRequestBody(JSON))
            .addHeader("Authorization", "Bearer $token")
            .build()
        return executer(request)
    }

    /**
     * Traite une tâche de suppression (DELETE)
     */
    private fun traiterSuppression(syncJob: SyncJob, urlBase: String, token: String): Resultat {
        val collection = SyncCollections.collectionPourType(syncJob.type)
        val recordId = syncJob.recordId.ifBlank { idDepuisJson(syncJob.dataJson) ?: "" }
        if (recordId.isBlank()) return Resultat(false, 400, "Identifiant de fiche manquant")

        val request = Request.Builder()
            .url("$urlBase/api/collections/$collection/records/$recordId")
            .delete()
            .addHeader("Authorization", "Bearer $token")
            .build()
        val resultat = executer(request)
        // 404 = déjà supprimée sur le serveur (tâche rejouée) : le résultat voulu est atteint
        return if (resultat.code == 404) Resultat(true, 404, null) else resultat
    }

    /** Envoie la requête ; le message d'erreur garde seulement le texte de PocketBase, jamais le contenu envoyé. */
    private fun executer(request: Request): Resultat =
        httpClient.newCall(request).execute().use { reponse ->
            if (reponse.isSuccessful) {
                Resultat(true, reponse.code, null)
            } else {
                val message = try {
                    gson.fromJson(reponse.body?.string(), Map::class.java)?.get("message") as? String
                } catch (_: Exception) {
                    null
                }
                Resultat(false, reponse.code, "HTTP ${reponse.code}" + (message?.let { " : ${it.take(120)}" } ?: ""))
            }
        }

    private fun idDepuisJson(dataJson: String): String? = try {
        (gson.fromJson(dataJson, Map::class.java)["id"] as? String)?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun ficheExiste(urlBase: String, collection: String, recordId: String, token: String): Boolean {
        val request = Request.Builder()
            .url("$urlBase/api/collections/$collection/records/$recordId")
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()
        return try {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Vérifie si le réseau est disponible
     */
    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private val JSON = "application/json".toMediaType()

        /** Un seul envoi de la file à la fois dans tout le processus, quel que soit le déclencheur. */
        private val verrou = Mutex()
    }
}
