package com.xburnsx.toutiebudget.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * Service de diagnostic pour tester la connectivité réseau
 * et identifier les problèmes de connexion PocketBase
 */
object DiagnosticConnexion {

    private val clientTest = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /**
     * Effectue un diagnostic complet de la connectivité
     */
    suspend fun diagnostiquerConnexion(): String = withContext(Dispatchers.IO) {
        val rapport = StringBuilder()
        rapport.appendLine("=== DIAGNOSTIC DE CONNEXION ===")
        rapport.appendLine()

        // Informations sur l'environnement
        val typeEnv = DetecteurEmulateur.obtenirTypeEnvironnement()
        rapport.appendLine("Environnement détecté: $typeEnv")
        rapport.appendLine("Informations système:")
        rapport.appendLine(DetecteurEmulateur.obtenirInfoEnvironnement())
        rapport.appendLine()

        // Tester l'URL PocketBase HTTPS
        val urlPocketbase = "https://pocketbase.toutieserver.com"
        
        val urlsATester = listOf(
            urlPocketbase to "PocketBase HTTPS"
        )

        rapport.appendLine("=== TESTS DE CONNEXION POCKETBASE ===")
        for ((url, description) in urlsATester) {
            val resultat = testerUrl(url, description)
            rapport.appendLine(resultat)
        }
        rapport.appendLine()

        // Test de connectivité internet générale
        rapport.appendLine("=== TEST DE CONNECTIVITÉ INTERNET ===")
        val sitesTest = listOf(
            "https://www.google.com" to "Google",
            "https://www.cloudflare.com" to "Cloudflare",
            "https://toutieserver.duckdns.org" to "DuckDNS"
        )

        for ((url, nom) in sitesTest) {
            val resultat = testerUrl(url, nom)
            rapport.appendLine(resultat)
        }

        rapport.toString()
    }

    private fun testerUrl(url: String, description: String): String {
        return try {
            val requete = Request.Builder()
                .url(url)
                .get()
                .build()

            val reponse = clientTest.newCall(requete).execute()
            
            if (reponse.isSuccessful) {
                "✅ $description ($url) - OK (${reponse.code})"
            } else {
                "❌ $description ($url) - ÉCHEC (${reponse.code})"
            }
        } catch (e: Exception) {
            val messageErreur = when (e) {
                is java.net.SocketTimeoutException -> "Timeout"
                is java.net.UnknownHostException -> "Hôte introuvable"
                is java.io.IOException -> "Erreur réseau"
                else -> "Erreur: ${e.message}"
            }
            "❌ $description ($url) - $messageErreur"
        }
    }

}