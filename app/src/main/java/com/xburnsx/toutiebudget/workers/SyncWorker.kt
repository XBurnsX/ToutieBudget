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
            
            // Vérifier l'authentification
            val token = client.obtenirToken()
            if (token == null) {
                Log.w(logTag, "⚠️ Pas de token d'authentification - synchronisation reportée")
                return@withContext Result.retry()
            }
            
            val urlBase = UrlResolver.obtenirUrlActive()
            
            // Récupérer tous les SyncJob en attente
            val syncJobs = syncJobDao.getPendingSyncJobs()
            if (syncJobs.isEmpty()) {
                Log.d(logTag, "✅ Aucune tâche de synchronisation en attente")
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
            val collection = syncJob.type.lowercase()
            val url = "$urlBase/api/collections/$collection/records"
            
            val requestBody = syncJob.dataJson.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", token)
                .build()
            
            val response = httpClient.newCall(request).execute()
            return response.isSuccessful
            
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
            val collection = syncJob.type.lowercase()
            val url = "$urlBase/api/collections/$collection/records/${syncJob.id}"
            
            val requestBody = syncJob.dataJson.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .patch(requestBody)
                .addHeader("Authorization", token)
                .build()
            
            val response = httpClient.newCall(request).execute()
            return response.isSuccessful
            
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
            val collection = syncJob.type.lowercase()
            val url = "$urlBase/api/collections/$collection/records/${syncJob.id}"
            
            val request = Request.Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", token)
                .build()
            
            val response = httpClient.newCall(request).execute()
            return response.isSuccessful
            
        } catch (e: Exception) {
            Log.e(logTag, "❌ Erreur lors de la suppression", e)
            return false
        }
    }
    
    /**
     * Vérifie si le réseau est disponible
     */
    private fun isNetworkAvailable(): Boolean {
        // TODO: Implémenter la vérification réseau
        // Pour l'instant, on suppose que le réseau est disponible
        return true
    }
}
