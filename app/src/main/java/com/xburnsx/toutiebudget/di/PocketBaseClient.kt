// chemin/simule: app/src/main/java/com/xburnsx/toutiebudget/di/PocketBaseClient.kt
// Dépendances: UrlResolver, OkHttp3, Gson, Coroutines

package com.xburnsx.toutiebudget.di

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.util.Date
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.content.Context
import com.xburnsx.toutiebudget.utils.SafeDateAdapter

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

    private val gson = com.google.gson.GsonBuilder()
        .registerTypeAdapter(java.util.Date::class.java, SafeDateAdapter())
        .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
    private var tokenAuthentification: String? = null
    private var utilisateurConnecte: EnregistrementUtilisateur? = null

    private const val PREFS_NAME = "pocketbase_prefs"
    private const val KEY_TOKEN = "token_auth"
    private const val KEY_USER = "user_auth"

    /**
     * Initialise le client en résolvant l'URL active
     */
    suspend fun initialiser() {
        try {
            val urlActive = UrlResolver.obtenirUrlActive()
            
            // Test de connectivité
            testerConnectivite(urlActive)
            
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Teste la connectivité vers PocketBase
     */
    private suspend fun testerConnectivite(urlBase: String) {
        try {
            val requete = Request.Builder()
                .url("${urlBase.trimEnd('/')}/api/health")
                .get()
                .build()
            
            val reponse = withContext(Dispatchers.IO) {
                client.newCall(requete).execute()
            }
            
            if (!reponse.isSuccessful) {
                // Log silencieux pour les erreurs de connectivité
            }
        } catch (e: Exception) {
            // Log silencieux pour les erreurs de connectivité
        }
    }

    /**
     * Connecte l'utilisateur via Google OAuth2
     * @param codeAutorisation Le code d'autorisation obtenu de Google
     */
    suspend fun connecterAvecGoogle(codeAutorisation: String, context: Context): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val urlBase = obtenirUrlBaseActive()

                // Endpoint OAuth2 PocketBase pour Google
                val urlOAuth = "$urlBase/api/collections/users/auth-with-oauth2"

                // Données pour l'authentification OAuth2
                // IMPORTANT : Le redirectURL doit correspondre EXACTEMENT à ce qui est configuré dans Google Cloud Console
                // On utilise localhost car Google refuse les IPs privées, mais on communique quand même avec l'IP locale
                val donneesOAuth = JsonObject().apply {
                    addProperty("provider", "google")
                    addProperty("code", codeAutorisation)
                    addProperty("codeVerifier", "") // Standard pour le flux web/mobile
                    addProperty("redirectURL", "http://localhost:8090") // Sans le path complet
                    // createData est optionnel, PocketBase prendra les infos du profil Google
                }
                val corpsRequeteString = donneesOAuth.toString()

                val corpsRequete = corpsRequeteString.toRequestBody("application/json".toMediaType())

                val requete = Request.Builder()
                    .url(urlOAuth)
                    .post(corpsRequete)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build()

                val reponse = client.newCall(requete).execute()

                val corpsReponse = reponse.body?.string() ?: ""

                if (reponse.isSuccessful) {
                    try {
                        val reponseAuth = gson.fromJson(corpsReponse, ReponseAuthentification::class.java)
                        sauvegarderToken(context, reponseAuth.token)
                        sauvegarderUtilisateur(context, reponseAuth.record)
                        Result.success(Unit)
                    } catch (e: Exception) {
                        Result.failure(Exception("Erreur de parsing de la réponse JSON: ${e.message}"))
                    }
                } else {
                    // Analyse détaillée de l'erreur
                    val messageErreur = analyserErreurServeur(reponse.code, corpsReponse)
                    
                    Result.failure(Exception(messageErreur))
                }

            } catch (e: IOException) {
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
                    corpsReponse.contains("invalid_grant", ignoreCase = true) -> 
                        "Code d'autorisation Google invalide ou expiré. Réessayez la connexion."
                    corpsReponse.contains("invalid_code", ignoreCase = true) -> 
                        "Code d'autorisation Google invalide. Vérifiez votre configuration Google."
                    corpsReponse.contains("invalid_provider", ignoreCase = true) -> 
                        "Fournisseur OAuth2 non configuré. Vérifiez la configuration PocketBase."
                    corpsReponse.contains("missing_code", ignoreCase = true) -> 
                        "Code d'autorisation manquant. Vérifiez la configuration Google."
                    else -> "Requête invalide (400). Vérifiez la configuration OAuth2."
                }
            }
            401 -> "Authentification échouée. Vérifiez vos identifiants Google."
            403 -> "Accès refusé. Vérifiez les permissions de votre compte Google."
            404 -> "Endpoint OAuth2 introuvable. Vérifiez la configuration PocketBase."
            500 -> "Erreur serveur interne. Contactez l'administrateur."
            else -> "Erreur serveur (code $code): $corpsReponse"
        }
    }

    /**
     * Déconnecte l'utilisateur
     */
    fun deconnecter(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove(KEY_TOKEN)
            remove(KEY_USER)
            apply()
        }
        tokenAuthentification = null
        utilisateurConnecte = null
    }

    /**
     * Charge le token depuis les préférences
     */
    fun chargerToken(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        tokenAuthentification = prefs.getString(KEY_TOKEN, null)
        
        val userJson = prefs.getString(KEY_USER, null)
        if (userJson != null) {
            try {
                utilisateurConnecte = gson.fromJson(userJson, EnregistrementUtilisateur::class.java)
            } catch (e: Exception) {
                // Ignorer les erreurs de parsing
            }
        }
    }

    /**
     * Sauvegarde le token d'authentification
     */
    private fun sauvegarderToken(context: Context, token: String) {
        tokenAuthentification = token
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    /**
     * Sauvegarde les informations utilisateur
     */
    private fun sauvegarderUtilisateur(context: Context, utilisateur: EnregistrementUtilisateur) {
        utilisateurConnecte = utilisateur
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userJson = gson.toJson(utilisateur)
        prefs.edit().putString(KEY_USER, userJson).apply()
    }

    /**
     * Vérifie si l'utilisateur est connecté
     */
    fun estConnecte(): Boolean {
        return tokenAuthentification != null
    }

    /**
     * Récupère l'utilisateur connecté
     */
    fun obtenirUtilisateurConnecte(): EnregistrementUtilisateur? {
        return utilisateurConnecte
    }

    /**
     * Récupère le token d'authentification
     */
    fun obtenirToken(): String? {
        return tokenAuthentification
    }

    /**
     * Obtient l'URL de base active
     */
    suspend fun obtenirUrlBaseActive(): String {
        return UrlResolver.obtenirUrlActive()
    }

    /**
     * Teste la connexion à PocketBase
     */
    suspend fun testerConnexion(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val urlBase = obtenirUrlBaseActive()
            val requete = Request.Builder()
                .url("${urlBase.trimEnd('/')}/api/health")
                .get()
                .build()
            
            val reponse = client.newCall(requete).execute()
            
            if (reponse.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Erreur de connexion - Code ${reponse.code}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Erreur lors du test de connexion: ${e.message}"))
        }
    }

    // Classes de données pour la réponse d'authentification
    data class ReponseAuthentification(
        val token: String,
        val record: EnregistrementUtilisateur
    )

    data class EnregistrementUtilisateur(
        val id: String,
        val email: String,
        val name: String? = null,
        val avatar: String? = null,
        val created: String? = null,
        val updated: String? = null
    )
}