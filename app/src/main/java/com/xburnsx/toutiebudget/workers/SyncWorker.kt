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
            Log.d(logTag, "🚀 DÉBUT DE LA SYNCHRONISATION")
            
            // Vérifier la connexion réseau
            if (!isNetworkAvailable()) {
                Log.w(logTag, "⚠️ Pas de connexion réseau - synchronisation reportée")
                return@withContext Result.retry()
            }
            
            // 🆕 CHARGER L'AUTHENTIFICATION SAUVEGARDÉE AVANT D'OBTENIR LE TOKEN
            // C'est crucial pour que le worker puisse accéder au token sauvegardé
            client.chargerAuthentificationSauvegardee(applicationContext)
            
            // Vérifier l'authentification
            val token = client.obtenirToken()
            if (token == null) {
                Log.w(logTag, "⚠️ Pas de token d'authentification - synchronisation reportée")
                return@withContext Result.retry()
            }
            
            Log.d(logTag, "✅ Token d'authentification récupéré avec succès")
            
            val urlBase = UrlResolver.obtenirUrlActive()
            
            // Récupérer tous les SyncJob en attente ET les échecs pour retry
            val syncJobs = syncJobDao.getPendingAndFailedSyncJobs()
            if (syncJobs.isEmpty()) {
                Log.d(logTag, "✅ Aucune tâche de synchronisation en attente ou à retenter")
                return@withContext Result.success()
            }
            
            Log.d(logTag, "📋 ${syncJobs.size} tâches de synchronisation à traiter")
            
            var successCount = 0
            var failureCount = 0
            
            // Traiter chaque tâche de synchronisation
            for (syncJob in syncJobs) {
                try {
                    Log.d(logTag, "🔄 Traitement de la tâche ${syncJob.id}: ${syncJob.type} - ${syncJob.action}")
                    
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
                        Log.d(logTag, "✅ Tâche ${syncJob.id} synchronisée avec succès")
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
            
            Log.d(logTag, "🎉 SYNCHRONISATION TERMINÉE: $successCount succès, $failureCount échecs")
            
            // 🧹 NETTOYAGE AUTOMATIQUE : Supprimer les SyncJobs terminés avec succès
            try {
                syncJobDao.deleteCompletedSyncJobs()
                Log.d(logTag, "🧹 Nettoyage automatique des SyncJobs terminés effectué")
            } catch (e: Exception) {
                Log.w(logTag, "⚠️ Erreur lors du nettoyage automatique des SyncJobs", e)
            }
            
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
            // 🆕 CORRECTION COMPLÈTE : Mapper TOUS les types vers les VRAIS noms de collections Pocketbase
            val collection = when (syncJob.type.uppercase()) {
                "TRANSACTION" -> "transactions" // ✅ VRAI nom
                "COMPTE" -> "comptes_cheques" // ✅ VRAI nom (collection par défaut)
                "COMPTE_CHEQUE" -> "comptes_cheques" // ✅ VRAI nom
                "COMPTE_CREDIT" -> "comptes_credits" // ✅ VRAI nom
                "COMPTE_DETTE" -> "comptes_dettes" // ✅ VRAI nom
                "COMPTE_INVESTISSEMENT" -> "comptes_investissement" // ✅ VRAI nom
                "ALLOCATION_MENSUELLE" -> "allocations_mensuelles" // ✅ VRAI nom (pluriel !)
                "PRET_PERSONNEL" -> "pret_personnel" // ✅ VRAI nom
                "ENVELOPPE" -> "enveloppes" // ✅ VRAI nom
                "CATEGORIE" -> "categories" // ✅ VRAI nom
                "TIERS" -> "tiers" // ✅ VRAI nom
                else -> syncJob.type.lowercase()
            }
            
            val url = "$urlBase/api/collections/$collection/records"
            Log.d(logTag, "🔄 URL de création: $url (type: ${syncJob.type} → collection: $collection)")
            
            val requestBody = syncJob.dataJson.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()
            
            val response = httpClient.newCall(request).execute()
            val success = response.isSuccessful
            
            if (!success) {
                Log.e(logTag, "❌ Échec HTTP ${response.code} pour CREATE: ${response.body?.string()}")
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
            // 🆕 CORRECTION COMPLÈTE : Mapper TOUS les types vers les VRAIS noms de collections Pocketbase
            val collection = when (syncJob.type.uppercase()) {
                "TRANSACTION" -> "transactions" // ✅ VRAI nom
                "COMPTE" -> "comptes_cheques" // ✅ VRAI nom (collection par défaut)
                "COMPTE_CHEQUE" -> "comptes_cheques" // ✅ VRAI nom
                "COMPTE_CREDIT" -> "comptes_credits" // ✅ VRAI nom
                "COMPTE_DETTE" -> "comptes_dettes" // ✅ VRAI nom
                "COMPTE_INVESTISSEMENT" -> "comptes_investissement" // ✅ VRAI nom
                "ALLOCATION_MENSUELLE" -> "allocations_mensuelles" // ✅ VRAI nom (pluriel !)
                "PRET_PERSONNEL" -> "pret_personnel" // ✅ VRAI nom
                "ENVELOPPE" -> "enveloppes" // ✅ VRAI nom
                "CATEGORIE" -> "categories" // ✅ VRAI nom
                "TIERS" -> "tiers" // ✅ VRAI nom
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
            
            // 🚨 DEBUG CRITIQUE : Log détaillé pour les comptes chèques
            if (syncJob.type == "COMPTE_CHEQUE") {
                Log.d(logTag, "🚨 COMPTE_CHÈQUE DÉTECTÉ:")
                Log.d(logTag, "  Action: ${syncJob.action}")
                Log.d(logTag, "  RecordId: ${syncJob.recordId}")
                Log.d(logTag, "  DataJson: ${syncJob.dataJson}")
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
            val success = response.isSuccessful
            
            if (!success) {
                Log.e(logTag, "❌ Échec HTTP ${response.code} pour UPDATE: ${response.body?.string()}")
            } else {
                // 🚨 DEBUG CRITIQUE : Log de succès pour les comptes chèques
                if (syncJob.type == "COMPTE_CHEQUE") {
                    Log.d(logTag, "✅ COMPTE_CHÈQUE MIS À JOUR AVEC SUCCÈS !")
                    Log.d(logTag, "  Réponse: ${response.body?.string()}")
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
            // 🆕 CORRECTION COMPLÈTE : Mapper TOUS les types vers les VRAIS noms de collections Pocketbase
            val collection = when (syncJob.type.uppercase()) {
                "TRANSACTION" -> "transactions" // ✅ VRAI nom
                "COMPTE" -> "comptes_cheques" // ✅ VRAI nom (collection par défaut)
                "COMPTE_CHEQUE" -> "comptes_cheques" // ✅ VRAI nom
                "COMPTE_CREDIT" -> "comptes_credits" // ✅ VRAI nom
                "COMPTE_DETTE" -> "comptes_dettes" // ✅ VRAI nom
                "COMPTE_INVESTISSEMENT" -> "comptes_investissement" // ✅ VRAI nom
                "ALLOCATION_MENSUELLE" -> "allocations_mensuelles" // ✅ VRAI nom (pluriel !)
                "PRET_PERSONNEL" -> "pret_personnel" // ✅ VRAI nom
                "ENVELOPPE" -> "enveloppes" // ✅ VRAI nom
                "CATEGORIE" -> "categories" // ✅ VRAI nom
                "TIERS" -> "tiers" // ✅ VRAI nom
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
            val success = response.isSuccessful
            
            if (!success) {
                Log.e(logTag, "❌ Échec HTTP ${response.code} pour DELETE: ${response.body?.string()}")
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
