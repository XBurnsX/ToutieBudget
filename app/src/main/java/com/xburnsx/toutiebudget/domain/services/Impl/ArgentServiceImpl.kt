package com.xburnsx.toutiebudget.domain.services.Impl

import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.domain.services.ArgentService
import java.util.*
import javax.inject.Inject

/**
 * Implémentation du service ArgentService qui gère les opérations financières.
 */
class ArgentServiceImpl @Inject constructor(
    private val compteRepository: CompteRepository,
    private val transactionRepository: TransactionRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository
) : ArgentService {

    /**
     * Alloue un montant d'un compte source vers une enveloppe pour un mois donné.
     *
     * @param enveloppeId L'ID de l'enveloppe à créditer.
     * @param compteSourceId L'ID du compte d'où provient l'argent.
     * @param collectionCompteSource Le nom de la collection du compte source (ex: "comptes_cheque").
     * @param montant Le montant à allouer.
     * @param mois Le mois de l'allocation (le premier jour du mois).
     * @return Une Result<Unit> indiquant le succès ou l'échec de l'opération.
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
        
        // 3. Récupérer ou créer l'allocation mensuelle pour cette enveloppe et ce mois
        val allocation = allocationMensuelleRepository.getOrCreateAllocationMensuelle(
            enveloppeId = enveloppeId,
            mois = mois
        )
        
        // 4. Mettre à jour le solde du compte source
        val nouveauSolde = compteSource.solde - montant
        compteRepository.mettreAJourSolde(compteSourceId, collectionCompteSource, nouveauSolde)
        
        // 5. Mettre à jour l'allocation mensuelle
        val nouveauSoldeAllocation = allocation.solde + montant
        val nouvelleAllocation = allocation.copy(
            solde = nouveauSoldeAllocation,
            alloue = allocation.alloue + montant,
            compteSourceId = compteSourceId,
            collectionCompteSource = collectionCompteSource
        )
        allocationMensuelleRepository.mettreAJourAllocation(nouvelleAllocation)
        
        // 6. Créer une transaction pour cette allocation
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            utilisateurId = "", // À récupérer depuis un UserRepository ou une session
            type = TypeTransaction.Depense,
            montant = montant,
            date = Date(),
            compteId = compteSourceId,
            collectionCompte = collectionCompteSource,
            allocationMensuelleId = allocation.id,
            note = "Allocation vers enveloppe #$enveloppeId"
        )
        
        transactionRepository.creerTransaction(transaction)
    }
    
    /**
     * Enregistre une nouvelle transaction (dépense ou revenu) et met à jour les soldes correspondants.
     *
     * @param type Le type de transaction (Depense, Revenu, Pret, Emprunt).
     * @param montant Le montant de la transaction.
     * @param date La date de la transaction.
     * @param compteId L'ID du compte affecté.
     * @param collectionCompte Le nom de la collection du compte affecté.
     * @param allocationMensuelleId (Optionnel) L'ID de l'allocation mensuelle si c'est une dépense liée à une enveloppe.
     * @param note Une description pour la transaction.
     * @return Une Result<Unit> indiquant le succès ou l'échec de l'opération.
     */
    override suspend fun enregistrerTransaction(
        type: String,
        montant: Double,
        date: Date,
        compteId: String,
        collectionCompte: String,
        allocationMensuelleId: String?,
        note: String?
    ): Result<Unit> = runCatching {
        if (montant <= 0) throw IllegalArgumentException("Le montant de la transaction doit être positif.")
        
        // 1. Récupérer le compte
        val compte = compteRepository.getCompteById(compteId, collectionCompte)
            ?: throw IllegalArgumentException("Compte non trouvé: $compteId")
        
        // 2. Convertir le type de transaction (String vers Enum)
        val typeTransaction = try {
            TypeTransaction.valueOf(type)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Type de transaction invalide: $type. Valeurs acceptées: ${TypeTransaction.values().joinToString()}")
        }
        
        // 3. Calculer le nouveau solde du compte selon le type de transaction
        val nouveauSolde = when (typeTransaction) {
            TypeTransaction.Depense -> compte.solde - montant
            TypeTransaction.Revenu -> compte.solde + montant
            TypeTransaction.Pret -> compte.solde - montant
            TypeTransaction.Emprunt -> compte.solde + montant
        }
        
        // 4. Mettre à jour le solde du compte
        compteRepository.mettreAJourSolde(compteId, collectionCompte, nouveauSolde)
        
        // 5. Si c'est une dépense liée à une enveloppe, mettre à jour l'allocation mensuelle
        if (typeTransaction == TypeTransaction.Depense && allocationMensuelleId != null) {
            val allocation = allocationMensuelleRepository.getAllocationById(allocationMensuelleId)
                ?: throw IllegalArgumentException("Allocation mensuelle non trouvée: $allocationMensuelleId")
            
            val nouveauSoldeAllocation = allocation.solde - montant
            val nouvelleAllocation = allocation.copy(
                solde = nouveauSoldeAllocation,
                depense = allocation.depense + montant
            )
            
            allocationMensuelleRepository.mettreAJourAllocation(nouvelleAllocation)
        }
        
        // 6. Créer et enregistrer la transaction
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
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
        val nouveauSoldeSource = compteSource.solde - montant
        val nouveauSoldeDest = compteDest.solde + montant
        
        compteRepository.mettreAJourSolde(compteSourceId, collectionCompteSource, nouveauSoldeSource)
        compteRepository.mettreAJourSolde(compteDestId, collectionCompteDest, nouveauSoldeDest)
        
        // 4. Créer les transactions correspondantes
        val transactionSource = Transaction(
            id = UUID.randomUUID().toString(),
            utilisateurId = "", // À récupérer depuis un UserRepository ou une session
            type = TypeTransaction.Pret,
            montant = montant,
            date = Date(),
            compteId = compteSourceId,
            collectionCompte = collectionCompteSource,
            allocationMensuelleId = null,
            note = "Transfert vers compte #$compteDestId"
        )
        
        val transactionDest = Transaction(
            id = UUID.randomUUID().toString(),
            utilisateurId = "", // À récupérer depuis un UserRepository ou une session
            type = TypeTransaction.Emprunt,
            montant = montant,
            date = Date(),
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
            id = UUID.randomUUID().toString(),
            utilisateurId = compteSource.utilisateurId,
            type = TypeTransaction.Pret,
            montant = montant,
            date = Date(),
            compteId = compteSource.id,
            collectionCompte = compteSource.collection,
            allocationMensuelleId = null,
            note = "Virement vers ${compteDestination.nom}"
        )
        
        val transactionDest = Transaction(
            id = UUID.randomUUID().toString(),
            utilisateurId = compteDestination.utilisateurId,
            type = TypeTransaction.Emprunt,
            montant = montant,
            date = Date(),
            compteId = compteDestination.id,
            collectionCompte = compteDestination.collection,
            allocationMensuelleId = null,
            note = "Virement depuis ${compteSource.nom}"
        )
        
        transactionRepository.creerTransaction(transactionSource)
        transactionRepository.creerTransaction(transactionDest)
    }

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
        val nouveauSoldeCompte = compte.solde - montant
        compteRepository.mettreAJourSolde(compte.id, compte.collection, nouveauSoldeCompte)
        
        // Créer une allocation mensuelle pour l'enveloppe
        val allocation = allocationMensuelleRepository.getOrCreateAllocationMensuelle(
            enveloppeId = enveloppe.id,
            mois = Date()
        )
        
        // Mettre à jour l'allocation
        val nouveauSoldeAllocation = allocation.solde + montant
        val nouvelleAllocation = allocation.copy(
            solde = nouveauSoldeAllocation,
            alloue = allocation.alloue + montant,
            compteSourceId = compte.id,
            collectionCompteSource = compte.collection
        )
        allocationMensuelleRepository.mettreAJourAllocation(nouvelleAllocation)
        
        // Créer la transaction
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            utilisateurId = compte.utilisateurId,
            type = TypeTransaction.Depense,
            montant = montant,
            date = Date(),
            compteId = compte.id,
            collectionCompte = compte.collection,
            allocationMensuelleId = allocation.id,
            note = "Virement vers enveloppe ${enveloppe.nom}"
        )
        
        transactionRepository.creerTransaction(transaction)
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
        val nouveauSoldeCompte = compte.solde + montant
        compteRepository.mettreAJourSolde(compte.id, compte.collection, nouveauSoldeCompte)
        
        // Mettre à jour l'allocation
        val nouveauSoldeAllocation = allocation.solde - montant
        val nouvelleAllocation = allocation.copy(
            solde = nouveauSoldeAllocation,
            depense = allocation.depense + montant
        )
        allocationMensuelleRepository.mettreAJourAllocation(nouvelleAllocation)
        
        // Créer la transaction
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            utilisateurId = compte.utilisateurId,
            type = TypeTransaction.Revenu,
            montant = montant,
            date = Date(),
            compteId = compte.id,
            collectionCompte = compte.collection,
            allocationMensuelleId = allocation.id,
            note = "Virement depuis enveloppe ${enveloppe.nom}"
        )
        
        transactionRepository.creerTransaction(transaction)
    }

    override suspend fun effectuerVirementEnveloppeVersEnveloppe(
        enveloppeSource: com.xburnsx.toutiebudget.data.modeles.Enveloppe,
        enveloppeDestination: com.xburnsx.toutiebudget.data.modeles.Enveloppe,
        montant: Double
    ): Result<Unit> = runCatching {
        if (montant <= 0) throw IllegalArgumentException("Le montant du virement doit être positif.")
        
        // Récupérer les allocations mensuelles
        val allocationSource = allocationMensuelleRepository.getAllocationById(enveloppeSource.id)
            ?: throw IllegalArgumentException("Aucune allocation trouvée pour l'enveloppe source ${enveloppeSource.nom}")
        
        val allocationDest = allocationMensuelleRepository.getOrCreateAllocationMensuelle(
            enveloppeId = enveloppeDestination.id,
            mois = Date()
        )
        
        if (allocationSource.solde < montant) {
            throw IllegalStateException("Solde insuffisant dans l'enveloppe source ${enveloppeSource.nom}.")
        }
        
        // Mettre à jour l'allocation source
        val nouveauSoldeSource = allocationSource.solde - montant
        val nouvelleAllocationSource = allocationSource.copy(
            solde = nouveauSoldeSource,
            depense = allocationSource.depense + montant
        )
        allocationMensuelleRepository.mettreAJourAllocation(nouvelleAllocationSource)
        
        // Mettre à jour l'allocation destination
        val nouveauSoldeDest = allocationDest.solde + montant
        val nouvelleAllocationDest = allocationDest.copy(
            solde = nouveauSoldeDest,
            alloue = allocationDest.alloue + montant
        )
        allocationMensuelleRepository.mettreAJourAllocation(nouvelleAllocationDest)
        
        // Créer une transaction fictive pour tracer le virement
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            utilisateurId = enveloppeSource.utilisateurId,
            type = TypeTransaction.Depense,
            montant = montant,
            date = Date(),
            compteId = "", // Pas de compte impliqué
            collectionCompte = "",
            allocationMensuelleId = allocationSource.id,
            note = "Virement vers enveloppe ${enveloppeDestination.nom}"
        )
        
        transactionRepository.creerTransaction(transaction)
    }
}
