// chemin/simule: app/src/main/java/com/xburnsx/toutiebudget/domain/services/ArchitectureRules.kt
// Dépendances: Documentation des règles pour éviter que l'IA se trompe

package com.xburnsx.toutiebudget.domain.services

/**
 * 🎯 RÈGLES CLAIRES POUR L'ARCHITECTURE ALLOCATION VS TRANSACTION
 *
 * Ces règles DOIVENT être respectées pour éviter que l'IA fasse n'importe quoi !
 */

// =====================================================
// 📅 ALLOCATION MENSUELLE - Pour le budget du mois
// =====================================================

/**
 * ✅ QUAND CRÉER UNE ALLOCATION MENSUELLE :
 *
 * 1. Placer de l'argent dans une enveloppe
 *    - Exemple: "Placer 100$ dans épicerie"
 *    - Action: Créer NOUVELLE AllocationMensuelle avec solde=+100$
 *
 * 2. Dépenser depuis une enveloppe
 *    - Exemple: "Dépenser 50$ épicerie"
 *    - Action: Créer NOUVELLE AllocationMensuelle avec solde=-50$
 *
 * 3. Virement entre enveloppes du même mois
 *    - Exemple: "Virer 20$ épicerie → essence"
 *    - Action: 2 NOUVELLES AllocationMensuelle (une -20$, une +20$)
 *
 * 4. Rollover automatique début de mois
 *    - Exemple: Passer de juillet à août
 *    - Action: Créer NOUVELLES AllocationMensuelle pour août
 */

/**
 * ❌ CE QU'IL NE FAUT JAMAIS FAIRE AVEC ALLOCATION :
 *
 * - JAMAIS modifier une allocation existante
 * - JAMAIS utiliser mettreAJourAllocation() pour ajouter de l'argent
 * - JAMAIS recupererOuCreerAllocation() pour les virements
 *
 * 🔑 RÈGLE D'OR : TOUJOURS créer une NOUVELLE allocation !
 */

// =====================================================
// 📊 TRANSACTION - Pour l'historique permanent
// =====================================================

/**
 * ✅ QUAND CRÉER UNE TRANSACTION :
 *
 * 1. TOUJOURS en parallèle d'une AllocationMensuelle
 *    - But: Traçabilité et historique
 *
 * 2. Mouvements de comptes
 *    - Exemple: Salaire reçu, virement bancaire
 *    - Action: Transaction pour l'historique du compte
 *
 * 3. Actions utilisateur importantes
 *    - Exemple: "Utilisateur a placé 100$ épicerie depuis compte chèque"
 *    - Action: Transaction avec description claire
 */

/**
 * 🎯 STRUCTURE D'UNE ACTION COMPLÈTE :
 *
 * Exemple: "Placer 50$ dans épicerie depuis compte chèque"
 *
 * Étape 1: AllocationMensuelle
 * - enveloppeId: "epicerie_id"
 * - mois: "2025-07-01 00:00:00" (premier jour du mois)
 * - solde: +50.0 (ajout à l'enveloppe)
 * - compteSourceId: "compte_cheque_id"
 *
 * Étape 2: Transaction (parallèle)
 * - description: "Allocation vers épicerie"
 * - montant: 50.0
 * - compteId: "compte_cheque_id" (historique du compte)
 * - allocationMensuelleId: [ID de l'allocation créée]
 * - date: aujourd'hui
 *
 * Étape 3: Mise à jour compte
 * - Diminuer "pret_a_placer" du compte chèque de 50$
 */

// =====================================================
// 🐛 ERREURS COMMUNES DE L'IA À ÉVITER
// =====================================================

/**
 * ❌ ERREUR 1: Confondre les rôles
 * Mauvais:
 * ```
 * // L'IA crée Transaction au lieu d'AllocationMensuelle
 * transactionRepository.creerTransaction(...)  // ❌ Faux !
 * ```
 *
 * Correct:
 * ```
 * // D'abord AllocationMensuelle pour le budget
 * allocationRepository.creerNouvelleAllocation(...)  // ✅
 * // Puis Transaction pour l'historique
 * transactionRepository.creerTransaction(...)        // ✅
 * ```
 */

/**
 * ❌ ERREUR 2: Modifier au lieu de créer
 * Mauvais:
 * ```
 * // L'IA modifie allocation existante
 * allocationRepository.mettreAJourAllocation(...)  // ❌ Faux !
 * ```
 *
 * Correct:
 * ```
 * // Toujours créer une nouvelle allocation
 * allocationRepository.creerNouvelleAllocation(...)  // ✅
 * ```
 */

/**
 * ❌ ERREUR 3: Oublier le parallélisme
 * Mauvais:
 * ```
 * // L'IA crée seulement AllocationMensuelle
 * creerNouvelleAllocation(...)  // ❌ Incomplet !
 * ```
 *
 * Correct:
 * ```
 * // Les deux sont nécessaires
 * creerNouvelleAllocation(...)  // ✅ Pour le budget
 * creerTransaction(...)         // ✅ Pour l'historique
 * ```
 */

// =====================================================
// 🔧 TEMPLATE POUR L'IA
// =====================================================

/**
 * TEMPLATE À SUIVRE POUR TOUTE ACTION BUDGET :
 *
 * suspend fun actionBudget(parametres...) {
 *   try {
 *     // 1. Valider les données
 *     // ...
 *
 *     // 2. Créer NOUVELLE AllocationMensuelle (jamais modifier !)
 *     val nouvelleAllocation = AllocationMensuelle(
 *       id = "",  // PocketBase génère
 *       enveloppeId = enveloppeId,
 *       mois = premierJourDuMois(Date()),
 *       solde = montantSigne,  // +50$ ou -50$
 *       // ... autres champs
 *     )
 *     val allocationCreee = allocationRepository.creerNouvelleAllocation(nouvelleAllocation)
 *
 *     // 3. Créer Transaction pour historique
 *     val transaction = Transaction(
 *       id = "",  // PocketBase génère
 *       description = "Description claire",
 *       montant = montant,
 *       compteId = compteId,
 *       allocationMensuelleId = allocationCreee.id,
 *       date = Date()
 *     )
 *     transactionRepository.creerTransaction(transaction)
 *
 *     // 4. Mettre à jour soldes comptes si nécessaire
 *     if (impacteCompte) {
 *       compteRepository.mettreAJourPretAPlacerSeulement(compteId, -montant)
 *     }
 *
 *   } catch (e: Exception) {
 *     // Gestion erreur
 *   }
 * }
 */

// =====================================================
// 🎯 POINTS DE CONTRÔLE POUR L'IA
// =====================================================

/**
 * CHECKLIST AVANT CHAQUE ACTION :
 *
 * ✅ Est-ce que je crée une NOUVELLE AllocationMensuelle ?
 * ✅ Est-ce que je crée AUSSI une Transaction ?
 * ✅ Est-ce que j'utilise le bon format de date ?
 * ✅ Est-ce que je ne modifie PAS d'allocation existante ?
 * ✅ Est-ce que les montants ont le bon signe (+/-) ?
 *
 * Si une seule réponse est NON → L'architecture n'est pas respectée !
 */