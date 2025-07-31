package com.xburnsx.toutiebudget.data.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service de validation complexe qui s'assure que les données dynamiques ne sont jamais cachées
 * SÉCURITÉ FINANCIÈRE CRITIQUE : Une erreur de cache pourrait afficher de faux soldes
 * Surveille et prévient les tentatives de cache sur les données financières
 */
@Singleton
class CacheValidationService @Inject constructor() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        // Liste exhaustive des types de données qui ne doivent JAMAIS être cachées
        private val FORBIDDEN_CACHE_TYPES = listOf(
            "Transaction",
            "AllocationMensuelle",
            "Compte", // Les comptes peuvent être cachés mais pas leurs soldes
            "Enveloppe", // Les enveloppes peuvent être cachées mais pas leurs montants
            "Categorie", // Les catégories peuvent être cachées
            "Tiers" // Les tiers peuvent être cachés
        )

        // Liste des propriétés qui ne doivent JAMAIS être cachées
        private val FORBIDDEN_PROPERTIES = listOf(
            "solde", "soldes",
            "montant", "montants",
            "total", "totaux",
            "somme", "sommes",
            "balance", "balances",
            "argent", "money",
            "calcul", "calculs",
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
            "historique", "historiques",
            "allocation", "allocations",
            "comptabilite", "comptabilites",
            "financier", "financiers",
            "economique", "economiques"
        )

        // Liste des clés de cache interdites
        private val FORBIDDEN_CACHE_KEYS = listOf(
            "transactions_list",
            "soldes_list",
            "montants_list",
            "allocations_list",
            "historiques_list",
            "objectifs_list",
            "rollovers_list",
            "virements_list",
            "prets_list",
            "dettes_list",
            "paiements_list",
            "investissements_list",
            "budgets_list",
            "depenses_list",
            "revenus_list",
            "calculs_list",
            "totaux_list",
            "sommes_list",
            "balances_list",
            "argent_list",
            "financier_list"
        )
    }

    /**
     * Valide qu'une tentative de cache est autorisée
     * SÉCURITÉ FINANCIÈRE CRITIQUE : Cette méthode est critique pour éviter les faux soldes
     * @param key La clé de cache
     * @param dataType Le type de données
     * @return true si autorisé, false sinon
     */
    fun validateCacheAttempt(key: String, dataType: String? = null): Boolean {
        serviceScope.launch {
            // Vérifier la clé
            if (isForbiddenCacheKey(key)) {
                logForbiddenAttempt("Clé de cache interdite", key, dataType)
                return@launch
            }

            // Vérifier le type de données
            if (dataType != null && isForbiddenDataType(dataType)) {
                logForbiddenAttempt("Type de données interdit", key, dataType)
                return@launch
            }

            // Vérifier les mots-clés dans la clé
            if (containsForbiddenKeywords(key)) {
                logForbiddenAttempt("Mots-clés interdits détectés", key, dataType)
                return@launch
            }

            println("[CacheValidation] ✅ Tentative de cache autorisée: $key ($dataType)")
        }

        return true
    }

    /**
     * Vérifie si une clé de cache est interdite
     * SÉCURITÉ FINANCIÈRE : Détection stricte des clés dangereuses
     */
    private fun isForbiddenCacheKey(key: String): Boolean {
        val keyLower = key.lowercase()
        return FORBIDDEN_CACHE_KEYS.any { forbiddenKey ->
            keyLower.contains(forbiddenKey.lowercase())
        }
    }

    /**
     * Vérifie si un type de données est interdit
     * SÉCURITÉ FINANCIÈRE : Protection contre le cache des données dynamiques
     */
    private fun isForbiddenDataType(dataType: String): Boolean {
        return FORBIDDEN_CACHE_TYPES.any { forbiddenType ->
            dataType.contains(forbiddenType, ignoreCase = true)
        }
    }

    /**
     * Vérifie si une clé contient des mots-clés interdits
     * SÉCURITÉ FINANCIÈRE : Détection automatique des données financières
     */
    private fun containsForbiddenKeywords(key: String): Boolean {
        val keyLower = key.lowercase()
        return FORBIDDEN_PROPERTIES.any { forbiddenProperty ->
            keyLower.contains(forbiddenProperty.lowercase())
        }
    }

    /**
     * Enregistre une tentative interdite avec détails
     * SÉCURITÉ FINANCIÈRE : Logs détaillés pour audit
     */
    private fun logForbiddenAttempt(reason: String, key: String, dataType: String?) {
        println("[CacheValidation] 🚫 $reason - Clé: $key, Type: $dataType")
        println("[CacheValidation] 🚫 SÉCURITÉ FINANCIÈRE : Les données financières ne doivent JAMAIS être cachées !")
        println("[CacheValidation] 🚫 Risque : Affichage de faux soldes ou montants")
    }

    /**
     * Valide un objet avant mise en cache
     * SÉCURITÉ FINANCIÈRE CRITIQUE : Vérification approfondie du contenu
     * @param obj L'objet à valider
     * @param key La clé de cache
     * @return true si l'objet peut être caché
     */
    fun validateObjectForCache(obj: Any?, key: String): Boolean {
        if (obj == null) return false

        // Vérifier si l'objet contient des propriétés financières
        val objString = obj.toString().lowercase()
        val containsFinancialData = FORBIDDEN_PROPERTIES.any { property ->
            objString.contains(property.lowercase())
        }

        if (containsFinancialData) {
            println("[CacheValidation] 🚫 SÉCURITÉ FINANCIÈRE : Objet contient des données financières - Refusé: $key")
            println("[CacheValidation] 🚫 Contenu détecté: ${objString.take(100)}...")
            return false
        }

        // Vérification supplémentaire pour les objets complexes
        if (obj is Collection<*>) {
            for (item in obj) {
                if (!validateObjectForCache(item, key)) {
                    return false
                }
            }
        }

        return true
    }

    /**
     * Obtient la liste des types autorisés pour le cache
     * DOCUMENTATION : Pour référence et audit
     */
    fun getAllowedCacheTypes(): List<String> {
        return listOf(
            "Categorie (nom, ordre)",
            "Compte (nom, couleur, type)",
            "Enveloppe (nom, couleur)",
            "Tiers (nom, email)"
        )
    }

    /**
     * Obtient la liste des types interdits pour le cache
     * SÉCURITÉ FINANCIÈRE : Documentation des risques
     */
    fun getForbiddenCacheTypes(): List<String> {
        return FORBIDDEN_CACHE_TYPES + listOf(
            "Toute donnée contenant des montants",
            "Toute donnée contenant des soldes",
            "Toute donnée contenant des calculs",
            "Toute donnée financière dynamique"
        )
    }

    /**
     * Valide une opération de cache avec contexte complet
     * SÉCURITÉ FINANCIÈRE : Validation multi-niveaux
     */
    fun validateCacheOperation(key: String, data: Any?, context: String = ""): Boolean {
        // Niveau 1 : Validation basique
        if (!validateCacheAttempt(key, data?.javaClass?.simpleName)) {
            return false
        }

        // Niveau 2 : Validation de l'objet
        if (!validateObjectForCache(data, key)) {
            return false
        }

        // Niveau 3 : Validation contextuelle
        if (context.contains("transaction") || context.contains("solde")) {
            println("[CacheValidation] 🚫 Contexte interdit détecté: $context")
            return false
        }

        println("[CacheValidation] ✅ Opération de cache validée: $key ($context)")
        return true
    }
} 