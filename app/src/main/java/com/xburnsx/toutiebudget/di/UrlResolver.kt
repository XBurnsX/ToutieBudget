package com.xburnsx.toutiebudget.di

import kotlinx.coroutines.*

/**
 * Service qui se connecte uniquement à l'URL PocketBase HTTPS configurée.
 */
object UrlResolver {

    // === CONFIGURATION UNIQUE URL POCKETBASE ===
    private const val URL_POCKETBASE = "https://pocketbase.toutieserver.com"
    
    private var urlActive: String? = null
    private var derniereVerification: Long = 0L
    private const val DUREE_CACHE_MS = 300_000L // Cache de 5 minutes (au lieu de 30s)


    suspend fun obtenirUrlActive(): String = withContext(Dispatchers.IO) {
        val maintenant = System.currentTimeMillis()
        if (urlActive != null && (maintenant - derniereVerification) < DUREE_CACHE_MS) {
            return@withContext urlActive!!
        }

        // Utiliser uniquement l'URL HTTPS configurée
        urlActive = URL_POCKETBASE
        derniereVerification = maintenant
        return@withContext URL_POCKETBASE
    }


    fun invaliderCache() {
        derniereVerification = 0L
        urlActive = null
    }

    fun obtenirUrlActuelle(): String? = urlActive
}