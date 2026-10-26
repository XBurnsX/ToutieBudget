package com.xburnsx.toutiebudget.domain.services.Impl

import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.domain.services.ArgentService

import com.xburnsx.toutiebudget.domain.usecases.VirementUseCase
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import com.xburnsx.toutiebudget.utils.IdGenerator
import java.util.*
import javax.inject.Inject
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.data.modeles.CompteCredit

/**
 * Implémentation du service ArgentService qui gère les opérations financières.
 */
class ArgentServiceImpl @Inject constructor(
    private val compteRepository: CompteRepository,
    private val transactionRepository: TransactionRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository,
    private val virementUseCase: VirementUseCase,
    private val enveloppeRepository: com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository,
    private val categorieRepository: com.xburnsx.toutiebudget.data.repositories.CategorieRepository,
    private val historiqueAllocationService: com.xburnsx.toutiebudget.domain.services.HistoriqueAllocationService
) : ArgentService {

    /**
     * Alloue un montant d'un compte source vers une enveloppe pour un mois donné.
     * Architecture : Crée TOUJOURS une nouvelle allocation (pas de modification)
     */
    override suspend fun allouerArgentEnveloppe(
        enveloppeId: String,
        compteSourceId: String,
        collectionCompteSource: String,
        montant: Double,
        mois: Date
    ): Result<Unit> = runCatching {
        if (montant <= 0) throw IllegalArgumentException("Le montant de l'allocation doit être positif.")
        
        // 1. Récupérer le compte source
        val compteSource = compteRepository.getCompteById(compteSourceId, collectionCompteSource)
            ?: throw IllegalArgumentException("Compte source non trouvé: $compteSourceId")
        
        // 2. Vérifier que le compte a suffisamment de fonds
        if (compteSource.solde < montant) {
            throw IllegalStateException("Solde insuffisant sur le compte source.")
        }
        
        // 3. RÉCUPÉRER ou créer l'allocation mensuelle (évite les doublons)
        val allocation = allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeId, mois)
        
        // Mettre à jour le montant alloué
        val allocationMiseAJour = allocation.copy(
            alloue = allocation.alloue + montant,
            solde = allocation.solde + montant
        )
        
        allocationMensuelleRepository.mettreAJourAllocation(allocationMiseAJour)

        // 3.5. Récupérer l'enveloppe pour l'historique
        val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrNull()
            ?: throw IllegalArgumentException("Impossible de récupérer les enveloppes")
        val enveloppe = enveloppes.find { it.id == enveloppeId }
            ?: throw IllegalArgumentException("Enveloppe non trouvée: $enveloppeId")
        
        // 3.6. Enregistrer dans l'historique
        if (compteSource is CompteCheque) {
            android.util.Log.d("ToutieBudget", "🔄 ARGENT_SERVICE : Tentative d'enregistrement dans l'historique pour allocation")
            try {
                historiqueAllocationService.enregistrerModificationAllocation(
                    allocationAvant = allocation,
                    allocationApres = allocationMiseAJour,
                    compte = compteSource,
                    enveloppe = enveloppe,
                    montantModification = montant,
                    soldeAvant = compteSource.solde,
                    soldeApres = compteSource.solde - montant,
                    pretAPlacerAvant = compteSource.pretAPlacer,
                    pretAPlacerApres = compteSource.pretAPlacer // Le prêt à placer ne change pas pour une allocation directe
                )
                android.util.Log.d("ToutieBudget", "✅ ARGENT_SERVICE : Enregistrement dans l'historique réussi")
            } catch (e: Exception) {
                android.util.Log.e("ToutieBudget", "❌ ARGENT_SERVICE : Erreur lors de l'enregistrement dans l'historique: ${e.message}")
            }
        } else {
            android.util.Log.d("ToutieBudget", "ℹ️ ARGENT_SERVICE : Compte source n'est pas un CompteCheque, pas d'enregistrement dans l'historique")
        }

        // 4. Mettre à jour le solde du compte source
        val nouveauSolde = compteSource.solde - montant
        compteRepository.mettreAJourSolde(compteSourceId, collectionCompteSource, nouveauSolde)
        
        // 5. Créer une transaction pour cette allocation
        val transaction = Transaction(
            id = IdGenerator.generateId(),
            utilisateurId = "", // À récupérer depuis un UserRepository ou une session
            type = TypeTransaction.Depense,
            montant = montant,
            date = Date(), // Utilise l'heure locale actuelle du téléphone
            compteId = compteSourceId,
            collectionCompte = collectionCompteSource,
            allocationMensuelleId = allocationMiseAJour.id,
            note = "Allocation vers enveloppe #$enveloppeId"
        )
        
        transactionRepository.creerTransaction(transaction)
        
        // 📝 ENREGISTRER DANS L'HISTORIQUE
        try {
            val compte = compteRepository.getCompteById(compteSourceId, collectionCompteSource)
            val enveloppe = enveloppeRepository.recupererToutesLesEnveloppes().getOrNull()?.find { it.id == enveloppeId }
            
            if (compte is CompteCheque && enveloppe != null) {
                android.util.Log.d("ToutieBudget", "🔄 ARGENT_SERVICE : Tentative d'enregistrement dans l'historique pour allocation argent enveloppe")
                historiqueAllocationService.enregistrerCreationAllocation(
                    allocation = allocationMiseAJour,
                    compte = compte,
                    enveloppe = enveloppe,
                    montant = montant,
                    soldeAvant = compte.solde,
                    soldeApres = compte.solde - montant,
                    pretAPlacerAvant = compte.pretAPlacer,
                    pretAPlacerApres = compte.pretAPlacer
                )
                android.util.Log.d("ToutieBudget", "✅ ARGENT_SERVICE : Enregistrement dans l'historique réussi (allocation argent enveloppe)")
            } else {
                android.util.Log.d("ToutieBudget", "ℹ️ ARGENT_SERVICE : Compte ou enveloppe non trouvé, pas d'enregistrement dans l'historique (allocation argent enveloppe)")
            }
        } catch (e: Exception) {
            android.util.Log.e("ToutieBudget", "❌ ARGENT_SERVICE : Erreur lors de l'enregistrement dans l'historique (allocation argent enveloppe): ${e.message}")
        }
    }

    /**
     * Alloue un montant d'un compte source vers une enveloppe pour un mois donné SANS créer de transaction.
     * Utilisé pour les virements internes (prêt à placer vers enveloppe).
     */
    override suspend fun allouerArgentEnveloppeSansTransaction(
        enveloppeId: String,
        compteSourceId: String,
        collectionCompteSource: String,
        montant: Double,
        mois: Date
    ): Result<Unit> = runCatching {
        if (montant <= 0) throw IllegalArgumentException("Le montant de l'allocation doit être positif.")
        
        // 1. Récupérer le compte source
        val compteSource = compteRepository.getCompteById(compteSourceId, collectionCompteSource)
            ?: throw IllegalArgumentException("Compte source non trouvé: $compteSourceId")
        
        // 2. Vérifier que le compte a suffisamment de fonds
        if (compteSource.solde < montant) {
            throw IllegalStateException("Solde insuffisant sur le compte source.")
        }
        
        // 3. RÉCUPÉRER ou créer l'allocation mensuelle (évite les doublons)
        val allocation = allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeId, mois)
        
        // Mettre à jour le montant alloué
        val allocationMiseAJour = allocation.copy(
            alloue = allocation.alloue + montant,
            solde = allocation.solde + montant
        )
        
        allocationMensuelleRepository.mettreAJourAllocation(allocationMiseAJour)

        // 3.5. Récupérer l'enveloppe pour l'historique
        val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrNull()
            ?: throw IllegalArgumentException("Impossible de récupérer les enveloppes")
        val enveloppe = enveloppes.find { it.id == enveloppeId }
            ?: throw IllegalArgumentException("Enveloppe non trouvée: $enveloppeId")
        
        // 3.6. Enregistrer dans l'historique
        if (compteSource is CompteCheque) {
            android.util.Log.d("ToutieBudget", "🔄 ARGENT_SERVICE : Tentative d'enregistrement dans l'historique pour allocation sans transaction")
            try {
                historiqueAllocationService.enregistrerModificationAllocation(
                    allocationAvant = allocation,
                    allocationApres = allocationMiseAJour,
                    compte = compteSource,
                    enveloppe = enveloppe,
                    montantModification = montant,
                    soldeAvant = compteSource.solde,
                    soldeApres = compteSource.solde - montant,
                    pretAPlacerAvant = compteSource.pretAPlacer,
                    pretAPlacerApres = compteSource.pretAPlacer // Le prêt à placer ne change pas pour une allocation directe
                )
                android.util.Log.d("ToutieBudget", "✅ ARGENT_SERVICE : Enregistrement dans l'historique réussi (sans transaction)")
            } catch (e: Exception) {
                android.util.Log.e("ToutieBudget", "❌ ARGENT_SERVICE : Erreur lors de l'enregistrement dans l'historique (sans transaction): ${e.message}")
            }
        } else {
            android.util.Log.d("ToutieBudget", "ℹ️ ARGENT_SERVICE : Compte source n'est pas un CompteCheque, pas d'enregistrement dans l'historique (sans transaction)")
        }

        // 4. Mettre à jour le solde du compte source
        val nouveauSolde = compteSource.solde - montant
        compteRepository.mettreAJourSolde(compteSourceId, collectionCompteSource, nouveauSolde)
        

        
        // 5. PAS DE TRANSACTION - C'est un virement interne !
    }
    
    /**
     * Enregistre une nouvelle transaction (dépense ou revenu) et met à jour les soldes correspondants.
     * Architecture : Si c'est une dépense avec enveloppe, créer une NOUVELLE allocation
     */
    override suspend fun enregistrerTransaction(
        type: String,
        montant: Double,
        date: Date,
        compteId: String,
        collectionCompte: String,
        enveloppeId: String?,
        note: String?
    ): Result<Unit> = runCatching {
        if (montant <= 0) throw IllegalArgumentException("Le montant de la transaction doit être positif.")
        
        // 1. Récupérer le compte
        val compte = compteRepository.getCompteById(compteId, collectionCompte)
            ?: throw IllegalArgumentException("Compte non trouvé: $compteId")
        
        // 2. Convertir le type de transaction (String vers Enum)
        val typeTransaction = try {
            TypeTransaction.valueOf(type)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Type de transaction invalide: $type. Valeurs acceptées: ${TypeTransaction.entries.joinToString()}")
        }
        
        // 3. ✅ GÉRER L'ALLOCATION CORRECTEMENT - PAS DE DOUBLON
        var allocationMensuelleId: String? = null
        if (typeTransaction == TypeTransaction.Depense && !enveloppeId.isNullOrBlank()) {
            // ✅ Récupérer ou créer l'allocation de base pour ce mois
            val calendrier = Calendar.getInstance().apply {
                time = date
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val premierJourMois = calendrier.time

            // ✅ CRÉER une allocation additive pour la dépense
            val nouvelleAllocation = com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle(
                id = "",
                utilisateurId = "",
                enveloppeId = enveloppeId,
                mois = premierJourMois,
                solde = -montant, // Négatif car c'est une dépense
                alloue = 0.0,
                depense = montant,
                compteSourceId = compteId,
                collectionCompteSource = collectionCompte
            )

            val allocationCreee = allocationMensuelleRepository.creerNouvelleAllocation(nouvelleAllocation)
            allocationMensuelleId = allocationCreee.id
        }

        // 4. Calculer le nouveau solde du compte selon le type de transaction
        val nouveauSoldeBrut = when (typeTransaction) {
            TypeTransaction.Depense -> compte.solde - montant
            TypeTransaction.Revenu -> compte.solde + montant
            TypeTransaction.Pret -> compte.solde - montant
            TypeTransaction.RemboursementRecu -> compte.solde + montant
            TypeTransaction.Emprunt -> compte.solde + montant
            TypeTransaction.RemboursementDonne -> compte.solde - montant
            TypeTransaction.Paiement -> compte.solde + montant
            TypeTransaction.PaiementEffectue -> compte.solde - montant
            TypeTransaction.TransfertSortant -> compte.solde - montant
            TypeTransaction.TransfertEntrant -> compte.solde + montant
        }
        
        // 🎯 ARRONDIR AUTOMATIQUEMENT LE NOUVEAU SOLDE
        val nouveauSolde = MoneyFormatter.roundAmount(nouveauSoldeBrut)
        
        // 5. Mettre à jour le solde du compte
        compteRepository.mettreAJourSolde(compteId, collectionCompte, nouveauSolde)
        
        // 6. Créer et enregistrer la transaction
        val transaction = Transaction(
            id = IdGenerator.generateId(),
            utilisateurId = "", // À récupérer depuis un UserRepository ou une session
            type = typeTransaction,
            montant = montant,
            date = date,
            compteId = compteId,
            collectionCompte = collectionCompte,
            allocationMensuelleId = allocationMensuelleId,
            note = note
        )
        
        transactionRepository.creerTransaction(transaction)
        
        // 📝 ENREGISTRER DANS L'HISTORIQUE
        try {
            val compte = compteRepository.getCompteById(compteId, collectionCompte)
            
            if (compte is CompteCheque) {
                android.util.Log.d("ToutieBudget", "🔄 ARGENT_SERVICE : Tentative d'enregistrement dans l'historique pour transaction ${typeTransaction}")
                historiqueAllocationService.enregistrerTransactionDirecte(
                    compte = compte,
                    enveloppe = null, // Pas d'enveloppe pour les transactions directes
                    typeTransaction = typeTransaction.name,
                    montant = when (typeTransaction) {
                        TypeTransaction.Depense, TypeTransaction.Pret, TypeTransaction.RemboursementDonne, TypeTransaction.PaiementEffectue, TypeTransaction.TransfertSortant -> -montant
                        TypeTransaction.Revenu, TypeTransaction.RemboursementRecu, TypeTransaction.Emprunt, TypeTransaction.Paiement, TypeTransaction.TransfertEntrant -> montant
                    },
                    soldeAvant = compte.solde,
                    soldeApres = nouveauSolde,
                    pretAPlacerAvant = compte.pretAPlacer,
                    pretAPlacerApres = compte.pretAPlacer, // Le prêt à placer ne change pas pour les transactions directes
                    note = note
                )
                android.util.Log.d("ToutieBudget", "✅ ARGENT_SERVICE : Enregistrement dans l'historique réussi (transaction ${typeTransaction})")
            } else {
                android.util.Log.d("ToutieBudget", "ℹ️ ARGENT_SERVICE : Compte n'est pas un CompteCheque, pas d'enregistrement dans l'historique (transaction ${typeTransaction})")
            }
        } catch (e: Exception) {
            android.util.Log.e("ToutieBudget", "❌ ARGENT_SERVICE : Erreur lors de l'enregistrement dans l'historique (transaction ${typeTransaction}): ${e.message}")
        }
    }
    
    /**
     * Transfère de l'argent entre deux comptes.
     *
     * @param compteSourceId L'ID du compte source.
     * @param collectionCompteSource Le nom de la collection du compte source (ex: "comptes_cheque").
     * @param compteDestId L'ID du compte destination.
     * @param collectionCompteDest Le nom de la collection du compte destination.
     * @param montant Le montant à transférer.
     * @return Une Result<Unit> indiquant le succès ou l'échec de l'opération.
     */
    override suspend fun transfererArgentEntreComptes(
        compteSourceId: String,
        collectionCompteSource: String,
        compteDestId: String,
        collectionCompteDest: String,
        montant: Double
    ): Result<Unit> = runCatching {
        if (montant <= 0) throw IllegalArgumentException("Le montant du transfert doit être positif.")
        
        // 1. Récupérer les comptes source et destination
        val compteSource = compteRepository.getCompteById(compteSourceId, collectionCompteSource)
            ?: throw IllegalArgumentException("Compte source non trouvé: $compteSourceId")
            
        val compteDest = compteRepository.getCompteById(compteDestId, collectionCompteDest)
            ?: throw IllegalArgumentException("Compte destination non trouvé: $compteDestId")
        
        // 2. Vérifier que le compte source a suffisamment de fonds
        if (compteSource.solde < montant) {
            throw IllegalStateException("Solde insuffisant sur le compte source.")
        }
        
        // 3. Mettre à jour les soldes des deux comptes
        val nouveauSoldeSourceBrut = compteSource.solde - montant
        val nouveauSoldeDestBrut = compteDest.solde + montant
        
        // 🎯 ARRONDIR AUTOMATIQUEMENT LES NOUVEAUX SOLDES
        val nouveauSoldeSource = MoneyFormatter.roundAmount(nouveauSoldeSourceBrut)
        val nouveauSoldeDest = MoneyFormatter.roundAmount(nouveauSoldeDestBrut)
        
        compteRepository.mettreAJourSolde(compteSourceId, collectionCompteSource, nouveauSoldeSource)
        compteRepository.mettreAJourSolde(compteDestId, collectionCompteDest, nouveauSoldeDest)
        
        // 4. Créer les transactions correspondantes
        val transactionSource = Transaction(
            id = IdGenerator.generateId(),
            utilisateurId = "", // À récupérer depuis un UserRepository ou une session
            type = TypeTransaction.Pret,
            montant = montant,
            date = Date(), // Utilise l'heure locale actuelle du téléphone
            compteId = compteSourceId,
            collectionCompte = collectionCompteSource,
            allocationMensuelleId = null,
            note = "Transfert vers compte #$compteDestId"
        )
        
        val transactionDest = Transaction(
            id = IdGenerator.generateId(),
            utilisateurId = "", // À récupérer depuis un UserRepository ou une session
            type = TypeTransaction.Emprunt,
            montant = montant,
            date = Date(), // Utilise l'heure locale actuelle du téléphone
            compteId = compteDestId,
            collectionCompte = collectionCompteDest,
            allocationMensuelleId = null,
            note = "Transfert depuis compte #$compteSourceId"
        )
        
        transactionRepository.creerTransaction(transactionSource)
        transactionRepository.creerTransaction(transactionDest)
    }

    override suspend fun effectuerVirementCompteVersCompte(
        compteSource: com.xburnsx.toutiebudget.data.modeles.Compte,
        compteDestination: com.xburnsx.toutiebudget.data.modeles.Compte,
        montant: Double
    ): Result<Unit> = runCatching {
        if (montant <= 0) throw IllegalArgumentException("Le montant du virement doit être positif.")
        
        if (compteSource.solde < montant) {
            throw IllegalStateException("Solde insuffisant sur le compte source.")
        }
        
        // Mettre à jour les soldes
        val nouveauSoldeSource = compteSource.solde - montant
        val nouveauSoldeDest = compteDestination.solde + montant
        
        compteRepository.mettreAJourSolde(compteSource.id, compteSource.collection, nouveauSoldeSource)
        compteRepository.mettreAJourSolde(compteDestination.id, compteDestination.collection, nouveauSoldeDest)
        
        // Créer les transactions
        val transactionSource = Transaction(
            id = IdGenerator.generateId(),
            utilisateurId = compteSource.utilisateurId,
            type = TypeTransaction.Pret,
            montant = montant,
            date = Date(), // Utilise l'heure locale actuelle du téléphone
            compteId = compteSource.id,
            collectionCompte = compteSource.collection,
            allocationMensuelleId = null,
            note = "Virement vers ${compteDestination.nom}"
        )
        
        val transactionDest = Transaction(
            id = IdGenerator.generateId(),
            utilisateurId = compteDestination.utilisateurId,
            type = TypeTransaction.Emprunt,
            montant = montant,
            date = Date(), // Utilise l'heure locale actuelle du téléphone
            compteId = compteDestination.id,
            collectionCompte = compteDestination.collection,
            allocationMensuelleId = null,
            note = "Virement depuis ${compteSource.nom}"
        )
        
        transactionRepository.creerTransaction(transactionSource)
        transactionRepository.creerTransaction(transactionDest)
    }

    /**
     * Effectue un virement d'un compte vers une enveloppe.
     * Architecture : Crée une NOUVELLE allocation (pas de récupération/modification)
     */
    override suspend fun effectuerVirementCompteVersEnveloppe(
        compte: com.xburnsx.toutiebudget.data.modeles.Compte,
        enveloppe: com.xburnsx.toutiebudget.data.modeles.Enveloppe,
        montant: Double
    ): Result<Unit> = runCatching {
        if (montant <= 0) throw IllegalArgumentException("Le montant du virement doit être positif.")
        
        if (compte.solde < montant) {
            throw IllegalStateException("Solde insuffisant sur le compte source.")
        }
        
        // Mettre à jour le solde du compte
        val nouveauSoldeCompteBrut = compte.solde - montant
        // 🎯 ARRONDIR AUTOMATIQUEMENT LE NOUVEAU SOLDE
        val nouveauSoldeCompte = MoneyFormatter.roundAmount(nouveauSoldeCompteBrut)
        compteRepository.mettreAJourSolde(compte.id, compte.collection, nouveauSoldeCompte)
        
        // ✅ UTILISER recupererOuCreerAllocation + addition automatique pour éviter les doublons
        val calendrier = Calendar.getInstance().apply {
            time = Date()
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val premierJourMois = calendrier.time

        // ✅ Obtenir/créer l'allocation de base

        // ✅ CRÉER une allocation additive pour le virement
        val nouvelleAllocation = com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle(
            id = "",
            utilisateurId = compte.utilisateurId,
            enveloppeId = enveloppe.id,
            mois = premierJourMois,
            solde = montant,
            alloue = montant,
            depense = 0.0,
            compteSourceId = compte.id,
            collectionCompteSource = compte.collection
        )

        val allocationCreee = allocationMensuelleRepository.creerNouvelleAllocation(nouvelleAllocation)

        // 4.5. Enregistrer dans l'historique
        if (compte is CompteCheque) {
            historiqueAllocationService.enregistrerCreationAllocation(
                allocation = allocationCreee,
                compte = compte,
                enveloppe = enveloppe,
                montant = montant,
                soldeAvant = compte.solde,
                soldeApres = nouveauSoldeCompte,
                pretAPlacerAvant = compte.pretAPlacer,
                pretAPlacerApres = compte.pretAPlacer // Le prêt à placer ne change pas pour un virement direct
            )
        }

        // Créer la transaction
        val transaction = Transaction(
            id = IdGenerator.generateId(),
            utilisateurId = compte.utilisateurId,
            type = TypeTransaction.Depense,
            montant = montant,
            date = Date(), // Utilise l'heure locale actuelle du téléphone
            compteId = compte.id,
            collectionCompte = compte.collection,
            allocationMensuelleId = allocationCreee.id,
            note = "Virement vers enveloppe ${enveloppe.nom}"
        )
        
        transactionRepository.creerTransaction(transaction)

        // 🔥 FUSION AUTOMATIQUE : Forcer la fusion des allocations après le virement
        try {
            allocationMensuelleRepository.recupererOuCreerAllocation(enveloppe.id, premierJourMois)
        } catch (e: Exception) {
            // Erreur silencieuse de fusion - ne pas faire échouer le virement
            // ⚠️ Erreur lors de la fusion des allocations après virement compte->enveloppe: ${e.message}
        }
    }

    override suspend fun effectuerVirementEnveloppeVersCompte(
        enveloppe: com.xburnsx.toutiebudget.data.modeles.Enveloppe,
        compte: com.xburnsx.toutiebudget.data.modeles.Compte,
        montant: Double
    ): Result<Unit> = runCatching {
        if (montant <= 0) throw IllegalArgumentException("Le montant du virement doit être positif.")
        
        // Récupérer l'allocation mensuelle de l'enveloppe
        val allocation = allocationMensuelleRepository.getAllocationById(enveloppe.id)
            ?: throw IllegalArgumentException("Aucune allocation trouvée pour l'enveloppe ${enveloppe.nom}")
        
        if (allocation.solde < montant) {
            throw IllegalStateException("Solde insuffisant dans l'enveloppe ${enveloppe.nom}.")
        }
        
        // Mettre à jour le solde du compte
        val nouveauSoldeCompteBrut = compte.solde + montant
        // 🎯 ARRONDIR AUTOMATIQUEMENT LE NOUVEAU SOLDE
        val nouveauSoldeCompte = MoneyFormatter.roundAmount(nouveauSoldeCompteBrut)
        compteRepository.mettreAJourSolde(compte.id, compte.collection, nouveauSoldeCompte)
        
        // ✅ UTILISER recupererOuCreerAllocation + allocation négative pour éviter les doublons
        val calendrier = Calendar.getInstance().apply {
            time = Date()
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val premierJourMois = calendrier.time

        // ✅ Obtenir/créer l'allocation de base pour l'enveloppe
                    val allocationExistante = allocationMensuelleRepository.recupererOuCreerAllocation(enveloppe.id, premierJourMois)

        // ✅ CRÉER une allocation négative pour le virement vers prêt à placer (addition automatique)
        val allocationVirement = com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle(
            id = "",
            utilisateurId = allocationExistante.utilisateurId,
            enveloppeId = allocationExistante.enveloppeId,
            mois = premierJourMois,
            solde = -montant,        // Négatif pour sortir l'argent
            alloue = -montant,       // Alloué négatif pour virement sortant
            depense = 0.0,           // Pas une dépense, c'est un virement !
            compteSourceId = allocationExistante.compteSourceId,
            collectionCompteSource = allocationExistante.collectionCompteSource
        )
        allocationMensuelleRepository.creerNouvelleAllocation(allocationVirement)
        
        // Créer la transaction
        val transaction = Transaction(
            id = IdGenerator.generateId(),
            utilisateurId = compte.utilisateurId,
            type = TypeTransaction.Revenu,
            montant = montant,
            date = Date(), // Utilise l'heure locale actuelle du téléphone
            compteId = compte.id,
            collectionCompte = compte.collection,
            allocationMensuelleId = allocation.id,
            note = "Virement depuis enveloppe ${enveloppe.nom}"
        )
        
        transactionRepository.creerTransaction(transaction)
        
        // 📝 ENREGISTRER DANS L'HISTORIQUE
        if (compte is CompteCheque) {
            android.util.Log.d("ToutieBudget", "🔄 ARGENT_SERVICE : Tentative d'enregistrement dans l'historique pour virement enveloppe vers compte")
            try {
                historiqueAllocationService.enregistrerModificationAllocation(
                    allocationAvant = allocationExistante,
                    allocationApres = allocationExistante.copy(solde = allocationExistante.solde - montant),
                    compte = compte,
                    enveloppe = enveloppe,
                    montantModification = -montant, // Négatif car on retire de l'enveloppe
                    soldeAvant = compte.solde,
                    soldeApres = nouveauSoldeCompte, // Le solde du compte augmente
                    pretAPlacerAvant = compte.pretAPlacer,
                    pretAPlacerApres = compte.pretAPlacer // Le prêt à placer ne change pas
                )
                android.util.Log.d("ToutieBudget", "✅ ARGENT_SERVICE : Enregistrement dans l'historique réussi (enveloppe vers compte)")
            } catch (e: Exception) {
                android.util.Log.e("ToutieBudget", "❌ ARGENT_SERVICE : Erreur lors de l'enregistrement dans l'historique (enveloppe vers compte): ${e.message}")
            }
        } else {
            android.util.Log.d("ToutieBudget", "ℹ️ ARGENT_SERVICE : Compte destination n'est pas un CompteCheque, pas d'enregistrement dans l'historique (enveloppe vers compte)")
        }
    }

    override suspend fun effectuerVirementEnveloppeVersCompteSansTransaction(
        enveloppe: com.xburnsx.toutiebudget.data.modeles.Enveloppe,
        compte: com.xburnsx.toutiebudget.data.modeles.Compte,
        montant: Double
    ): Result<Unit> = runCatching {
        android.util.Log.d("ToutieBudget", "🔄 ARGENT_SERVICE : Début effectuerVirementEnveloppeVersCompteSansTransaction - ${enveloppe.nom} vers ${compte.nom} - ${montant}$")
        
        if (montant <= 0) throw IllegalArgumentException("Le montant du virement doit être positif.")
        
        // Récupérer l'allocation mensuelle de l'enveloppe
        val allocation = allocationMensuelleRepository.getAllocationById(enveloppe.id)
            ?: throw IllegalArgumentException("Aucune allocation trouvée pour l'enveloppe ${enveloppe.nom}")
        
        if (allocation.solde < montant) {
            throw IllegalStateException("Solde insuffisant dans l'enveloppe ${enveloppe.nom}.")
        }
        
        // Mettre à jour le solde du compte
        val nouveauSoldeCompteBrut = compte.solde + montant
        // 🎯 ARRONDIR AUTOMATIQUEMENT LE NOUVEAU SOLDE
        val nouveauSoldeCompte = MoneyFormatter.roundAmount(nouveauSoldeCompteBrut)
        compteRepository.mettreAJourSolde(compte.id, compte.collection, nouveauSoldeCompte)
        
        // ✅ UTILISER recupererOuCreerAllocation + allocation négative pour éviter les doublons
        val calendrier = Calendar.getInstance().apply {
            time = Date()
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val premierJourMois = calendrier.time

        // ✅ Obtenir/créer l'allocation de base pour l'enveloppe
        val allocationExistante = allocationMensuelleRepository.recupererOuCreerAllocation(enveloppe.id, premierJourMois)

        // ✅ CRÉER une allocation négative pour le virement vers prêt à placer (addition automatique)
        val allocationVirement = com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle(
            id = "",
            utilisateurId = allocationExistante.utilisateurId,
            enveloppeId = allocationExistante.enveloppeId,
            mois = premierJourMois,
            solde = -montant,        // Négatif pour sortir l'argent
            alloue = -montant,       // Alloué négatif pour virement sortant
            depense = 0.0,           // Pas une dépense, c'est un virement !
            compteSourceId = allocationExistante.compteSourceId,
            collectionCompteSource = allocationExistante.collectionCompteSource
        )
        allocationMensuelleRepository.creerNouvelleAllocation(allocationVirement)
        
        // 🔥 FUSION AUTOMATIQUE : Forcer la fusion des allocations après le virement
        try {
            allocationMensuelleRepository.recupererOuCreerAllocation(enveloppe.id, premierJourMois)
        } catch (e: Exception) {
            // Erreur silencieuse de fusion - ne pas faire échouer le virement
            // ⚠️ Erreur lors de la fusion des allocations après virement enveloppe->compte: ${e.message}
        }
        
        android.util.Log.d("ToutieBudget", "🔄 ARGENT_SERVICE : Arrivé à la section historique - compte type: ${compte::class.simpleName}")
        
        // 📝 ENREGISTRER DANS L'HISTORIQUE
        if (compte is CompteCheque) {
            android.util.Log.d("ToutieBudget", "🔄 ARGENT_SERVICE : Tentative d'enregistrement dans l'historique pour virement enveloppe vers prêt à placer")
            try {
                historiqueAllocationService.enregistrerModificationAllocation(
                    allocationAvant = allocationExistante,
                    allocationApres = allocationExistante.copy(solde = allocationExistante.solde - montant),
                    compte = compte,
                    enveloppe = enveloppe,
                    montantModification = -montant, // Négatif car on retire de l'enveloppe
                    soldeAvant = compte.solde, // Solde AVANT la modification
                    soldeApres = compte.solde, // Le solde du compte ne change pas pour un virement enveloppe vers prêt à placer
                    pretAPlacerAvant = compte.pretAPlacer,
                    pretAPlacerApres = compte.pretAPlacer + montant // Le prêt à placer augmente
                )
                android.util.Log.d("ToutieBudget", "✅ ARGENT_SERVICE : Enregistrement dans l'historique réussi (enveloppe vers prêt à placer)")
            } catch (e: Exception) {
                android.util.Log.e("ToutieBudget", "❌ ARGENT_SERVICE : Erreur lors de l'enregistrement dans l'historique (enveloppe vers prêt à placer): ${e.message}")
            }
        } else {
            android.util.Log.d("ToutieBudget", "ℹ️ ARGENT_SERVICE : Compte destination n'est pas un CompteCheque, pas d'enregistrement dans l'historique (enveloppe vers prêt à placer)")
        }
        
        // PAS DE TRANSACTION - C'est un virement interne !
    }

    /**
     * Effectue un virement d'enveloppe vers enveloppe.
     * Architecture : Crée une NOUVELLE allocation pour la destination (pas de récupération/modification)
     */
    override suspend fun effectuerVirementEnveloppeVersEnveloppe(
        enveloppeSource: com.xburnsx.toutiebudget.data.modeles.Enveloppe,
        enveloppeDestination: com.xburnsx.toutiebudget.data.modeles.Enveloppe,
        montant: Double
    ): Result<Unit> = runCatching {
        if (montant <= 0) throw IllegalArgumentException("Le montant du virement doit être positif.")
        
        // Récupérer l'allocation source (doit exister)
        val allocationSource = allocationMensuelleRepository.getAllocationById(enveloppeSource.id)
            ?: throw IllegalArgumentException("Aucune allocation trouvée pour l'enveloppe source ${enveloppeSource.nom}")
        
        if (allocationSource.solde < montant) {
            throw IllegalStateException("Solde insuffisant dans l'enveloppe source ${enveloppeSource.nom}.")
        }
        
        // ✅ UTILISER recupererOuCreerAllocation pour éviter les doublons
        val calendrier = Calendar.getInstance().apply {
            time = Date()
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val premierJourMois = calendrier.time

        // ✅ Obtenir/créer allocations pour les deux enveloppes

        // ✅ CRÉER des allocations additionnelles pour les virements (addition automatique)
        val allocationVirementDest = com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle(
            id = "",
            utilisateurId = enveloppeDestination.utilisateurId,
            enveloppeId = enveloppeDestination.id,
            mois = premierJourMois,
            solde = montant,
            alloue = montant,
            depense = 0.0,
            compteSourceId = null,
            collectionCompteSource = null
        )

        val allocationDestCreee = allocationMensuelleRepository.creerNouvelleAllocation(allocationVirementDest)

        // ✅ CRÉER allocation négative pour la source (addition automatique)
        val allocationVirementSource = com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle(
            id = "",
            utilisateurId = enveloppeSource.utilisateurId,
            enveloppeId = enveloppeSource.id,
            mois = premierJourMois,
            solde = -montant,
            alloue = -montant,  // Alloué négatif pour virement sortant
            depense = 0.0,      // Pas une dépense, c'est un virement !
            compteSourceId = null,
            collectionCompteSource = null
        )

        val allocationSourceCreee = allocationMensuelleRepository.creerNouvelleAllocation(allocationVirementSource)

        // ❌ NE PAS créer de transactions pour les virements entre enveloppes
        // Les virements entre enveloppes ne doivent PAS affecter les soldes des comptes
        // Les transactions ne sont créées que pour tracer les mouvements entre comptes,
        // pas entre enveloppes (qui sont des allocations internes)

        // 🔥 FUSION AUTOMATIQUE : Forcer la fusion des allocations après le virement
        try {
            // Fusionner les allocations de l'enveloppe source
            allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeSource.id, premierJourMois)
            
            // Fusionner les allocations de l'enveloppe destination
            allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeDestination.id, premierJourMois)
        } catch (e: Exception) {
            // Erreur silencieuse de fusion - ne pas faire échouer le virement
            // ⚠️ Erreur lors de la fusion des allocations après virement enveloppe->enveloppe: ${e.message}
        }
    }

    override suspend fun effectuerVirementPretAPlacerVersEnveloppe(
        compteId: String,
        enveloppeId: String,
        montant: Double
    ): Result<Unit> {
        return virementUseCase.effectuerVirementPretAPlacerVersEnveloppe(compteId, enveloppeId, montant)
    }

    override suspend fun effectuerVirementEnveloppeVersPretAPlacer(
        enveloppeId: String,
        compteId: String,
        montant: Double
    ): Result<Unit> {
        return virementUseCase.effectuerVirementEnveloppeVersPretAPlacer(enveloppeId, compteId, montant)
    }

    /**
     * Effectue un virement entre deux comptes, en créant une transaction pour chaque.
     * Met à jour les soldes et crée des transactions de type Transfert.
     */
    override suspend fun effectuerVirementEntreComptes(
        compteSourceId: String,
        compteDestId: String,
        montant: Double,
        nomCompteSource: String,
        nomCompteDest: String
    ): Result<Unit> = runCatching {
        
        if (montant <= 0) throw IllegalArgumentException("Le montant du virement doit être positif.")

        // 1. Récupérer les comptes et leurs collections
        val compteSourceResult = compteRepository.recupererCompteParIdToutesCollections(compteSourceId)
        if (compteSourceResult.isFailure) {
            throw compteSourceResult.exceptionOrNull() ?: Exception("Erreur récupération compte source")
        }
        val compteSource = compteSourceResult.getOrThrow()

        val compteDestResult = compteRepository.recupererCompteParIdToutesCollections(compteDestId)
        if (compteDestResult.isFailure) {
            throw compteDestResult.exceptionOrNull() ?: Exception("Erreur récupération compte destination")
        }
        val compteDest = compteDestResult.getOrThrow()

        // 2. Vérifier le solde du compte source
        if (compteSource.solde < montant) {
            throw IllegalStateException("Solde insuffisant sur le compte '$nomCompteSource'.")
        }

        // 3. Mettre à jour les soldes
        val nouveauSoldeSource = compteSource.solde - montant
        try {
            compteRepository.mettreAJourSolde(compteSource.id, compteSource.collection, nouveauSoldeSource)
        } catch (e: Exception) {
            throw Exception("Erreur mise à jour solde source: ${e.message}")
        }

        val nouveauSoldeDest = compteDest.solde + montant
        try {
            compteRepository.mettreAJourSolde(compteDest.id, compteDest.collection, nouveauSoldeDest)
        } catch (e: Exception) {
            throw Exception("Erreur mise à jour solde destination: ${e.message}")
        }

        // 4. Gérer le "Prêt à placer" pour les comptes chèque
        // Mise à jour du prêt à placer du compte source (diminution)
        if (compteSource is
                    CompteCheque) {
            val variationPretAPlacerSource = -montant // Variation négative pour retirer le montant
            try {
                compteRepository.mettreAJourPretAPlacerSeulement(compteSource.id, variationPretAPlacerSource).getOrThrow()
            } catch (e: Exception) {
                throw Exception("Erreur mise à jour prêt à placer du compte source: ${e.message}")
            }
        }

        // Mise à jour du prêt à placer du compte destination (augmentation)
        if (compteDest is CompteCheque) {
            val variationPretAPlacerDest = montant // Variation positive pour ajouter le montant
            try {
                val result = compteRepository.mettreAJourPretAPlacerSeulement(compteDest.id, variationPretAPlacerDest)
                if (result.isSuccess) {
                } else {
                    throw result.exceptionOrNull() ?: Exception("Erreur inconnue")
                }
            } catch (e: Exception) {
                throw Exception("Erreur mise à jour prêt à placer du compte destination: ${e.message}")
            }
        } else {
        }

        // 5. Créer la transaction de sortie
        val transactionSortante = Transaction(
            id = IdGenerator.generateId(),
            utilisateurId = compteSource.utilisateurId,
            type = TypeTransaction.TransfertSortant, // ✅ Utiliser TransfertSortant pour les virements
            montant = montant,
            date = Date(), // Utilise l'heure locale actuelle du téléphone
            compteId = compteSource.id,
            collectionCompte = compteSource.collection,
            allocationMensuelleId = null,
            tiersUtiliser = nomCompteDest, // ✅ NOM du compte qui reçoit l'argent
            note = null // PAS de note !
        )
        try {
            transactionRepository.creerTransaction(transactionSortante).getOrThrow()
        } catch (e: Exception) {
            throw Exception("Erreur création transaction sortante: ${e.message}")
        }

        // 6. Créer la transaction d'entrée
        val transactionEntrante = Transaction(
            id = IdGenerator.generateId(),
            utilisateurId = compteDest.utilisateurId,
            type = TypeTransaction.TransfertEntrant, // ✅ Utiliser TransfertEntrant pour les virements
            montant = montant,
            date = Date(), // Utilise l'heure locale actuelle du téléphone
            compteId = compteDest.id,
            collectionCompte = compteDest.collection,
            allocationMensuelleId = null,
            tiersUtiliser = nomCompteSource, // ✅ NOM du compte qui envoie l'argent
            note = null // PAS de note !
        )
        try {
            transactionRepository.creerTransaction(transactionEntrante).getOrThrow()
        } catch (e: Exception) {
            throw Exception("Erreur création transaction entrante: ${e.message}")
        }
    }

    /**
     * Paiement d'une carte de crédit ou d'une dette (CC2).
     *
     * - Une seule sortie sur le compte payeur et une seule entrée « Paiement » sur la carte/dette.
     * - Le « Prêt à placer » n'est pas touché : l'argent était déjà placé dans l'enveloppe de la
     *   carte/dette, dont le solde du mois diminue du montant payé (provenance respectée).
     * - Les frais de carte ne remboursent JAMAIS une autre dette, même s'ils portent le même nom.
     * - Tout est validé avant d'écrire ; si l'écriture sur la carte échoue, le compte payeur est remis.
     */
    override suspend fun effectuerPaiementCarteOuDette(
        compteQuiPaieId: String,
        collectionCompteQuiPaie: String,
        carteOuDetteId: String,
        collectionCarteOuDette: String,
        montant: Double,
        note: String?
    ): Result<Unit> = runCatching {
        if (montant <= 0) throw IllegalArgumentException("Le montant du paiement doit être positif.")
        val montantArrondi = MoneyFormatter.roundAmount(montant)

        // 1. Comptes
        val compteQuiPaie = compteRepository.getCompteById(compteQuiPaieId, collectionCompteQuiPaie)
            ?: throw IllegalArgumentException("Compte qui paie non trouvé: $compteQuiPaieId")
        val carteOuDette = compteRepository.getCompteById(carteOuDetteId, collectionCarteOuDette)
            ?: throw IllegalArgumentException("Carte/Dette non trouvée: $carteOuDetteId")
        val estDette = collectionCarteOuDette == "comptes_dettes"
        val collectionCible = if (estDette) "comptes_dettes" else "comptes_credits"

        if (compteQuiPaie.solde < montantArrondi) {
            throw IllegalStateException("Solde insuffisant sur le compte qui paie.")
        }

        // 2. Enveloppe de la carte/dette : même nom, dans sa catégorie (« Cartes de crédit » / « Dettes »)
        val mois = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val nomCategorie = if (estDette) "Dettes" else "Cartes de crédit"
        val categorie = categorieRepository.recupererToutesLesCategories().getOrElse { emptyList() }
            .firstOrNull { it.nom.equals(nomCategorie, ignoreCase = true) }
        val enveloppeCible = enveloppeRepository.recupererToutesLesEnveloppes().getOrElse { emptyList() }
            .filter { it.nom.equals(carteOuDette.nom, ignoreCase = true) && !it.estArchive }
            .let { candidates -> candidates.firstOrNull { categorie != null && it.categorieId == categorie.id } ?: candidates.singleOrNull() }
        val allocationAvant = enveloppeCible?.let { allocationMensuelleRepository.recupererOuCreerAllocation(it.id, mois) }
        if (allocationAvant != null && allocationAvant.solde > 0.01 &&
            allocationAvant.compteSourceId != null && allocationAvant.compteSourceId != compteQuiPaieId
        ) {
            val nomProvenance = compteRepository.recupererCompteParIdToutesCollections(allocationAvant.compteSourceId).getOrNull()?.nom ?: "un autre compte"
            throw IllegalStateException(
                "L'argent de l'enveloppe ${enveloppeCible.nom} vient de $nomProvenance : payez depuis ce compte."
            )
        }

        // 3. Compte payeur (le Prêt à placer ne bouge pas)
        if (compteQuiPaie is CompteCheque) {
            compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
                compteId = compteQuiPaieId,
                collectionCompte = collectionCompteQuiPaie,
                variationSolde = -montantArrondi,
                mettreAJourPretAPlacer = false
            ).getOrThrow()
        } else {
            compteRepository.mettreAJourSolde(
                compteQuiPaieId, collectionCompteQuiPaie, MoneyFormatter.roundAmount(compteQuiPaie.solde - montantArrondi)
            )
        }

        // 4. Carte/dette : le paiement rapproche le solde de zéro
        val nouveauSoldeCible = MoneyFormatter.roundAmount(carteOuDette.solde + montantArrondi)
        try {
            compteRepository.mettreAJourSolde(carteOuDetteId, collectionCible, nouveauSoldeCible)
        } catch (e: Exception) {
            // Remettre le compte payeur comme avant
            if (compteQuiPaie is CompteCheque) {
                compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(compteQuiPaieId, collectionCompteQuiPaie, montantArrondi, false)
            } else {
                compteRepository.mettreAJourSolde(compteQuiPaieId, collectionCompteQuiPaie, compteQuiPaie.solde)
            }
            throw e
        }

        // 5. Enveloppe : l'argent réservé pour ce paiement est utilisé
        val allocationApres = allocationAvant?.copy(
            depense = MoneyFormatter.roundAmount(allocationAvant.depense + montantArrondi),
            solde = MoneyFormatter.roundAmount(allocationAvant.solde - montantArrondi)
        )
        if (allocationApres != null) {
            allocationMensuelleRepository.mettreAJourAllocation(allocationApres)
        }

        // 6. Transactions : une sortie, une entrée « Paiement » (jamais « Emprunt », qui est un prêt personnel)
        transactionRepository.creerTransaction(
            Transaction(
                id = IdGenerator.generateId(),
                utilisateurId = compteQuiPaie.utilisateurId,
                type = TypeTransaction.PaiementEffectue,
                montant = montantArrondi,
                date = Date(),
                compteId = compteQuiPaieId,
                collectionCompte = collectionCompteQuiPaie,
                allocationMensuelleId = allocationApres?.id,
                tiersUtiliser = note ?: carteOuDette.nom,
                note = null
            )
        ).getOrThrow()
        transactionRepository.creerTransaction(
            Transaction(
                id = IdGenerator.generateId(),
                utilisateurId = carteOuDette.utilisateurId,
                type = TypeTransaction.Paiement,
                montant = montantArrondi,
                date = Date(),
                compteId = carteOuDetteId,
                collectionCompte = collectionCible,
                allocationMensuelleId = null,
                tiersUtiliser = note ?: carteOuDette.nom,
                note = null
            )
        ).getOrThrow()

        // 7. Historique du compte payeur
        try {
            val compteApres = compteRepository.getCompteById(compteQuiPaieId, collectionCompteQuiPaie) as? CompteCheque
            if (compteApres != null) {
                historiqueAllocationService.enregistrerTransactionDirecte(
                    compte = compteApres,
                    enveloppe = enveloppeCible,
                    typeTransaction = "PAIEMENT_EFFECTUE",
                    montant = -montantArrondi,
                    soldeAvant = compteApres.solde + montantArrondi,
                    soldeApres = compteApres.solde,
                    pretAPlacerAvant = compteApres.pretAPlacer,
                    pretAPlacerApres = compteApres.pretAPlacer,
                    note = "Paiement vers ${note ?: carteOuDette.nom}"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("ToutieBudget", "Historique du paiement non enregistré: ${e.message}")
        }

        // 8. Dette soldée : compteur de paiements, archivage ; son enveloppe est archivée si elle est vide
        //    (jamais supprimée, et la catégorie Dettes reste en place)
        if (estDette) {
            val detteActuelle = compteRepository.getCompteById(carteOuDetteId, collectionCible) as? CompteDette
            if (detteActuelle != null) {
                val estSoldee = kotlin.math.abs(nouveauSoldeCible) < 0.01
                compteRepository.mettreAJourCompte(
                    detteActuelle.copy(
                        paiementEffectue = detteActuelle.paiementEffectue + 1,
                        estArchive = detteActuelle.estArchive || estSoldee
                    )
                )
                if (estSoldee && enveloppeCible != null && allocationApres != null &&
                    kotlin.math.abs(allocationApres.solde) < 0.01
                ) {
                    enveloppeRepository.mettreAJourEnveloppe(enveloppeCible.copy(estArchive = true))
                }
            }
        }
    }
}
