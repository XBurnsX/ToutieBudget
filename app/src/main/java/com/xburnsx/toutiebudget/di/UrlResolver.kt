// chemin/simule: app/src/main/java/com/xburnsx/toutiebudget/di/UrlResolver.kt
// Dépendances: BuildConfig, DetecteurEmulateur, OkHttp3, Coroutines

package com.xburnsx.toutiebudget.di

import com.xburnsx.toutiebudget.BuildConfig
import com.xburnsx.toutiebudget.utils.DetecteurEmulateur
import com.xburnsx.toutiebudget.utils.TypeEnvironnement
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Service intelligent qui détermine automatiquement quelle URL PocketBase utiliser selon :
 * 1. Le type d'environnement (émulateur vs dispositif physique)
 * 2. La disponibilité des serveurs
 * 3. La latence réseau
 */
object UrlResolver {

    private var urlActive: String? = null
    private var derniereVerification: Long = 0L
    private var typeEnvironnementCache: TypeEnvironnement? = null
    private const val DUREE_CACHE_MS = 30_000L // 30 secondes

    // Client HTTP optimisé pour les vérifications rapides
    private val clientVerification = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false) // Pas de retry pour les tests
        .build()

    /**
     * Obtient l'URL active de PocketBase avec détection intelligente d'environnement
     */
    suspend fun obtenirUrlActive(): String = withContext(Dispatchers.IO) {
        val maintenant = System.currentTimeMillis()

        // Utiliser le cache si encore valide
        if (urlActive != null && (maintenant - derniereVerification) < DUREE_CACHE_MS) {
            return@withContext urlActive!!
        }

        val typeEnvironnement = obtenirTypeEnvironnement()

        println("🔍 Résolution URL PocketBase pour: ${typeEnvironnement.name}")

        // URLs à tester selon l'environnement (LOCAL SEULEMENT)
        val urlsATester = when (typeEnvironnement) {
            TypeEnvironnement.EMULATEUR -> listOf(
                "http://10.0.2.2:8090" to "Émulateur → Host (10.0.2.2)",
                "http://127.0.0.1:8090" to "Localhost émulateur"
            )
            TypeEnvironnement.DISPOSITIF_PHYSIQUE -> listOf(
                "http://192.168.1.77:8090" to "Serveur local réseau",
                "http://localhost:8090" to "Localhost dispositif"
            )
        }

        // Tester chaque URL dans l'ordre de priorité
        for ((url, description) in urlsATester) {
            if (testerConnexion(url, description)) {
                urlActive = url
                derniereVerification = maintenant
                println("✅ URL sélectionnée: $url ($description)")
                return@withContext url
            }
        }

        // Aucune URL ne fonctionne - utiliser la première par défaut
        val urlParDefaut = urlsATester.first().first
        println("⚠️ Aucun serveur accessible. Utilisation par défaut: $urlParDefaut")
        println("💡 Assure-toi que PocketBase tourne avec: ./pocketbase serve --http=0.0.0.0:8090")

        urlActive = urlParDefaut
        derniereVerification = maintenant

        return@withContext urlParDefaut
    }

    /**
     * Obtient le type d'environnement avec cache
     */
    private fun obtenirTypeEnvironnement(): TypeEnvironnement {
        if (typeEnvironnementCache == null) {
            typeEnvironnementCache = DetecteurEmulateur.obtenirTypeEnvironnement()
        }
        return typeEnvironnementCache!!
    }



    /**
     * Teste si une URL PocketBase est accessible
     */
    private suspend fun testerConnexion(url: String, description: String): Boolean {
        return try {
            println("🔗 Test: $description → $url")

            val requete = Request.Builder()
                .url("${url.trimEnd('/')}/api/health")
                .get()
                .addHeader("User-Agent", "ToutieBudget-Android")
                .build()

            val reponse = clientVerification.newCall(requete).execute()
            val estSucces = reponse.isSuccessful

            if (estSucces) {
                println("✅ $description accessible (${reponse.code})")
            } else {
                println("❌ $description inaccessible (${reponse.code})")
            }

            reponse.close()
            estSucces

        } catch (e: SocketTimeoutException) {
            println("⏰ $description - Timeout")
            false
        } catch (e: UnknownHostException) {
            println("🌐 $description - Hôte introuvable")
            false
        } catch (e: IOException) {
            println("📡 $description - Erreur réseau: ${e.message}")
            false
        } catch (e: Exception) {
            println("💥 $description - Erreur: ${e.message}")
            false
        }
    }

    /**
     * Force une nouvelle détection au prochain appel
     */
    fun invaliderCache() {
        println("🔄 Cache invalidé - prochaine vérification forcée")
        derniereVerification = 0L
        urlActive = null
        typeEnvironnementCache = null
    }

    /**
     * Obtient l'URL actuellement utilisée
     */
    fun obtenirUrlActuelle(): String? = urlActive

    /**
     * Obtient des statistiques de debug
     */
    fun obtenirStatistiquesDebug(): String {
        val infoEnv = DetecteurEmulateur.obtenirInfoEnvironnement()
        return """
            🔧 STATISTIQUES URL RESOLVER
            ============================
            URL active: ${urlActive ?: "Non définie"}
            Dernière vérification: ${if (derniereVerification > 0) java.util.Date(derniereVerification) else "Jamais"}
            Type environnement: ${typeEnvironnementCache?.name ?: "Non détecté"}
            
            📱 ENVIRONNEMENT DÉTECTÉ
            ========================
            $infoEnv
            
            🌐 URLS UTILISÉES
            =================
            Émulateur: http://10.0.2.2:8090, http://127.0.0.1:8090
            Dispositif: http://192.168.1.77:8090, http://localhost:8090
        """.trimIndent()
    }
}