package com.xburnsx.toutiebudget.workers

import android.content.Context
// import android.util.Log
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
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Worker de synchronisation qui lit la table SyncJob et envoie les modifications vers Pocketbase
 * L'OUVRIER DE NUIT qui s'occupe de la liste de tâches !
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
    
    private val logTag = "SyncWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // 🚀 DÉBUT DE LA SYNCHRONISATION
            
            // Vérifier la connexion réseau
            if (!isNetworkAvailable()) {
                // ⚠️ Pas de connexion réseau - synchronisation reportée
                return@withContext Result.retry()
            }
            
            // 🚨 DEBUG : Afficher les informations de base
            
            // 🆕 CHARGER L'AUTHENTIFICATION SAUVEGARDÉE AVANT D'OBTENIR LE TOKEN
            // C'est crucial pour que le worker puisse accéder au token sauvegardé
            client.chargerAuthentificationSauvegardee(applicationContext)
            
            // Vérifier l'authentification
            val token = client.obtenirToken()
            if (token == null) {
                // ⚠️ Pas de token d'authentification - synchronisation reportée
                return@withContext Result.retry()
            }
            
            // ✅ Token d'authentification récupéré avec succès
            
            val urlBase = UrlResolver.obtenirUrlActive()
            
            // Récupérer tous les SyncJob en attente ET les échecs pour retry
            val syncJobs = syncJobDao.getPendingAndFailedSyncJobs()
            if (syncJobs.isEmpty()) {
                // ✅ Aucune tâche de synchronisation en attente ou à retenter
                return@withContext Result.success()
            }
            
            // 📋 ${syncJobs.size} tâches de synchronisation à traiter
            
            var successCount = 0
            var failureCount = 0
            
            // Traiter chaque tâche de synchronisation
            for (syncJob in syncJobs) {
                // Remplacé par un nouveau déclenchement : s'arrêter entre deux tâches,
                // le prochain worker reprendra les tâches restantes
                if (isStopped) break
                try {
                    // 🔄 Traitement de la tâche ${syncJob.id}: ${syncJob.type} - ${syncJob.action}
                    
                    val success = when (syncJob.action) {
                        "CREATE" -> traiterCreation(syncJob, urlBase, token)
                        "UPDATE" -> traiterMiseAJour(syncJob, urlBase, token)
                        "DELETE" -> traiterSuppression(syncJob, urlBase, token)
                        else -> {
                            // ⚠️ Action non reconnue: ${syncJob.action}
                            false
                        }
                    }
                    
                    if (success) {
                        // Marquer la tâche comme terminée
                        syncJobDao.updateSyncJobStatus(syncJob.id, "COMPLETED")
                        successCount++
                        // ✅ Tâche ${syncJob.id} synchronisée avec succès
                    } else {
                        // Marquer la tâche comme échouée
                        syncJobDao.updateSyncJobStatus(syncJob.id, "FAILED")
                        failureCount++
                        // ❌ Échec de la synchronisation de la tâche ${syncJob.id}
                    }
                    
                } catch (e: Exception) {
                    // ❌ Erreur lors du traitement de la tâche ${syncJob.id}
                    syncJobDao.updateSyncJobStatus(syncJob.id, "FAILED")
                    failureCount++
                }
            }
            
            // 🎉 SYNCHRONISATION TERMINÉE: $successCount succès, $failureCount échecs
            
            // 🚫 SUPPRESSION DU NETTOYAGE AUTOMATIQUE : L'utilisateur gère manuellement les tâches
            // Les tâches restent visibles dans l'interface avec les onglets par statut
            // 📋 Tâches conservées pour gestion manuelle par l'utilisateur
            
            // Si toutes les tâches ont réussi, on retourne success
            // Sinon, on retourne retry pour réessayer les tâches échouées
            return@withContext if (failureCount == 0) Result.success() else Result.retry()
            
        } catch (e: Exception) {
            // ❌ Erreur fatale lors de la synchronisation
            return@withContext Result.failure()
        }
    }
    
    /**
     * Traite une tâche de création (CREATE)
     */
    private suspend fun traiterCreation(syncJob: SyncJob, urlBase: String, token: String): Boolean {
        try {
            val collection = SyncCollections.collectionPourType(syncJob.type)
            
            val url = "$urlBase/api/collections/$collection/records"
            // 🔄 URL de création: $url (type: ${syncJob.type} → collection: $collection)
            
            // 🚨 DIAGNOSTIC CRITIQUE : Afficher les données JSON envoyées
            
            val requestBody = syncJob.dataJson.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()
            
            val response = httpClient.newCall(request).execute()
            val code = response.code
            response.close()
            if (response.isSuccessful) return true

            // Une tâche CREATE peut être rejouée si le worker a été coupé après l'envoi
            // mais avant de marquer la tâche COMPLETED. Si la fiche existe déjà sur le
            // serveur (400 sur l'id), on applique les données en PATCH au lieu d'échouer en boucle.
            if (code == 400) {
                val recordId = syncJob.recordId.ifBlank { idDepuisJson(syncJob.dataJson) ?: "" }
                if (recordId.isNotBlank() && ficheExiste(urlBase, collection, recordId, token)) {
                    val patch = Request.Builder()
                        .url("$url/$recordId")
                        .patch(syncJob.dataJson.toRequestBody("application/json".toMediaType()))
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                    return httpClient.newCall(patch).execute().use { it.isSuccessful }
                }
            }
            return false
            
        } catch (e: Exception) {
            // ❌ Erreur lors de la création
            return false
        }
    }
    
    /**
     * Traite une tâche de mise à jour (UPDATE)
     */
    private suspend fun traiterMiseAJour(syncJob: SyncJob, urlBase: String, token: String): Boolean {
        try {
            val collection = SyncCollections.collectionPourType(syncJob.type)
            
            // 🆕 CORRECTION CRITIQUE : Gestion des recordId manquants pour les anciennes tâches
            val recordId = if (syncJob.recordId.isBlank()) {
                // Si recordId est vide, essayer d'extraire l'ID depuis le JSON
                try {
                    val gson = Gson()
                    val dataMap = gson.fromJson(syncJob.dataJson, Map::class.java)
                    val idFromJson = dataMap["id"] as? String
                    if (!idFromJson.isNullOrBlank()) {
                        // ⚠️ RecordId manquant, utilisation de l'ID du JSON: $idFromJson
                        idFromJson
                    } else {
                        // ❌ RecordId manquant ET ID non trouvé dans le JSON pour UPDATE ${syncJob.id}
                        return false
                    }
                } catch (e: Exception) {
                    // ❌ Erreur lors de l'extraction de l'ID du JSON
                    return false
                }
            } else {
                syncJob.recordId
            }
            
            val url = "$urlBase/api/collections/$collection/records/$recordId"
            // 🔄 URL de mise à jour: $url (type: ${syncJob.type} → collection: $collection, recordId: $recordId)
            
            // 🚨 DIAGNOSTIC CRITIQUE : Afficher les données JSON envoyées
            
            // 🚨 DEBUG CRITIQUE : Log détaillé pour les comptes chèques
            if (syncJob.type == "COMPTE_CHEQUE") {
                // 🚨 COMPTE_CHÈQUE DÉTECTÉ:
                //   Action: ${syncJob.action}
                //   RecordId: ${syncJob.recordId}
            }
            
            // 🚨 CORRECTION CRITIQUE : Pour les allocations, faire comme Room - REMPLACER les valeurs !
            val requestBody = if (syncJob.type == "ALLOCATION_MENSUELLE") {
                // 🎯 PROBLÈME IDENTIFIÉ : Les opérateurs d'incrémentation causent des problèmes !
                // SOLUTION : Faire comme Room - remplacer complètement avec les bonnes valeurs calculées
                val dataMap = gson.fromJson(syncJob.dataJson, Map::class.java)
                val modifiedData = mutableMapOf<String, Any>()
                
                // Traiter chaque champ - REMPLACER complètement comme Room
                dataMap.forEach { (key, value) ->
                    when (key) {
                        "solde", "depense", "alloue", "pretAPlacer" -> {
                            // 🚨 CORRECTION : REMPLACER complètement au lieu d'incrémenter !
                            // Room calcule déjà les bonnes valeurs, on les envoie telles quelles
                            if (value != null) {
                                modifiedData[key.toString()] = value
                            }
                        }
                        else -> {
                            // Garder les autres champs tels quels (ID, mois, etc.)
                            if (value != null) {
                                modifiedData[key.toString()] = value
                            }
                        }
                    }
                }
                
                val jsonData = gson.toJson(modifiedData)
                // 🚨 ALLOCATION MODIFIÉE : Données originales Room = ${syncJob.dataJson}
                // 🚨 ALLOCATION MODIFIÉE : Données avec REMPLACEMENT complet = $jsonData
                
                jsonData.toRequestBody("application/json".toMediaType())
            } else {
                // Pour les autres types, utiliser les données telles quelles
                syncJob.dataJson.toRequestBody("application/json".toMediaType())
            }
            
            val request = Request.Builder()
                .url(url)
                .patch(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val success = response.isSuccessful
            
            if (!success) {
                // ❌ Échec HTTP ${response.code} pour UPDATE:
                //   Réponse du serveur: $responseBody
                //   Headers de réponse: ${response.headers}
            } else {
                // ✅ Mise à jour réussie:
                //   Réponse du serveur: $responseBody
                // 🚨 DEBUG CRITIQUE : Log de succès pour les comptes chèques
                if (syncJob.type == "COMPTE_CHEQUE") {
                    // ✅ COMPTE_CHÈQUE MIS À JOUR AVEC SUCCÈS !
                }
            }
            
            return success
            
        } catch (e: Exception) {
            // ❌ Erreur lors de la mise à jour
            return false
        }
    }
    
    /**
     * Traite une tâche de suppression (DELETE)
     */
    private suspend fun traiterSuppression(syncJob: SyncJob, urlBase: String, token: String): Boolean {
        try {
            val collection = SyncCollections.collectionPourType(syncJob.type)
            
            // 🆕 CORRECTION CRITIQUE : Gestion des recordId manquants pour les anciennes tâches
            val recordId = if (syncJob.recordId.isBlank()) {
                // Si recordId est vide, essayer d'extraire l'ID depuis le JSON
                try {
                    val gson = Gson()
                    val dataMap = gson.fromJson(syncJob.dataJson, Map::class.java)
                    val idFromJson = dataMap["id"] as? String
                    if (!idFromJson.isNullOrBlank()) {
                        // ⚠️ RecordId manquant, utilisation de l'ID du JSON: $idFromJson
                        idFromJson
                    } else {
                        // ❌ RecordId manquant ET ID non trouvé dans le JSON pour DELETE ${syncJob.id}
                        return false
                    }
                } catch (e: Exception) {
                    // ❌ Erreur lors de l'extraction de l'ID du JSON
                    return false
                }
            } else {
                syncJob.recordId
            }
            
            val url = "$urlBase/api/collections/$collection/records/$recordId"
            // 🔄 URL de suppression: $url (type: ${syncJob.type} → collection: $collection, recordId: $recordId)
            
            val request = Request.Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", "Bearer $token")
                .build()
            
            // 404 = déjà supprimée sur le serveur (tâche rejouée) : le résultat voulu est atteint
            return httpClient.newCall(request).execute().use { it.isSuccessful || it.code == 404 }
            
        } catch (e: Exception) {
            // ❌ Erreur lors de la suppression
            return false
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
        // 🆕 VRAIE VÉRIFICATION DE LA CONNECTIVITÉ RÉSEAU
        return try {
            val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            // ❌ Erreur lors de la vérification réseau
            false
        }
    }
}
