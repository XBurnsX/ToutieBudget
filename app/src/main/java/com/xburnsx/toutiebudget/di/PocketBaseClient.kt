// chemin/simule: app/src/main/java/com/xburnsx/toutiebudget/di/PocketBaseClient.kt
// Dépendances: UrlResolver, OkHttp3, Gson, Coroutines

package com.xburnsx.toutiebudget.di

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.JsonObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.content.Context
import android.util.Log
import com.xburnsx.toutiebudget.utils.SafeDateAdapter
import androidx.core.content.edit

/**
 * Client PocketBase personnalisé qui gère automatiquement :
 * - La résolution d'URL (local vs externe)
 * - L'authentification Google OAuth2
 * - La gestion des erreurs réseau
 * - Les timeouts appropriés
 * - Les logs détaillés pour le debug
 */
object PocketBaseClient {

    // Client HTTP configuré pour PocketBase - OPTIMISÉ pour les performances
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS) // Réduit de 10s à 3s
        .readTimeout(8, TimeUnit.SECONDS) // Réduit de 15s à 8s
        .writeTimeout(5, TimeUnit.SECONDS) // Réduit de 10s à 5s
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES)) // Pool de connexions persistantes
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)) // Support HTTP/2
        // OkHttp décompresse automatiquement gzip/deflate, pas besoin d'intercepteur spécial
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Accept-Encoding", "gzip, deflate") // Compression
                .addHeader("Connection", "keep-alive") // Connexions persistantes
                .build()
            val response = chain.proceed(request)
            // Vérifier que la réponse est bien décompressée
            Log.d("PocketBaseClient", "📦 Content-Encoding: ${response.header("Content-Encoding")}")
            response
        }
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
     * 🆕 MODE HORS LIGNE : Ne lance plus d'exception en cas d'erreur de connexion
     */
    suspend fun initialiser(): Boolean {
        return try {
            Log.d("PocketBaseClient", "🚀 Initialisation du client PocketBase...")
            val urlActive = UrlResolver.obtenirUrlActive()
            Log.d("PocketBaseClient", "🌐 URL active: $urlActive")
            
            // Test de connectivité (ne lance plus d'exception)
            testerConnectivite(urlActive)
            
            Log.d("PocketBaseClient", "✅ Initialisation réussie")
            true // ✅ Initialisation réussie
        } catch (e: Exception) {
            Log.e("PocketBaseClient", "❌ Erreur lors de l'initialisation: ${e.message}", e)
            // 🆕 MODE HORS LIGNE : Log de l'erreur mais pas d'exception
            // ⚠️ Erreur lors de l'initialisation (mode hors ligne autorisé): ${e.message}
            false // ❌ Initialisation échouée mais app peut continuer
        }
    }

    /**
     * Teste la connectivité vers PocketBase
     * 🆕 MODE HORS LIGNE : Ne lance plus d'exception
     */
    private suspend fun testerConnectivite(urlBase: String) {
        try {
            val urlHealth = "${urlBase.trimEnd('/')}/api/health"
            Log.d("PocketBaseClient", "🏥 Test de connectivité vers: $urlHealth")
            val requete = Request.Builder()
                .url(urlHealth)
                .get()
                .build()
            
            val reponse = withContext(Dispatchers.IO) {
                client.newCall(requete).execute()
            }
            
            if (!reponse.isSuccessful) {
                Log.w("PocketBaseClient", "⚠️ Serveur PocketBase non accessible (code: ${reponse.code})")
            } else {
                Log.d("PocketBaseClient", "✅ Serveur PocketBase accessible")
            }
        } catch (e: Exception) {
            Log.w("PocketBaseClient", "⚠️ Erreur de connectivité vers PocketBase: ${e.message}", e)
        }
    }

    /**
     * Connecte l'utilisateur via Google OAuth2
     * @param codeAutorisation Le code d'autorisation obtenu de Google
     */
    suspend fun connecterAvecGoogle(codeAutorisation: String, context: Context): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                Log.d("PocketBaseClient", "🔐 Début de l'authentification Google OAuth2")
                
                val urlBase = obtenirUrlBaseActive()
                Log.d("PocketBaseClient", "🌐 URL de base PocketBase: $urlBase")

                // Endpoint OAuth2 PocketBase pour Google
                val urlOAuth = "$urlBase/api/collections/users/auth-with-oauth2"
                Log.d("PocketBaseClient", "🔗 URL OAuth2: $urlOAuth")

                // Données pour l'authentification OAuth2
                // IMPORTANT : Le redirectURL doit correspondre EXACTEMENT à ce qui est configuré dans Google Cloud Console et PocketBase
                // Utiliser l'URL de base active (https://pocketbase.toutieserver.com)
                val redirectURL = urlBase.trimEnd('/')
                Log.d("PocketBaseClient", "↩️ Redirect URL: $redirectURL")
                
                val donneesOAuth = JsonObject().apply {
                    addProperty("provider", "google")
                    addProperty("code", codeAutorisation)
                    addProperty("codeVerifier", "") // Standard pour le flux web/mobile
                    addProperty("redirectURL", redirectURL) // URL de base active (sans path)
                    // createData est optionnel, PocketBase prendra les infos du profil Google
                }
                val corpsRequeteString = donneesOAuth.toString()

                val corpsRequete = corpsRequeteString.toRequestBody("application/json".toMediaType())

                val requete = Request.Builder()
                    .url(urlOAuth)
                    .post(corpsRequete)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    // Désactiver la compression pour éviter les problèmes de décompression
                    .addHeader("Accept-Encoding", "identity")
                    .build()

                Log.d("PocketBaseClient", "🚀 Envoi de la requête OAuth2 à PocketBase...")
                val reponse = client.newCall(requete).execute()

                // Vérifier le Content-Encoding avant de lire le body
                val contentEncoding = reponse.header("Content-Encoding")
                Log.d("PocketBaseClient", "📥 Réponse reçue - Code: ${reponse.code}")
                Log.d("PocketBaseClient", "📥 Content-Encoding: $contentEncoding")
                Log.d("PocketBaseClient", "📥 Content-Type: ${reponse.header("Content-Type")}")
                
                // Le corps d'une réponse réussie contient le jeton : ne jamais le logger
                val corpsReponse = reponse.body?.string() ?: ""
                Log.d("PocketBaseClient", "📥 Longueur de la réponse: ${corpsReponse.length} caractères")

                if (reponse.isSuccessful) {
                    Log.d("PocketBaseClient", "✅ Authentification réussie!")
                    try {
                        // Vérifier si la réponse est vide ou non-JSON
                        if (corpsReponse.isBlank()) {
                            Log.e("PocketBaseClient", "❌ Réponse vide de PocketBase")
                            Result.failure(Exception("Réponse vide de PocketBase. Vérifiez la configuration OAuth dans PocketBase."))
                        } else if (!corpsReponse.trimStart().startsWith("{")) {
                            Log.e("PocketBaseClient", "❌ Réponse n'est pas un JSON valide")
                            Result.failure(Exception("Réponse invalide de PocketBase"))
                        } else {
                            val reponseAuth = gson.fromJson(corpsReponse, ReponseAuthentification::class.java)
                            sauvegarderToken(context, reponseAuth.token)
                            sauvegarderUtilisateur(context, reponseAuth.record)
                            Log.d("PocketBaseClient", "💾 Token et utilisateur sauvegardés")
                            Result.success(Unit)
                        }
                    } catch (e: Exception) {
                        Log.e("PocketBaseClient", "❌ Erreur de parsing JSON: ${e.message}", e)
                        Result.failure(Exception("Erreur de parsing de la réponse JSON: ${e.message}"))
                    }
                } else {
                    // Analyse détaillée de l'erreur
                    val messageErreur = analyserErreurServeur(reponse.code, corpsReponse)
                    Log.e("PocketBaseClient", "❌ Erreur serveur - Code: ${reponse.code}, Message: $messageErreur")
                    Log.e("PocketBaseClient", "❌ Réponse complète: $corpsReponse")
                    
                    Result.failure(Exception(messageErreur))
                }

            } catch (e: javax.net.ssl.SSLHandshakeException) {
                Log.e("PocketBaseClient", "❌ Erreur SSL Handshake: ${e.message}", e)
                Log.e("PocketBaseClient", "❌ Problème de certificat SSL. Vérifiez que le certificat de pocketbase.toutieserver.com est valide.")
                UrlResolver.invaliderCache()
                Result.failure(Exception("Erreur de certificat SSL. Le certificat du serveur n'est pas valide ou n'est pas reconnu."))
            } catch (e: javax.net.ssl.SSLPeerUnverifiedException) {
                Log.e("PocketBaseClient", "❌ Erreur SSL Peer Unverified: ${e.message}", e)
                Log.e("PocketBaseClient", "❌ Le certificat SSL du serveur n'est pas vérifié.")
                UrlResolver.invaliderCache()
                Result.failure(Exception("Erreur de certificat SSL. Le certificat du serveur n'est pas vérifié."))
            } catch (e: java.net.UnknownHostException) {
                Log.e("PocketBaseClient", "❌ Host inconnu: ${e.message}", e)
                UrlResolver.invaliderCache()
                Result.failure(Exception("Impossible de joindre le serveur. Vérifiez votre connexion internet et l'adresse du serveur."))
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("PocketBaseClient", "❌ Timeout: ${e.message}", e)
                UrlResolver.invaliderCache()
                Result.failure(Exception("Le serveur ne répond pas dans les délais. Vérifiez votre connexion internet."))
            } catch (e: java.net.ConnectException) {
                Log.e("PocketBaseClient", "❌ Connexion refusée: ${e.message}", e)
                UrlResolver.invaliderCache()
                Result.failure(Exception("Connexion refusée par le serveur. Vérifiez que PocketBase est démarré."))
            } catch (e: IOException) {
                Log.e("PocketBaseClient", "❌ Erreur IO: ${e.message}", e)
                Log.e("PocketBaseClient", "❌ Type d'exception: ${e.javaClass.simpleName}")
                Log.e("PocketBaseClient", "❌ Stack trace: ${e.stackTraceToString()}")
                UrlResolver.invaliderCache()
                
                val messageErreur = when {
                    e.message?.contains("timeout", ignoreCase = true) == true -> 
                        "Le serveur ne répond pas dans les délais. Vérifiez votre connexion internet."
                    e.message?.contains("unknown host", ignoreCase = true) == true -> 
                        "Impossible de joindre le serveur. Vérifiez l'adresse du serveur."
                    e.message?.contains("connection refused", ignoreCase = true) == true -> 
                        "Connexion refusée par le serveur. Vérifiez que PocketBase est démarré."
                    e.message?.contains("SSL", ignoreCase = true) == true ->
                        "Erreur de certificat SSL. Vérifiez que le certificat du serveur est valide."
                    else -> "Erreur réseau. Le serveur est-il accessible à ${UrlResolver.obtenirUrlActuelle()}? Message: ${e.message}"
                }
                
                Result.failure(Exception(messageErreur))
            } catch (e: Exception) {
                Log.e("PocketBaseClient", "❌ Erreur inattendue: ${e.message}", e)
                Log.e("PocketBaseClient", "❌ Type d'exception: ${e.javaClass.simpleName}")
                Log.e("PocketBaseClient", "❌ Stack trace: ${e.stackTraceToString()}")
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
     * Vérifie si le serveur PocketBase est accessible
     * @return true si le serveur répond, false sinon
     */
    suspend fun verifierConnexionServeur(): Boolean {
        return try {
            val urlBase = UrlResolver.obtenirUrlActive()
            val requete = Request.Builder()
                .url("${urlBase.trimEnd('/')}/api/health")
                .get()
                .build()

            val reponse = withContext(Dispatchers.IO) {
                client.newCall(requete).execute()
            }

            reponse.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Charge les données d'authentification sauvegardées depuis les SharedPreferences
     */
    fun chargerAuthentificationSauvegardee(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)
        val userJson = prefs.getString(KEY_USER, null)

        return if (token != null && userJson != null) {
            try {
                tokenAuthentification = token
                utilisateurConnecte = gson.fromJson(userJson, EnregistrementUtilisateur::class.java)
                true
            } catch (_: Exception) {
                false
            }
        } else {
            false
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
            } catch (_: Exception) {
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
        prefs.edit { putString(KEY_TOKEN, token) }
    }

    /**
     * Sauvegarde les informations utilisateur
     */
    private fun sauvegarderUtilisateur(context: Context, utilisateur: EnregistrementUtilisateur) {
        utilisateurConnecte = utilisateur
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userJson = gson.toJson(utilisateur)
        prefs.edit { putString(KEY_USER, userJson) }
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
     * Effectue une requête GET avec paramètres optionnels
     */
    suspend fun effectuerRequeteGet(endpoint: String, parametres: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        try {
            val urlBase = obtenirUrlBaseActive()
            var url = "${urlBase.trimEnd('/')}$endpoint"

            // Ajouter les paramètres de requête
            if (parametres.isNotEmpty()) {
                val params = parametres.map { "${it.key}=${it.value}" }.joinToString("&")
                url += "?$params"
            }

            val requeteBuilder = Request.Builder()
                .url(url)
                .get()

            tokenAuthentification?.let { token ->
                requeteBuilder.addHeader("Authorization", "Bearer $token")
            }

            val requete = requeteBuilder.build()
            val reponse = client.newCall(requete).execute()

            if (reponse.isSuccessful) {
                reponse.body?.string() ?: ""
            } else {
                throw Exception("Erreur HTTP ${reponse.code}: ${reponse.message}")
            }
        } catch (e: IOException) {
            // En cas d'erreur réseau, invalider le cache et réessayer une fois
            UrlResolver.invaliderCache()
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Effectue une requête DELETE
     */
    suspend fun effectuerRequeteDelete(endpoint: String): String = withContext(Dispatchers.IO) {
        try {
            val urlBase = obtenirUrlBaseActive()
            val url = "${urlBase.trimEnd('/')}$endpoint"

            val requeteBuilder = Request.Builder()
                .url(url)
                .delete()

            tokenAuthentification?.let { token ->
                requeteBuilder.addHeader("Authorization", "Bearer $token")
            }

            val requete = requeteBuilder.build()
            val reponse = client.newCall(requete).execute()

            if (reponse.isSuccessful) {
                reponse.body?.string() ?: ""
            } else {
                throw Exception("Erreur HTTP ${reponse.code}: ${reponse.message}")
            }
        } catch (e: IOException) {
            // En cas d'erreur réseau, invalider le cache et réessayer une fois
            UrlResolver.invaliderCache()
            throw e
        } catch (e: Exception) {
            throw e
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