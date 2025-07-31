package com.xburnsx.toutiebudget.data.services

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Service de cache intelligent pour améliorer les performances de l'application
 * SÉCURITÉ FINANCIÈRE CRITIQUE : Cache UNIQUEMENT les données statiques
 * Les données dynamiques (transactions, soldes, montants) ne sont JAMAIS cachées
 * PRIORITÉ AUX MODIFICATIONS : Le cache est invalidé automatiquement lors des modifications
 * COMPLEXITÉ NÉCESSAIRE : Validation multi-niveaux pour éviter les faux soldes
 */
class CacheService(
    private val context: Context?,
    private val validationService: CacheValidationService? = null
) {

    private val prefs: SharedPreferences = context?.getSharedPreferences("cache_prefs", Context.MODE_PRIVATE) 
        ?: throw IllegalArgumentException("Context requis pour le cache")
    private val gson = Gson()
    
    // Cache en mémoire pour les données fréquemment accédées (priorité aux modifications)
    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()
    
    // Timestamps des dernières modifications pour invalidation intelligente
    private val lastModificationTimestamps = ConcurrentHashMap<String, Long>()

    companion object {
        private const val CACHE_DURATION_HOURS = 1L // Cache de 1 heure pour les données statiques
        private const val KEY_LAST_CLEANUP = "last_cleanup"
        private const val CLEANUP_INTERVAL_HOURS = 24L // Nettoyage quotidien
        
        // Clés de cache pour les données VRAIMENT statiques uniquement
        private const val CACHE_KEY_CATEGORIES = "categories_list"
        private const val CACHE_KEY_TIERS = "tiers_list"
        private const val CACHE_KEY_COMPTES = "comptes_list" // ✅ AJOUTÉ - Comptes peuvent être cachés
        private const val CACHE_KEY_ENVELOPPES = "enveloppes_list" // ✅ AJOUTÉ - Enveloppes peuvent être cachées
        
        // Les données dynamiques ne sont JAMAIS cachées - LISTE EXHAUSTIVE
        // private const val CACHE_KEY_TRANSACTIONS = "transactions_list" // INTERDIT
        // private const val CACHE_KEY_SOLDES = "soldes_list" // INTERDIT
        // private const val CACHE_KEY_ALLOCATIONS = "allocations_list" // INTERDIT
        // private const val CACHE_KEY_HISTORIQUES = "historiques_list" // INTERDIT
        // private const val CACHE_KEY_MONTANTS = "montants_list" // INTERDIT
        // private const val CACHE_KEY_OBJECTIFS = "objectifs_list" // INTERDIT
        // private const val CACHE_KEY_ROLLOVERS = "rollovers_list" // INTERDIT
        // private const val CACHE_KEY_VIREMENTS = "virements_list" // INTERDIT
        // private const val CACHE_KEY_PRETS = "prets_list" // INTERDIT
        // private const val CACHE_KEY_DETTES = "dettes_list" // INTERDIT
        // private const val CACHE_KEY_PAIEMENTS = "paiements_list" // INTERDIT
        // private const val CACHE_KEY_INVESTISSEMENTS = "investissements_list" // INTERDIT
        // private const val CACHE_KEY_BUDGETS = "budgets_list" // INTERDIT
        // private const val CACHE_KEY_DEPENSES = "depenses_list" // INTERDIT
        // private const val CACHE_KEY_REVENUS = "revenus_list" // INTERDIT
    }

    /**
     * Sauvegarde des données statiques dans le cache avec timestamp
     * SÉCURITÉ FINANCIÈRE CRITIQUE : Validation multi-niveaux avant mise en cache
     * UNIQUEMENT pour les données qui changent rarement
     * Validation stricte pour éviter le cache des données financières
     */
    fun <T> saveStaticDataToCache(key: String, data: T, context: String = "") {
        // Validation stricte avant mise en cache
        if (!validateCacheAttempt(key, data, context)) {
            println("[CacheService] 🚫 SÉCURITÉ FINANCIÈRE : Cache refusé pour $key")
            return
        }
        
        val cacheEntry = CacheEntry(
            data = gson.toJson(data),
            timestamp = System.currentTimeMillis()
        )
        
        // Mettre en cache en mémoire ET sur disque
        memoryCache[key] = cacheEntry
        prefs.edit()
            .putString(key, gson.toJson(cacheEntry))
            .apply()
        
        println("[CacheService] ✅ Données statiques cachées avec sécurité: $key")
    }

    /**
     * Récupère des données statiques du cache si elles ne sont pas expirées
     * PRIORITÉ AUX MODIFICATIONS : Vérifie si des modifications récentes invalident le cache
     * SÉCURITÉ FINANCIÈRE : Validation avant récupération
     */
    fun <T> getStaticDataFromCache(key: String, type: Class<T>): T? {
        // Vérifier que c'est une clé autorisée pour le cache
        if (!isStaticDataKey(key)) {
            println("[CacheService] ⚠️ Tentative de récupération de cache pour données dynamiques ignorée: $key")
            return null
        }
        
        // Vérifier si des modifications récentes invalident le cache
        if (hasRecentModifications(key)) {
            println("[CacheService] 🔄 Cache invalidé par modifications récentes: $key")
            invalidateCache(key)
            return null
        }
        
        // Essayer d'abord le cache en mémoire (plus rapide)
        val memoryEntry = memoryCache[key]
        if (memoryEntry != null && isCacheValid(memoryEntry.timestamp)) {
            println("[CacheService] ✅ Données récupérées du cache mémoire: $key")
            return gson.fromJson(memoryEntry.data, type)
        }
        
        // Sinon, essayer le cache sur disque
        val cacheJson = prefs.getString(key, null) ?: return null
        
        return try {
            val cacheEntry = gson.fromJson(cacheJson, CacheEntry::class.java)
            
            // Vérifier si le cache n'est pas expiré
            if (isCacheValid(cacheEntry.timestamp)) {
                // Mettre en cache mémoire pour les prochaines fois
                memoryCache[key] = cacheEntry
                println("[CacheService] ✅ Données récupérées du cache disque: $key")
                gson.fromJson(cacheEntry.data, type)
            } else {
                // Supprimer le cache expiré
                invalidateCache(key)
                println("[CacheService] 🗑️ Cache expiré supprimé: $key")
                null
            }
        } catch (e: Exception) {
            // En cas d'erreur, supprimer le cache corrompu
            invalidateCache(key)
            println("[CacheService] ❌ Cache corrompu supprimé: $key")
            null
        }
    }

    /**
     * Vérifie si des modifications récentes invalident le cache
     * PRIORITÉ AUX MODIFICATIONS : Si des modifs récentes, invalider le cache
     */
    private fun hasRecentModifications(key: String): Boolean {
        val lastModification = lastModificationTimestamps[key] ?: return false
        val currentTime = System.currentTimeMillis()
        val timeSinceModification = currentTime - lastModification
        
        // Si modification dans les 5 dernières minutes, invalider le cache
        return timeSinceModification < TimeUnit.MINUTES.toMillis(5)
    }

    /**
     * Enregistre une modification pour invalider automatiquement le cache
     * PRIORITÉ AUX MODIFICATIONS : Cette méthode est appelée lors des modifications
     * SÉCURITÉ FINANCIÈRE : Invalidation en cascade pour éviter les incohérences
     */
    fun notifyModification(entityType: String) {
        val currentTime = System.currentTimeMillis()
        lastModificationTimestamps[entityType] = currentTime
        
        // Invalider les caches liés à cette entité
        when (entityType.lowercase()) {
            "categorie", "categories" -> invalidateCache(CACHE_KEY_CATEGORIES)
            "tiers" -> invalidateCache(CACHE_KEY_TIERS)
            "compte", "comptes" -> invalidateCache(CACHE_KEY_COMPTES)
            "enveloppe", "enveloppes" -> invalidateCache(CACHE_KEY_ENVELOPPES)
            "transaction", "transactions" -> invalidateAllStaticCaches() // Transactions affectent tout
            "allocation", "allocations" -> invalidateAllStaticCaches() // Allocations affectent tout
        }
        
        println("[CacheService] 🔄 Modification détectée pour $entityType - Cache invalidé")
    }

    /**
     * Valide une tentative de cache avec plusieurs niveaux de vérification
     * SÉCURITÉ FINANCIÈRE CRITIQUE : Cette méthode est critique pour éviter les faux soldes
     */
    private fun <T> validateCacheAttempt(key: String, data: T, context: String = ""): Boolean {
        // 1. Vérification basique des clés autorisées
        if (!isStaticDataKey(key)) {
            return false
        }
        
        // 2. Validation via le service de validation (si disponible)
        validationService?.let { validator ->
            if (!validator.validateCacheOperation(key, data, context)) {
                return false
            }
        }
        
        // 3. Validation basique des données
        if (data == null) {
            return false
        }
        
        // 4. Vérifier que les données ne contiennent pas d'informations financières
        val dataString = data.toString().lowercase()
        val financialKeywords = listOf(
            "solde", "montant", "argent", "total", "somme", "balance",
            "transaction", "allocation", "objectif", "rollover", "virement"
        )
        
        val containsFinancialData = financialKeywords.any { keyword ->
            dataString.contains(keyword)
        }
        
        if (containsFinancialData) {
            println("[CacheService] 🚫 SÉCURITÉ FINANCIÈRE : Données financières détectées - Refusé: $key")
            return false
        }
        
        return true
    }

    /**
     * Récupère une liste statique du cache
     * SÉCURITÉ FINANCIÈRE : Validation avant récupération
     */
    fun <T> getStaticListFromCache(key: String, type: Class<T>): List<T>? {
        // Vérifier que c'est une clé autorisée pour le cache
        if (!isStaticDataKey(key)) {
            println("[CacheService] ⚠️ Tentative de récupération de liste du cache pour données dynamiques ignorée: $key")
            return null
        }
        
        // Vérifier si des modifications récentes invalident le cache
        if (hasRecentModifications(key)) {
            println("[CacheService] 🔄 Cache invalidé par modifications récentes: $key")
            invalidateCache(key)
            return null
        }
        
        // Essayer d'abord le cache en mémoire
        val memoryEntry = memoryCache[key]
        if (memoryEntry != null && isCacheValid(memoryEntry.timestamp)) {
            val listType = TypeToken.getParameterized(List::class.java, type).type
            return gson.fromJson(memoryEntry.data, listType) as List<T>
        }
        
        // Sinon, essayer le cache sur disque
        val cacheJson = prefs.getString(key, null) ?: return null
        
        return try {
            val cacheEntry = gson.fromJson(cacheJson, CacheEntry::class.java)
            
            if (isCacheValid(cacheEntry.timestamp)) {
                // Mettre en cache mémoire
                memoryCache[key] = cacheEntry
                val listType = TypeToken.getParameterized(List::class.java, type).type
                gson.fromJson(cacheEntry.data, listType) as List<T>
            } else {
                invalidateCache(key)
                null
            }
        } catch (e: Exception) {
            invalidateCache(key)
            null
        }
    }

    /**
     * Vérifie si une clé correspond à des données statiques autorisées
     * SÉCURITÉ FINANCIÈRE : Détection automatique des données dynamiques par mots-clés
     */
    private fun isStaticDataKey(key: String): Boolean {
        // Liste stricte des clés autorisées
        val allowedKeys = listOf(
            CACHE_KEY_CATEGORIES,
            CACHE_KEY_TIERS,
            CACHE_KEY_COMPTES, // ✅ AJOUTÉ
            CACHE_KEY_ENVELOPPES // ✅ AJOUTÉ
        )
        
        // Vérifier si c'est une clé autorisée
        if (key in allowedKeys) {
            return true
        }
        
        // Détection automatique des données dynamiques par mots-clés
        val dynamicKeywords = listOf(
            "transaction", "transactions",
            "solde", "soldes",
            "montant", "montants",
            "allocation", "allocations",
            "historique", "historiques",
            "objectif", "objectifs",
            "rollover", "rollovers",
            "virement", "virements",
            "pret", "prets",
            "dette", "dettes",
            "paiement", "paiements",
            "investissement", "investissements",
            "budget", "budgets",
            "depense", "depenses",
            "revenu", "revenus",
            "argent", "money",
            "calcul", "calculs",
            "total", "totaux",
            "somme", "sommes",
            "balance", "balances",
            "comptabilite", "comptabilites",
            "financier", "financiers",
            "economique", "economiques"
        )
        
        val keyLower = key.lowercase()
        val containsDynamicKeyword = dynamicKeywords.any { keyword ->
            keyLower.contains(keyword.lowercase())
        }
        
        if (containsDynamicKeyword) {
            println("[CacheService] 🚫 SÉCURITÉ FINANCIÈRE : Détection automatique - Données dynamiques détectées: $key")
            return false
        }
        
        // Par défaut, ne pas autoriser les clés inconnues
        println("[CacheService] ⚠️ Clé inconnue - Refusée par sécurité: $key")
        return false
    }

    /**
     * Invalide le cache pour une clé spécifique (utile après mise à jour)
     * PRIORITÉ AUX MODIFICATIONS : Cette méthode est appelée lors des modifications
     */
    fun invalidateCache(key: String) {
        memoryCache.remove(key)
        prefs.edit().remove(key).apply()
        println("[CacheService] 🔄 Cache invalidé: $key")
    }

    /**
     * Invalide tous les caches statiques (utile après modifications importantes)
     * SÉCURITÉ FINANCIÈRE : Invalidation complète pour éviter les incohérences
     */
    fun invalidateAllStaticCaches() {
        memoryCache.clear()
        prefs.edit()
            .remove(CACHE_KEY_CATEGORIES)
            .remove(CACHE_KEY_TIERS)
            .remove(CACHE_KEY_COMPTES)
            .remove(CACHE_KEY_ENVELOPPES)
            .apply()
        println("[CacheService] 🔄 SÉCURITÉ FINANCIÈRE : Tous les caches statiques invalidés")
    }

    /**
     * Vérifie si le cache est encore valide
     */
    private fun isCacheValid(timestamp: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        val cacheAge = currentTime - timestamp
        return cacheAge < TimeUnit.HOURS.toMillis(CACHE_DURATION_HOURS)
    }

    /**
     * Supprime une entrée du cache
     */
    fun removeFromCache(key: String) {
        memoryCache.remove(key)
        prefs.edit().remove(key).apply()
    }

    /**
     * Vide tout le cache
     */
    fun clearCache() {
        memoryCache.clear()
        lastModificationTimestamps.clear()
        prefs.edit().clear().apply()
        println("[CacheService] 🗑️ Cache complètement vidé")
    }

    /**
     * Nettoie le cache expiré
     */
    suspend fun cleanupExpiredCache() = withContext(Dispatchers.IO) {
        val lastCleanup = prefs.getLong(KEY_LAST_CLEANUP, 0L)
        val currentTime = System.currentTimeMillis()
        
        // Nettoyer seulement une fois par jour
        if (currentTime - lastCleanup > TimeUnit.HOURS.toMillis(CLEANUP_INTERVAL_HOURS)) {
            val allKeys = prefs.all.keys.toList()
            var cleanedCount = 0
            
            for (key in allKeys) {
                if (key != KEY_LAST_CLEANUP) {
                    val cacheJson = prefs.getString(key, null)
                    if (cacheJson != null) {
                        try {
                            val cacheEntry = gson.fromJson(cacheJson, CacheEntry::class.java)
                            if (!isCacheValid(cacheEntry.timestamp)) {
                                removeFromCache(key)
                                cleanedCount++
                            }
                        } catch (e: Exception) {
                            // Supprimer les entrées corrompues
                            removeFromCache(key)
                            cleanedCount++
                        }
                    }
                }
            }
            
            prefs.edit().putLong(KEY_LAST_CLEANUP, currentTime).apply()
            println("[CacheService] 🧹 Nettoyage terminé: $cleanedCount entrées supprimées")
        }
    }

    /**
     * Obtient la taille du cache en nombre d'entrées
     */
    fun getCacheSize(): Int {
        return memoryCache.size + prefs.all.size - 1 // -1 pour exclure KEY_LAST_CLEANUP
    }

    /**
     * Obtient les statistiques du cache
     */
    fun getCacheStats(): CacheStats {
        val allKeys = prefs.all.keys.toList()
        val staticKeys = allKeys.filter { isStaticDataKey(it) }
        val dynamicKeys = allKeys.filter { !isStaticDataKey(it) && it != KEY_LAST_CLEANUP }
        
        return CacheStats(
            totalEntries = memoryCache.size + allKeys.size - 1, // -1 pour exclure KEY_LAST_CLEANUP
            staticEntries = staticKeys.size,
            dynamicEntries = dynamicKeys.size,
            staticKeys = staticKeys,
            dynamicKeys = dynamicKeys,
            memoryCacheSize = memoryCache.size,
            modificationTimestamps = lastModificationTimestamps.size
        )
    }

    data class CacheEntry(
        val data: String,
        val timestamp: Long
    )

    data class CacheStats(
        val totalEntries: Int,
        val staticEntries: Int,
        val dynamicEntries: Int,
        val staticKeys: List<String>,
        val dynamicKeys: List<String>,
        val memoryCacheSize: Int,
        val modificationTimestamps: Int
    )
} 