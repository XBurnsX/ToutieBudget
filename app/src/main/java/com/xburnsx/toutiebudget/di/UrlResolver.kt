// chemin/simule: /di/UrlResolver.kt
// Dépendances: DetecteurEmulateur, TypeEnvironnement, OkHttp3, Coroutines

package com.xburnsx.toutiebudget.di

import com.xburnsx.toutiebudget.utils.DetecteurEmulateur
import com.xburnsx.toutiebudget.utils.TypeEnvironnement
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * Service intelligent qui détermine automatiquement quelle URL PocketBase utiliser.
 * Il teste les URL locales en priorité et se rabat sur l'URL publique si nécessaire.
 * Version améliorée avec tests parallèles et fallback intelligent.
 */
object UrlResolver {

    private var urlActive: String? = null
    private var derniereVerification: Long = 0L
    private const val DUREE_CACHE_MS = 300_000L // Cache de 5 minutes (au lieu de 30s)

    private val clientVerification = OkHttpClient.Builder()
        .connectTimeout(500, TimeUnit.MILLISECONDS) // Réduit à 500ms pour des tests ultra-rapides
        .readTimeout(1, TimeUnit.SECONDS)
        .writeTimeout(500, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .connectionPool(ConnectionPool(3, 1, TimeUnit.MINUTES)) // Pool dédié pour les tests
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Accept-Encoding", "gzip, deflate")
                .addHeader("Connection", "keep-alive")
                .build()
            chain.proceed(request)
        }
        .build()

    suspend fun obtenirUrlActive(): String = withContext(Dispatchers.IO) {
        val maintenant = System.currentTimeMillis()
        if (urlActive != null && (maintenant - derniereVerification) < DUREE_CACHE_MS) {
            return@withContext urlActive!!
        }

        val typeEnvironnement = DetecteurEmulateur.obtenirTypeEnvironnement()
        
        // Définir toutes les URLs à tester dans l'ordre de priorité
        val urlsATester = when (typeEnvironnement) {
            TypeEnvironnement.EMULATEUR -> listOf(
                "http://10.0.2.2:8090" to "Émulateur vers Host",
                "http://192.168.1.77:8090" to "IP Locale (émulateur)",
                "http://toutiebudget.duckdns.org:8090" to "Publique (Fallback)"
            )
            TypeEnvironnement.DISPOSITIF_PHYSIQUE -> listOf(
                "http://192.168.1.77:8090" to "IP Locale",
                "http://10.0.2.2:8090" to "Host (dispositif physique)",
                "http://toutiebudget.duckdns.org:8090" to "Publique (Fallback)"
            )
        }

        // Tester toutes les URLs en parallèle
        val resultats = urlsATester.map { (url, description) ->
            async {
                val estConnecte = testerConnexion(url)
                Triple(url, description, estConnecte)
            }
        }.awaitAll()

        // Trouver la première URL qui fonctionne
        val premiereUrlValide = resultats.find { it.third }?.first
        
        if (premiereUrlValide != null) {
            urlActive = premiereUrlValide
            derniereVerification = maintenant
            return@withContext premiereUrlValide
        }

        // Si aucune URL ne fonctionne, utiliser la première comme fallback
        val urlFallback = urlsATester.first().first
        urlActive = urlFallback
        derniereVerification = maintenant
        return@withContext urlFallback
    }

    private fun testerConnexion(url: String): Boolean {
        return try {
            val requete = Request.Builder().url("${url.trimEnd('/')}/api/health").get().build()
            val reponse = clientVerification.newCall(requete).execute()
            val estValide = reponse.isSuccessful
            return estValide
        } catch (_: Exception) {
            return false
        }
    }

    fun invaliderCache() {
        derniereVerification = 0L
        urlActive = null
    }

    fun obtenirUrlActuelle(): String? = urlActive
}