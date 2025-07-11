// chemin/simule: app/src/main/java/com/xburnsx/toutiebudget/di/PocketBaseClient.kt
// Dépendances: UrlResolver, OkHttp3, Gson, Coroutines

package com.xburnsx.toutiebudget.di

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client PocketBase personnalisé qui gère automatiquement :
 * - La résolution d'URL (local vs externe)
 * - L'authentification Google OAuth2
 * - La gestion des erreurs réseau
 * - Les timeouts appropriés
 */
object PocketBaseClient {

    // Client HTTP configuré pour PocketBase
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val gson = Gson()
    private var tokenAuthentification: String? = null
    private var utilisateurConnecte: EnregistrementUtilisateur? = null

    /**
     * Initialise le client en résolvant l'URL active
     */
    suspend fun initialiser() {
        try {
            val urlActive = UrlResolver.obtenirUrlActive()
            println("🚀 PocketBaseClient initialisé avec : $urlActive")
        } catch (e: Exception) {
            println("❌ Erreur lors de l'initialisation : ${e.message}")
        }
    }

    /**
     * Connecte l'utilisateur via Google OAuth2
     * @param codeAutorisation Le code d'autorisation obtenu de Google
     */
    suspend fun connecterAvecGoogle(codeAutorisation: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                println("🔐 === AUTHENTIFICATION GOOGLE OAUTH2 ===")
                println("📤 Code d'autorisation Google reçu: ${codeAutorisation.take(20)}...")

                val urlBase = UrlResolver.obtenirUrlActive()
                println("🌐 URL PocketBase: $urlBase")

                // Endpoint OAuth2 PocketBase pour Google
                val urlOAuth = "$urlBase/api/collections/users/auth-with-oauth2"
                println("🔗 URL OAuth2: $urlOAuth")

                // Données pour l'authentification OAuth2
                val donneesOAuth = JsonObject().apply {
                    addProperty("provider", "google")
                    addProperty("code", codeAutorisation)
                    addProperty("codeVerifier", "")
                    addProperty("redirectUrl", "")
                    addProperty("createData", JsonObject().apply {
                        addProperty("emailVisibility", false)
                    }.toString())
                }

                val corpsRequete = donneesOAuth.toString().toRequestBody("application/json".toMediaType())

                val requete = Request.Builder()
                    .url(urlOAuth)
                    .post(corpsRequete)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build()

                println("📡 Tentative authentification OAuth2...")
                println("🔑 Provider: google")

                val reponse = client.newCall(requete).execute()
                val corpsReponse = reponse.body?.string() ?: ""

                println("📨 Status HTTP: ${reponse.code}")
                println("📨 Response: ${corpsReponse.take(500)}...")

                when (reponse.code) {
                    200 -> {
                        println("✅ AUTHENTIFICATION GOOGLE RÉUSSIE !")
                        try {
                            val reponseAuth = gson.fromJson(corpsReponse, ReponseAuthentification::class.java)

                            // Sauvegarder les informations de connexion
                            tokenAuthentification = reponseAuth.token
                            utilisateurConnecte = reponseAuth.record

                            println("✅ Utilisateur connecté: ${reponseAuth.record.email}")
                            println("🎫 Token: ${reponseAuth.token.take(20)}...")
                            println("🔐 === AUTHENTIFICATION GOOGLE RÉUSSIE ===")

                            Result.success(Unit)

                        } catch (e: Exception) {
                            println("❌ Erreur parsing réponse OAuth2: ${e.message}")
                            println("📄 Réponse brute: $corpsReponse")
                            Result.failure(Exception("Erreur parsing OAuth2: ${e.message}"))
                        }
                    }
                    400 -> {
                        println("❌ 400 - Erreur dans la requête OAuth2")
                        println("📄 Détails: $corpsReponse")
                        println("💡 Solutions:")
                        println("   1. Vérifiez que Google OAuth2 est configuré dans PocketBase")
                        println("   2. Settings → Auth providers → Google")
                        println("   3. Client ID et Client Secret corrects")
                        println("   4. Redirect URLs autorisées")
                        Result.failure(Exception("Configuration OAuth2 incorrecte"))
                    }
                    401 -> {
                        println("❌ 401 - Code d'autorisation invalide ou expiré")
                        Result.failure(Exception("Code d'autorisation Google invalide"))
                    }
                    404 -> {
                        println("❌ 404 - Endpoint OAuth2 non trouvé")
                        println("💡 Vérifiez que PocketBase tourne sur: $urlBase")
                        Result.failure(Exception("Service PocketBase non accessible"))
                    }
                    else -> {
                        println("❌ Erreur HTTP ${reponse.code}")
                        println("📄 Détails: $corpsReponse")
                        Result.failure(Exception("Erreur HTTP ${reponse.code}: $corpsReponse"))
                    }
                }

            } catch (e: IOException) {
                println("🌐 Erreur réseau: ${e.message}")
                UrlResolver.invaliderCache()
                Result.failure(Exception("Erreur réseau: ${e.message}"))
            } catch (e: Exception) {
                println("💥 Erreur générale: ${e.message}")
                Result.failure(Exception("Erreur inattendue: ${e.message}"))
            }
        }

    /**
     * Déconnecte l'utilisateur actuel
     */
    fun deconnecter() {
        tokenAuthentification = null
        utilisateurConnecte = null
        println("👋 Utilisateur déconnecté")
    }

    /**
     * Vérifie si un utilisateur est actuellement connecté
     */
    fun estConnecte(): Boolean {
        return tokenAuthentification != null
    }

    /**
     * Obtient les informations de l'utilisateur connecté
     */
    fun obtenirUtilisateurConnecte(): EnregistrementUtilisateur? {
        return utilisateurConnecte
    }

    /**
     * Obtient le token d'authentification actuel
     */
    fun obtenirToken(): String? {
        return tokenAuthentification
    }

    // Classes de données pour les réponses PocketBase
    data class ReponseAuthentification(
        val token: String,
        val record: EnregistrementUtilisateur
    )

    data class EnregistrementUtilisateur(
        val id: String,
        val email: String,
        val name: String?,
        val avatar: String?
    )
}