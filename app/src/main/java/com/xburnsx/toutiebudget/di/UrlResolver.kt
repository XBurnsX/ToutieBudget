package com.xburnsx.toutiebudget.di

import com.xburnsx.toutiebudget.utils.DetecteurEmulateur
import com.xburnsx.toutiebudget.utils.TypeEnvironnement
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Service intelligent qui détermine automatiquement quelle URL PocketBase utiliser.
 * Il teste les URL locales en priorité et se rabat sur l'URL publique si nécessaire.
 */
object UrlResolver {

    private var urlActive: String? = null
    private var derniereVerification: Long = 0L
    private const val DUREE_CACHE_MS = 30_000L // Cache de 30 secondes

    private val clientVerification = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS) // Timeout court pour des tests rapides
        .readTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun obtenirUrlActive(): String = withContext(Dispatchers.IO) {
        val maintenant = System.currentTimeMillis()
        if (urlActive != null && (maintenant - derniereVerification) < DUREE_CACHE_MS) {
            return@withContext urlActive!!
        }

        val typeEnvironnement = DetecteurEmulateur.obtenirTypeEnvironnement()

        // URLs locales en priorité (sans fallback public)
        val urlsLocales = when (typeEnvironnement) {
            TypeEnvironnement.EMULATEUR -> listOf(
                "http://10.0.2.2:8090" to "Émulateur vers Host"
            )
            TypeEnvironnement.DISPOSITIF_PHYSIQUE -> listOf(
                "http://192.168.1.77:8090" to "IP Locale"
            )
        }


        // Tester d'abord les URLs locales
        for ((url, description) in urlsLocales) {
            if (testerConnexion(url, description)) {
                urlActive = url
                derniereVerification = maintenant
                return@withContext url
            }
        }

        // Seulement si aucune URL locale ne fonctionne, tester l'URL publique
        val urlPublique = "http://toutiebudget.duckdns.org:8090"
        if (testerConnexion(urlPublique, "Publique (Fallback)")) {
            urlActive = urlPublique
            derniereVerification = maintenant
            return@withContext urlPublique
        }

        // Si absolument rien ne fonctionne
        urlActive = urlPublique
        derniereVerification = maintenant
        return@withContext urlPublique
    }

    private suspend fun testerConnexion(url: String, description: String): Boolean {
        return try {
            print("  -> Test de '$description' sur $url ... ")
            val requete = Request.Builder().url("${url.trimEnd('/')}/api/health").get().build()
            clientVerification.newCall(requete).execute().use { reponse ->
                if (reponse.isSuccessful) {
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            val messageErreur = when (e) {
                is SocketTimeoutException -> "Timeout"
                is UnknownHostException -> "Hôte introuvable"
                is IOException -> "Erreur réseau"
                else -> "Erreur inconnue"
            }
            false
        }
    }

    fun invaliderCache() {
        derniereVerification = 0L
        urlActive = null
    }

    fun obtenirUrlActuelle(): String? = urlActive
}