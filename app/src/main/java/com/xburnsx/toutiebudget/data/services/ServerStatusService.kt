package com.xburnsx.toutiebudget.data.services

import com.xburnsx.toutiebudget.di.UrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Service pour vérifier l'état du serveur PocketBase
 */
class ServerStatusService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS) // Réduit de 5s à 2s
        .readTimeout(3, TimeUnit.SECONDS) // Réduit de 5s à 3s
        .writeTimeout(2, TimeUnit.SECONDS) // Réduit de 5s à 2s
        .retryOnConnectionFailure(false)
        .connectionPool(okhttp3.ConnectionPool(2, 1, TimeUnit.MINUTES)) // Pool dédié
        .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Accept-Encoding", "gzip, deflate")
                .addHeader("Connection", "keep-alive")
                .build()
            chain.proceed(request)
        }
        .build()

    /**
     * Vérifie si le serveur PocketBase est disponible
     * @return true si le serveur répond, false sinon
     */
    suspend fun verifierServeurDisponible(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val urlBase = UrlResolver.obtenirUrlActive()
            val requete = Request.Builder()
                .url("${urlBase.trimEnd('/')}/api/health")
                .get()
                .build()

            val reponse = client.newCall(requete).execute()
            reponse.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Vérifie la connectivité avec plus de détails
     * @return Pair<Boolean, String> - (isConnected, errorMessage)
     */
    suspend fun verifierConnectiviteDetaillée(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val urlBase = UrlResolver.obtenirUrlActive()
            val requete = Request.Builder()
                .url("${urlBase.trimEnd('/')}/api/health")
                .get()
                .build()

            val reponse = client.newCall(requete).execute()

            if (reponse.isSuccessful) {
                Pair(true, "")
            } else {
                Pair(false, "Le serveur a répondu avec l'erreur ${reponse.code}")
            }
        } catch (e: Exception) {
            val messageErreur = when {
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "Le serveur ne répond pas dans les délais"
                e.message?.contains("unknown host", ignoreCase = true) == true ->
                    "Impossible de joindre le serveur"
                e.message?.contains("connection refused", ignoreCase = true) == true ->
                    "Connexion refusée - le serveur PocketBase n'est pas démarré"
                else -> "Erreur de connexion: ${e.message}"
            }
            Pair(false, messageErreur)
        }
    }
}
