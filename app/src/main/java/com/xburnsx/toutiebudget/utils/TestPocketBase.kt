package com.xburnsx.toutiebudget.utils

import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Utilitaire pour tester la connexion à PocketBase et diagnostiquer les problèmes
 */
object TestPocketBase {

    private val clientTest = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /**
     * Teste la connectivité vers PocketBase avec différentes URLs
     */
    suspend fun testerConnectiviteComplète(): String = withContext(Dispatchers.IO) {
        val rapport = StringBuilder()
        rapport.appendLine("🔍 === TEST DE CONNECTIVITÉ POCKETBASE ===")
        
        // Utiliser l'URL active au lieu de tester toutes les URLs
        val urlActive = com.xburnsx.toutiebudget.di.UrlResolver.obtenirUrlActive()
        rapport.appendLine("📡 Test de l'URL active : $urlActive")
        
        val resultat = testerUrl(urlActive)
        rapport.appendLine(resultat)
        
        // Test rapide des autres URLs seulement si l'URL active échoue
        if (!resultat.contains("✅ Health OK")) {
            rapport.appendLine("\n📡 Test des URLs de fallback...")
            
            val urlsFallback = listOf(
                "https://toutie-budget.pockethost.io" to "PocketHost",
                "http://10.0.2.2:8090" to "Local (émulateur)",
                "http://192.168.1.77:8090" to "Local (IP)"
            ).filter { it.first != urlActive } // Exclure l'URL déjà testée
            
            for ((url, description) in urlsFallback) {
                rapport.appendLine("\n📡 Test de $description sur $url")
                val resultatFallback = testerUrl(url)
                rapport.appendLine(resultatFallback)
            }
        } else {
            rapport.appendLine("✅ URL active fonctionne - Pas besoin de tester les autres URLs")
        }
        
        rapport.toString()
    }

    /**
     * Teste une URL spécifique
     */
    private suspend fun testerUrl(url: String): String {
        return try {
            // Test 1: Endpoint health
            val requeteHealth = Request.Builder()
                .url("${url.trimEnd('/')}/api/health")
                .get()
                .build()
            
            val reponseHealth = clientTest.newCall(requeteHealth).execute()
            val corpsHealth = reponseHealth.body?.string() ?: ""
            
            if (reponseHealth.isSuccessful) {
                "✅ Health OK (${reponseHealth.code}) - $corpsHealth"
            } else {
                "❌ Health échoué (${reponseHealth.code}) - $corpsHealth"
            }
            
        } catch (e: Exception) {
            "❌ Erreur: ${e.javaClass.simpleName} - ${e.message}"
        }
    }

    /**
     * Teste l'endpoint OAuth2 spécifiquement
     */
    suspend fun testerEndpointOAuth2(url: String): String = withContext(Dispatchers.IO) {
        val rapport = StringBuilder()
        rapport.appendLine("🔐 === TEST ENDPOINT OAUTH2 ===")
        rapport.appendLine("URL: $url")
        
        try {
            // Test GET (doit retourner 404 car c'est un endpoint POST)
            val requeteGet = Request.Builder()
                .url("${url.trimEnd('/')}/api/collections/users/auth-with-oauth2")
                .get()
                .build()
            
            val reponseGet = clientTest.newCall(requeteGet).execute()
            rapport.appendLine("📤 GET /api/collections/users/auth-with-oauth2")
            rapport.appendLine("   Status: ${reponseGet.code}")
            
            if (reponseGet.code == 404) {
                rapport.appendLine("   ✅ Normal - L'endpoint existe mais n'accepte que POST")
            } else {
                rapport.appendLine("   ⚠️ Inattendu - Code ${reponseGet.code}")
            }
            
            // Test POST avec données invalides (pour voir la réponse d'erreur)
            val donneesTest = """
                {
                    "provider": "google",
                    "code": "test_invalid_code",
                    "codeVerifier": "",
                    "redirectUrl": "http://localhost:8090"
                }
            """.trimIndent()
            
            val corpsRequete = donneesTest.toRequestBody("application/json".toMediaType())
            val requetePost = Request.Builder()
                .url("${url.trimEnd('/')}/api/collections/users/auth-with-oauth2")
                .post(corpsRequete)
                .addHeader("Content-Type", "application/json")
                .build()
            
            val reponsePost = clientTest.newCall(requetePost).execute()
            val corpsReponse = reponsePost.body?.string() ?: ""
            
            rapport.appendLine("📤 POST /api/collections/users/auth-with-oauth2")
            rapport.appendLine("   Status: ${reponsePost.code}")
            rapport.appendLine("   Réponse: ${corpsReponse.take(200)}...")
            
            when (reponsePost.code) {
                400 -> rapport.appendLine("   ✅ Endpoint accessible - Erreur 400 attendue pour code invalide")
                401 -> rapport.appendLine("   ⚠️ Endpoint accessible - Erreur 401 (authentification)")
                404 -> rapport.appendLine("   ❌ Endpoint introuvable - Vérifiez la configuration PocketBase")
                500 -> rapport.appendLine("   ⚠️ Erreur serveur - Vérifiez les logs PocketBase")
                else -> rapport.appendLine("   ❓ Code inattendu: ${reponsePost.code}")
            }
            
        } catch (e: Exception) {
            rapport.appendLine("❌ Erreur lors du test: ${e.message}")
        }
        
        rapport.toString()
    }

    /**
     * Teste la configuration Google OAuth2 dans PocketBase
     */
    suspend fun testerConfigurationGoogle(url: String): String = withContext(Dispatchers.IO) {
        val rapport = StringBuilder()
        rapport.appendLine("🔧 === TEST CONFIGURATION GOOGLE OAUTH2 ===")
        rapport.appendLine("URL: $url")
        
        try {
            // Test de l'endpoint des collections pour voir si la collection users existe
            val requeteCollections = Request.Builder()
                .url("${url.trimEnd('/')}/api/collections")
                .get()
                .build()
            
            val reponseCollections = clientTest.newCall(requeteCollections).execute()
            val corpsCollections = reponseCollections.body?.string() ?: ""
            
            rapport.appendLine("📤 GET /api/collections")
            rapport.appendLine("   Status: ${reponseCollections.code}")
            
            if (reponseCollections.isSuccessful) {
                if (corpsCollections.contains("users")) {
                    rapport.appendLine("   ✅ Collection 'users' trouvée")
                } else {
                    rapport.appendLine("   ❌ Collection 'users' introuvable")
                }
                
                if (corpsCollections.contains("google")) {
                    rapport.appendLine("   ✅ Configuration Google détectée")
                } else {
                    rapport.appendLine("   ⚠️ Configuration Google non détectée dans les collections")
                }
            } else if (reponseCollections.code == 401) {
                rapport.appendLine("   ⚠️ Authentification requise pour accéder aux collections")
                rapport.appendLine("   💡 C'est normal - les collections sont protégées")
                
                // Test alternatif : essayer d'accéder à l'endpoint OAuth2 directement
                rapport.appendLine("   🔍 Test alternatif : vérification de l'endpoint OAuth2...")
                val testOAuth2 = testerEndpointOAuth2(url)
                rapport.appendLine(testOAuth2)
            } else {
                rapport.appendLine("   ❌ Impossible d'accéder aux collections (code ${reponseCollections.code})")
            }
            
        } catch (e: Exception) {
            rapport.appendLine("❌ Erreur lors du test: ${e.message}")
        }
        
        rapport.toString()
    }

    /**
     * Teste spécifiquement la configuration OAuth2 de PocketBase
     */
    suspend fun testerConfigurationOAuth2(url: String): String = withContext(Dispatchers.IO) {
        val rapport = StringBuilder()
        rapport.appendLine("🔐 === TEST CONFIGURATION OAUTH2 POCKETBASE ===")
        rapport.appendLine("URL: $url")
        
        try {
            // Test 1: Vérifier si l'endpoint OAuth2 existe
            val requeteGet = Request.Builder()
                .url("${url.trimEnd('/')}/api/collections/users/auth-with-oauth2")
                .get()
                .build()
            
            val reponseGet = clientTest.newCall(requeteGet).execute()
            rapport.appendLine("📤 Test GET /api/collections/users/auth-with-oauth2")
            rapport.appendLine("   Status: ${reponseGet.code}")
            
            if (reponseGet.code == 404) {
                rapport.appendLine("   ✅ Endpoint OAuth2 existe (404 attendu pour GET)")
            } else {
                rapport.appendLine("   ⚠️ Code inattendu: ${reponseGet.code}")
            }
            
            // Test 2: Tester avec un code invalide mais structure correcte
            val donneesTest = """
                {
                    "provider": "google",
                    "code": "test_invalid_code_12345",
                    "codeVerifier": "",
                    "redirectUrl": "http://localhost:8090"
                }
            """.trimIndent()
            
            val corpsRequete = donneesTest.toRequestBody("application/json".toMediaType())
            val requetePost = Request.Builder()
                .url("${url.trimEnd('/')}/api/collections/users/auth-with-oauth2")
                .post(corpsRequete)
                .addHeader("Content-Type", "application/json")
                .build()
            
            val reponsePost = clientTest.newCall(requetePost).execute()
            val corpsReponse = reponsePost.body?.string() ?: ""
            
            rapport.appendLine("📤 Test POST /api/collections/users/auth-with-oauth2")
            rapport.appendLine("   Status: ${reponsePost.code}")
            rapport.appendLine("   Réponse: ${corpsReponse.take(300)}...")
            
            when (reponsePost.code) {
                400 -> {
                    rapport.appendLine("   ✅ Endpoint accessible")
                    when {
                        corpsReponse.contains("invalid_code", ignoreCase = true) -> 
                            rapport.appendLine("   ✅ Configuration OAuth2 correcte - Code invalide détecté")
                        corpsReponse.contains("redirectURL", ignoreCase = true) -> 
                            rapport.appendLine("   ⚠️ Configuration OAuth2 partielle - redirectURL requis")
                        corpsReponse.contains("provider", ignoreCase = true) -> 
                            rapport.appendLine("   ⚠️ Configuration OAuth2 partielle - Fournisseur non configuré")
                        else -> 
                            rapport.appendLine("   ⚠️ Erreur 400 inattendue - Vérifiez la configuration")
                    }
                }
                401 -> rapport.appendLine("   ⚠️ Authentification requise - Configuration partielle")
                404 -> rapport.appendLine("   ❌ Endpoint OAuth2 introuvable - Configuration manquante")
                500 -> rapport.appendLine("   ⚠️ Erreur serveur - Vérifiez les logs PocketBase")
                else -> rapport.appendLine("   ❓ Code inattendu: ${reponsePost.code}")
            }
            
        } catch (e: Exception) {
            rapport.appendLine("❌ Erreur lors du test: ${e.message}")
        }
        
        rapport.toString()
    }

    /**
     * Teste la configuration Google Sign-In
     */
    suspend fun testerConfigurationGoogleSignIn(): String = withContext(Dispatchers.IO) {
        val rapport = StringBuilder()
        rapport.appendLine("🔐 === TEST CONFIGURATION GOOGLE SIGN-IN ===")
        
        try {
            // Informations de base
            rapport.appendLine("📱 Informations de base:")
            rapport.appendLine("   - Package Name: com.xburnsx.toutiebudget")
            rapport.appendLine("   - Client ID: 857272496129-adbtb0bltka9siqarvll7s36l697bcs2.apps.googleusercontent.com")
            rapport.appendLine("   - Mode Debug: true")
            
            // Test de Google Play Services
            rapport.appendLine("\n🔧 Google Play Services:")
            try {
                // Note: Cette vérification nécessiterait un Context, donc on l'omet pour l'instant
                rapport.appendLine("   - Vérification: Nécessite un Context")
                rapport.appendLine("   - 💡 Vérifiez manuellement dans les paramètres de l'appareil")
            } catch (e: Exception) {
                rapport.appendLine("   - ❌ Erreur: ${e.message}")
            }
            
            // Recommandations pour SHA-1
            rapport.appendLine("\n🔑 Configuration SHA-1:")
            rapport.appendLine("   - Obtenez le SHA-1 de debug avec:")
            rapport.appendLine("     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android")
            rapport.appendLine("   - Ajoutez le SHA-1 dans Google Cloud Console")
            rapport.appendLine("   - Attendez 5-10 minutes pour la propagation")
            
            // Test de connectivité Google
            rapport.appendLine("\n🌐 Test de connectivité Google:")
            try {
                val requete = Request.Builder()
                    .url("https://accounts.google.com")
                    .get()
                    .build()
                
                val reponse = clientTest.newCall(requete).execute()
                if (reponse.isSuccessful) {
                    rapport.appendLine("   ✅ Connectivité Google OK")
                } else {
                    rapport.appendLine("   ⚠️ Connectivité Google partielle (${reponse.code})")
                }
            } catch (e: Exception) {
                rapport.appendLine("   ❌ Erreur de connectivité Google: ${e.message}")
            }
            
            // Recommandations générales
            rapport.appendLine("\n💡 Recommandations:")
            rapport.appendLine("   1. Vérifiez Google Play Services sur l'appareil")
            rapport.appendLine("   2. Vérifiez le SHA-1 dans Google Cloud Console")
            rapport.appendLine("   3. Testez sur un appareil physique")
            rapport.appendLine("   4. Vérifiez qu'un compte Google est configuré")
            
        } catch (e: Exception) {
            rapport.appendLine("❌ Erreur lors du test: ${e.message}")
        }
        
        rapport.toString()
    }

    /**
     * Lance tous les tests de diagnostic
     */
    suspend fun diagnosticComplet(): String {
        val rapport = StringBuilder()
        rapport.appendLine("🔍 === DIAGNOSTIC COMPLET POCKETBASE ===")
        
        // Test de connectivité (optimisé)
        rapport.appendLine(testerConnectiviteComplète())
        
        // Test avec l'URL active uniquement
        try {
            val urlActive = com.xburnsx.toutiebudget.di.UrlResolver.obtenirUrlActive()
            rapport.appendLine("\n" + testerEndpointOAuth2(urlActive))
            rapport.appendLine("\n" + testerConfigurationGoogle(urlActive))
            rapport.appendLine("\n" + testerConfigurationOAuth2(urlActive))
            
            // Test de configuration Google Sign-In
            rapport.appendLine("\n" + testerConfigurationGoogleSignIn())
            
            // Ajouter des recommandations basées sur les résultats
            rapport.appendLine("\n💡 RECOMMANDATIONS:")
            rapport.appendLine("   - URL active détectée : $urlActive")
            rapport.appendLine("   - Vérifiez la configuration OAuth2 dans PocketBase")
            rapport.appendLine("   - Assurez-vous que Google OAuth2 est activé")
            rapport.appendLine("   - Testez la connexion Google depuis l'application")
            rapport.appendLine("   - Vérifiez le SHA-1 dans Google Cloud Console")
            
        } catch (e: Exception) {
            rapport.appendLine("\n❌ Erreur lors de l'obtention de l'URL active: ${e.message}")
        }
        
        return rapport.toString()
    }
} 