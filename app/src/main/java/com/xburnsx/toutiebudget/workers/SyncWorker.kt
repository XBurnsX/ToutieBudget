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
            Log.i(logTag, "🚀 DÉBUT DE LA SYNCHRONISATION")
            
            // Vérifier la connexion réseau
            if (!isNetworkAvailable()) {
                Log.w(logTag, "⚠️ Pas de connexion réseau - synchronisation reportée")
                return@withContext Result.retry()
            }
            
            // 🚨 DEBUG : Afficher les informations de base
            Log.i(logTag, "🔧 Vérification de la connectivité et de l'authentification...")
            
            // 🆕 CHARGER L'AUTHENTIFICATION SAUVEGARDÉE AVANT D'OBTENIR LE TOKEN
            // C'est crucial pour que le worker puisse accéder au token sauvegardé
            client.chargerAuthentificationSauvegardee(applicationContext)
            
            // Vérifier l'authentification
            val token = client.obtenirToken()
            if (token == null) {
                Log.w(logTag, "⚠️ Pas de token d'authentification - synchronisation reportée")
                return@withContext Result.retry()
            }
            
            Log.i(logTag, "✅ Token d'authentification récupéré avec succès")
            
            val urlBase = UrlResolver.obtenirUrlActive()
            
            // Récupérer tous les SyncJob en attente ET les échecs pour retry
            val syncJobs = syncJobDao.getPendingAndFailedSyncJobs()
            if (syncJobs.isEmpty()) {
                Log.i(logTag, "✅ Aucune tâche de synchronisation en attente ou à retenter")
                return@withContext Result.success()
            }
            
            Log.i(logTag, "📋 ${syncJobs.size} tâches de synchronisation à traiter")
            
            var successCount = 0
            var failureCount = 0
            
            // Traiter chaque tâche de synchronisation
            for (syncJob in syncJobs) {
                try {
                    Log.i(logTag, "🔄 Traitement de la tâche ${syncJob.id}: ${syncJob.type} - ${syncJob.action}")
                    
                    val success = when (syncJob.action) {
                        "CREATE" -> traiterCreation(syncJob, urlBase, token)
                        "UPDATE" -> traiterMiseAJour(syncJob, urlBase, token)
                        "DELETE" -> traiterSuppression(syncJob, urlBase, token)
                        else -> {
                            Log.w(logTag, "⚠️ Action non reconnue: ${syncJob.action}")
                            false
                        }
                    }
                    
                    if (success) {
                        // Marquer la tâche comme terminée
                        syncJobDao.updateSyncJobStatus(syncJob.id, "COMPLETED")
                        successCount++
                        Log.i(logTag, "✅ Tâche ${syncJob.id} synchronisée avec succès")
                    } else {
                        // Marquer la tâche comme échouée
                        syncJobDao.updateSyncJobStatus(syncJob.id, "FAILED")
                        failureCount++
                        Log.e(logTag, "❌ Échec de la synchronisation de la tâche ${syncJob.id}")
                    }
                    
                } catch (e: Exception) {
                    Log.e(logTag, "❌ Erreur lors du traitement de la tâche ${syncJob.id}", e)
                    syncJobDao.updateSyncJobStatus(syncJob.id, "FAILED")
                    failureCount++
                }
            }
            
            Log.i(logTag, "🎉 SYNCHRONISATION TERMINÉE: $successCount succès, $failureCount échecs")
            
            // 🚫 SUPPRESSION DU NETTOYAGE AUTOMATIQUE : L'utilisateur gère manuellement les tâches
            // Les tâches restent visibles dans l'interface avec les onglets par statut
            Log.i(logTag, "📋 Tâches conservées pour gestion manuelle par l'utilisateur")
            
            // Si toutes les tâches ont réussi, on retourne success
            // Sinon, on retourne retry pour réessayer les tâches échouées
            return@withContext if (failureCount == 0) Result.success() else Result.retry()
            
        } catch (e: Exception) {
            Log.e(logTag, "❌ Erreur fatale lors de la synchronisation", e)
            return@withContext Result.failure()
        }
    }
    
    /**
     * Traite une tâche de création (CREATE)
     */
    private suspend fun traiterCreation(syncJob: SyncJob, urlBase: String, token: String): Boolean {
        try {
            // 🚨 CORRECTION CRITIQUE : Utiliser directement les noms des tables Room = collections Pocketbase
            val collection = when (syncJob.type.uppercase()) {
                "TRANSACTION" -> "transactions"
                "COMPTE" -> "comptes_cheques" // Collection par défaut
                "COMPTE_CHEQUE" -> "comptes_cheques" // Même nom que la table Room !
                "COMPTE_CREDIT" -> "comptes_credits" // Même nom que la table Room !
                "COMPTE_DETTE" -> "comptes_dettes" // Même nom que la table Room !
                "COMPTE_INVESTISSEMENT" -> "comptes_investissement" // Même nom que la table Room !
                "ALLOCATION_MENSUELLE" -> "allocations_mensuelles"
                "PRET_PERSONNEL" -> "pret_personnel"
                "ENVELOPPE" -> "enveloppes"
                "CATEGORIE" -> "categories"
                "TIERS" -> "tiers"
                else -> syncJob.type.lowercase()
            }
            
            val url = "$urlBase/api/collections/$collection/records"
            Log.i(logTag, "🔄 URL de création: $url (type: ${syncJob.type} → collection: $collection)")
            
            // 🚨 DIAGNOSTIC CRITIQUE : Afficher les données JSON envoyées
            Log.i(logTag, "📤 DONNÉES JSON ENVOYÉES POUR CRÉATION:")
            Log.i(logTag, "  ${syncJob.dataJson}")
            
            val requestBody = syncJob.dataJson.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val success = response.isSuccessful
            
            if (!success) {
                Log.e(logTag, "❌ Échec HTTP ${response.code} pour CREATE:")
                Log.e(logTag, "  Réponse du serveur: $responseBody")
                Log.e(logTag, "  Headers de réponse: ${response.headers}")
            } else {
                Log.i(logTag, "✅ Création réussie:")
                Log.i(logTag, "  Réponse du serveur: $responseBody")
            }
            
            return success
            
        } catch (e: Exception) {
            Log.e(logTag, "❌ Erreur lors de la création", e)
            return false
        }
    }
    
    /**
     * Traite une tâche de mise à jour (UPDATE)
     */
    private suspend fun traiterMiseAJour(syncJob: SyncJob, urlBase: String, token: String): Boolean {
        try {
            // 🚨 CORRECTION CRITIQUE : Utiliser directement les noms des tables Room = collections Pocketbase
            val collection = when (syncJob.type.uppercase()) {
                "TRANSACTION" -> "transactions"
                "COMPTE" -> "comptes_cheques" // Collection par défaut
                "COMPTE_CHEQUE" -> "comptes_cheques" // Même nom que la table Room !
                "COMPTE_CREDIT" -> "comptes_credits" // Même nom que la table Room !
                "COMPTE_DETTE" -> "comptes_dettes" // Même nom que la table Room !
                "COMPTE_INVESTISSEMENT" -> "comptes_investissement" // Même nom que la table Room !
                "ALLOCATION_MENSUELLE" -> "allocations_mensuelles"
                "PRET_PERSONNEL" -> "pret_personnel"
                "ENVELOPPE" -> "enveloppes"
                "CATEGORIE" -> "categories"
                "TIERS" -> "tiers"
                else -> syncJob.type.lowercase()
            }
            
            // 🆕 CORRECTION CRITIQUE : Gestion des recordId manquants pour les anciennes tâches
            val recordId = if (syncJob.recordId.isBlank()) {
                // Si recordId est vide, essayer d'extraire l'ID depuis le JSON
                try {
                    val gson = Gson()
                    val dataMap = gson.fromJson(syncJob.dataJson, Map::class.java)
                    val idFromJson = dataMap["id"] as? String
                    if (!idFromJson.isNullOrBlank()) {
                        Log.w(logTag, "⚠️ RecordId manquant, utilisation de l'ID du JSON: $idFromJson")
                        idFromJson
                    } else {
                        Log.e(logTag, "❌ RecordId manquant ET ID non trouvé dans le JSON pour UPDATE ${syncJob.id}")
                        return false
                    }
                } catch (e: Exception) {
                    Log.e(logTag, "❌ Erreur lors de l'extraction de l'ID du JSON", e)
                    return false
                }
            } else {
                syncJob.recordId
            }
            
            val url = "$urlBase/api/collections/$collection/records/$recordId"
            Log.d(logTag, "🔄 URL de mise à jour: $url (type: ${syncJob.type} → collection: $collection, recordId: $recordId)")
            
            // 🚨 DIAGNOSTIC CRITIQUE : Afficher les données JSON envoyées
            Log.i(logTag, "📤 DONNÉES JSON ENVOYÉES POUR MISE À JOUR:")
            Log.i(logTag, "  ${syncJob.dataJson}")
            
            // 🚨 DEBUG CRITIQUE : Log détaillé pour les comptes chèques
            if (syncJob.type == "COMPTE_CHEQUE") {
                Log.d(logTag, "🚨 COMPTE_CHÈQUE DÉTECTÉ:")
                Log.d(logTag, "  Action: ${syncJob.action}")
                Log.d(logTag, "  RecordId: ${syncJob.recordId}")
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
                Log.d(logTag, "🚨 ALLOCATION MODIFIÉE : Données originales Room = ${syncJob.dataJson}")
                Log.d(logTag, "🚨 ALLOCATION MODIFIÉE : Données avec REMPLACEMENT complet = $jsonData")
                
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
                Log.e(logTag, "❌ Échec HTTP ${response.code} pour UPDATE:")
                Log.e(logTag, "  Réponse du serveur: $responseBody")
                Log.e(logTag, "  Headers de réponse: ${response.headers}")
            } else {
                Log.i(logTag, "✅ Mise à jour réussie:")
                Log.i(logTag, "  Réponse du serveur: $responseBody")
                // 🚨 DEBUG CRITIQUE : Log de succès pour les comptes chèques
                if (syncJob.type == "COMPTE_CHEQUE") {
                    Log.i(logTag, "✅ COMPTE_CHÈQUE MIS À JOUR AVEC SUCCÈS !")
                }
            }
            
            return success
            
        } catch (e: Exception) {
            Log.e(logTag, "❌ Erreur lors de la mise à jour", e)
            return false
        }
    }
    
    /**
     * Traite une tâche de suppression (DELETE)
     */
    private suspend fun traiterSuppression(syncJob: SyncJob, urlBase: String, token: String): Boolean {
        try {
            // 🚨 CORRECTION CRITIQUE : Utiliser directement les noms des tables Room = collections Pocketbase
            val collection = when (syncJob.type.uppercase()) {
                "TRANSACTION" -> "transactions"
                "COMPTE" -> "comptes_cheques" // Collection par défaut
                "COMPTE_CHEQUE" -> "comptes_cheques" // Même nom que la table Room !
                "COMPTE_CREDIT" -> "comptes_credits" // Même nom que la table Room !
                "COMPTE_DETTE" -> "comptes_dettes" // Même nom que la table Room !
                "COMPTE_INVESTISSEMENT" -> "comptes_investissement" // Même nom que la table Room !
                "ALLOCATION_MENSUELLE" -> "allocations_mensuelles"
                "PRET_PERSONNEL" -> "pret_personnel"
                "ENVELOPPE" -> "enveloppes"
                "CATEGORIE" -> "categories"
                "TIERS" -> "tiers"
                else -> syncJob.type.lowercase()
            }
            
            // 🆕 CORRECTION CRITIQUE : Gestion des recordId manquants pour les anciennes tâches
            val recordId = if (syncJob.recordId.isBlank()) {
                // Si recordId est vide, essayer d'extraire l'ID depuis le JSON
                try {
                    val gson = Gson()
                    val dataMap = gson.fromJson(syncJob.dataJson, Map::class.java)
                    val idFromJson = dataMap["id"] as? String
                    if (!idFromJson.isNullOrBlank()) {
                        Log.w(logTag, "⚠️ RecordId manquant, utilisation de l'ID du JSON: $idFromJson")
                        idFromJson
                    } else {
                        Log.e(logTag, "❌ RecordId manquant ET ID non trouvé dans le JSON pour DELETE ${syncJob.id}")
                        return false
                    }
                } catch (e: Exception) {
                    Log.e(logTag, "❌ Erreur lors de l'extraction de l'ID du JSON", e)
                    return false
                }
            } else {
                syncJob.recordId
            }
            
            val url = "$urlBase/api/collections/$collection/records/$recordId"
            Log.d(logTag, "🔄 URL de suppression: $url (type: ${syncJob.type} → collection: $collection, recordId: $recordId)")
            
            val request = Request.Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", "Bearer $token")
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val success = response.isSuccessful
            
            if (!success) {
                Log.e(logTag, "❌ Échec HTTP ${response.code} pour DELETE:")
                Log.e(logTag, "  Réponse du serveur: $responseBody")
                Log.e(logTag, "  Headers de réponse: ${response.headers}")
            } else {
                Log.i(logTag, "✅ Suppression réussie:")
                Log.i(logTag, "  Réponse du serveur: $responseBody")
            }
            
            return success
            
        } catch (e: Exception) {
            Log.e(logTag, "❌ Erreur lors de la suppression", e)
            return false
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
            android.util.Log.e(logTag, "❌ Erreur lors de la vérification réseau", e)
            false
        }
    }
}
