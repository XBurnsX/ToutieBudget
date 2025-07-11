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
 * - Les logs détaillés pour le debug
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
            println("🔧 === INITIALISATION POCKETBASE CLIENT ===")
            val urlActive = UrlResolver.obtenirUrlActive()
            println("✅ PocketBaseClient initialisé avec : $urlActive")
            
            // Test de connectivité
            testerConnectivite(urlActive)
            
        } catch (e: Exception) {
            println("❌ Erreur lors de l'initialisation : ${e.message}")
            throw e
        }
    }

    /**
     * Teste la connectivité vers PocketBase
     */
    private suspend fun testerConnectivite(urlBase: String) {
        try {
            println("🔍 Test de connectivité vers : $urlBase")
            val requete = Request.Builder()
                .url("${urlBase.trimEnd('/')}/api/health")
                .get()
                .build()
            
            val reponse = withContext(Dispatchers.IO) {
                client.newCall(requete).execute()
            }
            
            if (reponse.isSuccessful) {
                println("✅ Connectivité OK - Serveur PocketBase accessible")
            } else {
                println("⚠️ Connectivité partielle - Code ${reponse.code}")
            }
        } catch (e: Exception) {
            val messageErreur = e.message ?: "Erreur inconnue"
            println("❌ Problème de connectivité : $messageErreur")
            println("🔍 Type d'erreur : ${e.javaClass.simpleName}")
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

                val urlBase = obtenirUrlBaseActive()
                println("🌐 URL PocketBase obtenue: $urlBase")

                // Endpoint OAuth2 PocketBase pour Google
                val urlOAuth = "$urlBase/api/collections/users/auth-with-oauth2"
                println("🔗 URL OAuth2 complète: $urlOAuth")

                // Données pour l'authentification OAuth2
                val donneesOAuth = JsonObject().apply {
                    addProperty("provider", "google")
                    addProperty("code", codeAutorisation)
                    addProperty("codeVerifier", "") // Standard pour le flux web/mobile
                    addProperty("redirectUrl", "http://localhost:8090") // Requis par PocketBase
                    // createData est optionnel, PocketBase prendra les infos du profil Google
                }
                val corpsRequeteString = donneesOAuth.toString()
                println("📦 Corps de la requête JSON préparé: $corpsRequeteString")

                val corpsRequete = corpsRequeteString.toRequestBody("application/json".toMediaType())

                val requete = Request.Builder()
                    .url(urlOAuth)
                    .post(corpsRequete)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build()

                println("📡 Envoi de la requête à PocketBase... (Timeout après 15s)")
                val reponse = client.newCall(requete).execute()
                println("📨 Réponse reçue de PocketBase ! Status: ${reponse.code}")

                val corpsReponse = reponse.body?.string() ?: ""
                println("📄 Corps de la réponse (brut): ${corpsReponse.take(500)}...")

                if (reponse.isSuccessful) {
                    println("✅ AUTHENTIFICATION GOOGLE RÉUSSIE !")
                    try {
                        val reponseAuth = gson.fromJson(corpsReponse, ReponseAuthentification::class.java)
                        tokenAuthentification = reponseAuth.token
                        utilisateurConnecte = reponseAuth.record
                        println("👤 Utilisateur connecté: ${reponseAuth.record.email} | Token: ${reponseAuth.token.take(20)}...")
                        Result.success(Unit)
                    } catch (e: Exception) {
                        println("❌ Erreur de parsing de la réponse JSON: ${e.message}")
                        println("📄 Réponse complète qui a causé l'erreur: $corpsReponse")
                        Result.failure(Exception("Erreur de parsing de la réponse JSON: ${e.message}"))
                    }
                } else {
                    println("❌ La réponse du serveur indique une erreur (code ${reponse.code})")
                    
                    // Analyse détaillée de l'erreur
                    val messageErreur = analyserErreurServeur(reponse.code, corpsReponse)
                    println("🔍 Analyse de l'erreur: $messageErreur")
                    
                    Result.failure(Exception(messageErreur))
                }

            } catch (e: IOException) {
                println("❌ Erreur réseau/timeout: ${e.javaClass.simpleName} - ${e.message}")
                UrlResolver.invaliderCache()
                
                val messageErreur = when {
                    e.message?.contains("timeout", ignoreCase = true) == true -> 
                        "Le serveur ne répond pas dans les délais. Vérifiez votre connexion internet."
                    e.message?.contains("unknown host", ignoreCase = true) == true -> 
                        "Impossible de joindre le serveur. Vérifiez l'adresse du serveur."
                    e.message?.contains("connection refused", ignoreCase = true) == true -> 
                        "Connexion refusée par le serveur. Vérifiez que PocketBase est démarré."
                    else -> "Erreur réseau ou timeout. Le serveur est-il accessible à l'adresse ${UrlResolver.obtenirUrlActuelle()}? Message: ${e.message}"
                }
                
                Result.failure(Exception(messageErreur))
            } catch (e: Exception) {
                println("❌ Erreur inattendue: ${e.javaClass.simpleName} - ${e.message}")
                Result.failure(Exception("Erreur inattendue: ${e.message}"))
            }
        }

    /**
     * Analyse l'erreur retournée par le serveur
     */
    private fun analyserErreurServeur(code: Int, corpsReponse: String): String {
        return when (code) {
            400 -> {
                when {
                    corpsReponse.contains("invalid_code", ignoreCase = true) -> 
                        "Code d'autorisation Google invalide. Vérifiez votre configuration Google."
                    corpsReponse.contains("invalid_provider", ignoreCase = true) -> 
                        "Fournisseur OAuth2 non configuré. Vérifiez la configuration PocketBase."
                    corpsReponse.contains("missing_code", ignoreCase = true) -> 
                        "Code d'autorisation manquant. Vérifiez la configuration Google Sign-In."
                    corpsReponse.contains("redirectURL", ignoreCase = true) -> 
                        "Configuration OAuth2 incomplète. Vérifiez la configuration PocketBase."
                    corpsReponse.contains("validation_required", ignoreCase = true) -> 
                        "Données de requête invalides. Vérifiez la configuration OAuth2."
                    else -> "Requête invalide (400). Détails: $corpsReponse"
                }
            }
            401 -> "Authentification échouée. Vérifiez vos identifiants Google."
            403 -> "Accès refusé. Vérifiez les permissions de votre compte Google."
            404 -> "Endpoint OAuth2 introuvable. Vérifiez la configuration PocketBase."
            500 -> "Erreur interne du serveur PocketBase. Contactez l'administrateur."
            502 -> "Serveur PocketBase temporairement indisponible. Réessayez plus tard."
            503 -> "Service PocketBase en maintenance. Réessayez plus tard."
            else -> "Erreur serveur (code $code). Détails: $corpsReponse"
        }
    }

    /**
     * Déconnecte l'utilisateur actuel
     */
    fun deconnecter() {
        println("👋 Déconnexion de l'utilisateur")
        tokenAuthentification = null
        utilisateurConnecte = null
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
     * Obtient l'URL de base active de PocketBase.
     */
    suspend fun obtenirUrlBaseActive(): String {
        return UrlResolver.obtenirUrlActive()
    }

    /**
     * Obtient le token d'authentification actuel
     */
    fun obtenirToken(): String? {
        return tokenAuthentification
    }

    /**
     * Teste la connexion à PocketBase (pour debug)
     */
    suspend fun testerConnexionPocketBase(): Result<String> = withContext(Dispatchers.IO) {
        try {
            println("🔍 Test de connexion PocketBase...")
            val urlBase = obtenirUrlBaseActive()
            val urlTest = "$urlBase/api/health"
            
            val requete = Request.Builder()
                .url(urlTest)
                .get()
                .build()
            
            val reponse = client.newCall(requete).execute()
            val corpsReponse = reponse.body?.string() ?: ""
            
            if (reponse.isSuccessful) {
                println("✅ Test de connexion réussi")
                Result.success("Connexion OK - $urlBase")
            } else {
                println("❌ Test de connexion échoué - Code ${reponse.code}")
                Result.failure(Exception("Test échoué - Code ${reponse.code}: $corpsReponse"))
            }
        } catch (e: Exception) {
            println("❌ Erreur lors du test de connexion: ${e.message}")
            Result.failure(e)
        }
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