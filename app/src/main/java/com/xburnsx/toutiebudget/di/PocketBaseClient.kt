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

    // Client HTTP configuré pour PocketBase - OPTIMISÉ pour les performances
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS) // Réduit de 10s à 3s
        .readTimeout(8, TimeUnit.SECONDS) // Réduit de 15s à 8s
        .writeTimeout(5, TimeUnit.SECONDS) // Réduit de 10s à 5s
        .retryOnConnectionFailure(true)
        .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES)) // Pool de connexions persistantes
        .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1)) // Support HTTP/2
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Accept-Encoding", "gzip, deflate") // Compression
                .addHeader("Connection", "keep-alive") // Connexions persistantes
                .build()
            chain.proceed(request)
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

                // Gérer le contenu compressé (gzip)
                val corpsReponse = if (reponse.header("Content-Encoding") == "gzip") {
                    // Décompresser le contenu gzippé
                    val inputStream = reponse.body?.byteStream()
                    if (inputStream != null) {
                        try {
                            java.util.zip.GZIPInputStream(inputStream).bufferedReader().use { reader ->
                                reader.readText()
                            }
                        } catch (e: Exception) {
                            println("[DEBUG] Erreur décompression gzip: ${e.message}")
                            reponse.body?.string() ?: ""
                        }
                    } else {
                        reponse.body?.string() ?: ""
                    }
                } else {
                    reponse.body?.string() ?: ""
                }

                if (reponse.isSuccessful) {
                    try {
                        // Nettoyer et valider la réponse JSON
                        val corpsReponseNettoye = nettoyerReponseJSON(corpsReponse)
                        println("[DEBUG] Réponse brute: '$corpsReponse'")
                        println("[DEBUG] Réponse nettoyée: '$corpsReponseNettoye'")
                        
                        val reponseAuth = gson.fromJson(corpsReponseNettoye, ReponseAuthentification::class.java)
                        
                        // Vérifier si la réponse contient une erreur
                        if (reponseAuth.error != null || reponseAuth.message != null) {
                            val messageErreur = reponseAuth.error ?: reponseAuth.message ?: "Erreur inconnue"
                            return@withContext Result.failure(Exception("Erreur serveur: $messageErreur"))
                        }
                        
                        // Vérifier que les données requises sont présentes
                        if (reponseAuth.token == null || reponseAuth.record == null) {
                            return@withContext Result.failure(Exception("Réponse d'authentification incomplète: token ou utilisateur manquant"))
                        }
                        
                        sauvegarderToken(context, reponseAuth.token)
                        sauvegarderUtilisateur(context, reponseAuth.record)
                        Result.success(Unit)
                    } catch (e: Exception) {
                        println("[DEBUG] Erreur de parsing JSON: ${e.message}")
                        println("[DEBUG] Corps de réponse: '$corpsReponse'")
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
     * Nettoie et valide une réponse JSON du serveur
     */
    private fun nettoyerReponseJSON(reponse: String): String {
        if (reponse.isBlank()) {
            throw Exception("Réponse vide du serveur")
        }
        
        // Supprimer les espaces en début et fin
        val reponseNettoyee = reponse.trim()
        
        // Vérifier si c'est déjà un JSON valide
        if (reponseNettoyee.startsWith("{") && reponseNettoyee.endsWith("}")) {
            return reponseNettoyee
        }
        
        // Si c'est une chaîne simple, essayer de la traiter comme un message d'erreur
        if (!reponseNettoyee.startsWith("{")) {
            // Créer un objet JSON avec le message d'erreur
            return """
                {
                    "error": "$reponseNettoyee",
                    "message": "Réponse non-JSON reçue du serveur"
                }
            """.trimIndent()
        }
        
        return reponseNettoyee
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
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Vérifie si l'utilisateur est déjà authentifié
     * @return true si un token valide existe, false sinon
     */
    fun verifierAuthentification(): Boolean {
        return tokenAuthentification != null && utilisateurConnecte != null
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
            } catch (e: Exception) {
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
                // Gérer le contenu compressé (gzip)
                if (reponse.header("Content-Encoding") == "gzip") {
                    val inputStream = reponse.body?.byteStream()
                    if (inputStream != null) {
                        try {
                            java.util.zip.GZIPInputStream(inputStream).bufferedReader().use { reader ->
                                reader.readText()
                            }
                        } catch (e: Exception) {
                            println("[DEBUG] Erreur décompression gzip: ${e.message}")
                            reponse.body?.string() ?: ""
                        }
                    } else {
                        reponse.body?.string() ?: ""
                    }
                } else {
                    reponse.body?.string() ?: ""
                }
            } else {
                throw Exception("Erreur HTTP ${reponse.code}: ${reponse.message}")
            }
        } catch (e: IOException) {
            // En cas d'erreur réseau, invalider le cache et réessayer une fois
            println("[PocketBaseClient] Erreur réseau détectée, invalidation du cache URL")
            UrlResolver.invaliderCache()
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Effectue une requête POST avec données JSON
     */
    suspend fun effectuerRequetePost(endpoint: String, donneesJson: String): String = withContext(Dispatchers.IO) {
        try {
            val urlBase = obtenirUrlBaseActive()
            val url = "${urlBase.trimEnd('/')}$endpoint"

            val corpsRequete = donneesJson.toRequestBody("application/json".toMediaType())

            val requeteBuilder = Request.Builder()
                .url(url)
                .post(corpsRequete)
                .addHeader("Content-Type", "application/json")

            tokenAuthentification?.let { token ->
                requeteBuilder.addHeader("Authorization", "Bearer $token")
            }

            val requete = requeteBuilder.build()
            val reponse = client.newCall(requete).execute()

            if (reponse.isSuccessful) {
                // Gérer le contenu compressé (gzip)
                if (reponse.header("Content-Encoding") == "gzip") {
                    val inputStream = reponse.body?.byteStream()
                    if (inputStream != null) {
                        try {
                            java.util.zip.GZIPInputStream(inputStream).bufferedReader().use { reader ->
                                reader.readText()
                            }
                        } catch (e: Exception) {
                            println("[DEBUG] Erreur décompression gzip: ${e.message}")
                            reponse.body?.string() ?: ""
                        }
                    } else {
                        reponse.body?.string() ?: ""
                    }
                } else {
                    reponse.body?.string() ?: ""
                }
            } else {
                throw Exception("Erreur HTTP ${reponse.code}: ${reponse.message}")
            }
        } catch (e: IOException) {
            // En cas d'erreur réseau, invalider le cache et réessayer une fois
            println("[PocketBaseClient] Erreur réseau détectée, invalidation du cache URL")
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
                // Gérer le contenu compressé (gzip)
                if (reponse.header("Content-Encoding") == "gzip") {
                    val inputStream = reponse.body?.byteStream()
                    if (inputStream != null) {
                        try {
                            java.util.zip.GZIPInputStream(inputStream).bufferedReader().use { reader ->
                                reader.readText()
                            }
                        } catch (e: Exception) {
                            println("[DEBUG] Erreur décompression gzip: ${e.message}")
                            reponse.body?.string() ?: ""
                        }
                    } else {
                        reponse.body?.string() ?: ""
                    }
                } else {
                    reponse.body?.string() ?: ""
                }
            } else {
                throw Exception("Erreur HTTP ${reponse.code}: ${reponse.message}")
            }
        } catch (e: IOException) {
            // En cas d'erreur réseau, invalider le cache et réessayer une fois
            println("[PocketBaseClient] Erreur réseau détectée, invalidation du cache URL")
            UrlResolver.invaliderCache()
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    // Classes de données pour la réponse d'authentification
    data class ReponseAuthentification(
        val token: String? = null,
        val record: EnregistrementUtilisateur? = null,
        val error: String? = null,
        val message: String? = null
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