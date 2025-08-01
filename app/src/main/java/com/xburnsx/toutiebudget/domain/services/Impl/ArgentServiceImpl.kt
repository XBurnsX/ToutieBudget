package com.xburnsx.toutiebudget.domain.services.Impl

import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.domain.services.ArgentService
import com.xburnsx.toutiebudget.domain.usecases.VirementUseCase
import java.util.*
import javax.inject.Inject

/**
 * Implémentation du service ArgentService qui gère les opérations financières.
 */
class ArgentServiceImpl @Inject constructor(
    private val compteRepository: CompteRepository,
    private val transactionRepository: TransactionRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository,
    private val virementUseCase: VirementUseCase
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
        
        // 3. CRÉER une nouvelle allocation mensuelle (pas de récupération)
        val nouvelleAllocation = com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle(
            id = "",
            utilisateurId = "",
            enveloppeId = enveloppeId,
            mois = mois,
            solde = montant,
            alloue = montant,
            depense = 0.0,
            compteSourceId = compteSourceId,
            collectionCompteSource = collectionCompteSource
        )

        val allocationCreee = allocationMensuelleRepository.creerNouvelleAllocation(nouvelleAllocation)

        // 4. Mettre à jour le solde du compte source
        val nouveauSolde = compteSource.solde - montant
        compteRepository.mettreAJourSolde(compteSourceId, collectionCompteSource, nouveauSolde)
        
        // 5. Créer une transaction pour cette allocation
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            utilisateurId = "", // À récupérer depuis un UserRepository ou une session
            type = TypeTransaction.Depense,
            montant = montant,
            date = Date(),
            compteId = compteSourceId,
            collectionCompte = collectionCompteSource,
            allocationMensuelleId = allocationCreee.id,
            note = "Allocation vers enveloppe #$enveloppeId"
        )
        
        transactionRepository.creerTransaction(transaction)
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
        
        // 3. Créer une nouvelle allocation si c'est une dépense avec enveloppe
        var allocationMensuelleId: String? = null
        if (typeTransaction == TypeTransaction.Depense && !enveloppeId.isNullOrBlank()) {
            val nouvelleAllocation = com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle(
                id = "",
                utilisateurId = "",
                enveloppeId = enveloppeId,
                mois = date,
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
        val nouveauSolde = when (typeTransaction) {
            TypeTransaction.Depense -> compte.solde - montant
            TypeTransaction.Revenu -> compte.solde + montant
            TypeTransaction.Pret -> compte.solde - montant
            TypeTransaction.RemboursementRecu -> compte.solde + montant
            TypeTransaction.Emprunt -> compte.solde + montant
            TypeTransaction.RemboursementDonne -> compte.solde - montant
            TypeTransaction.Paiement -> compte.solde - montant
            TypeTransaction.TransfertSortant -> compte.solde - montant
            TypeTransaction.TransfertEntrant -> compte.solde + montant
        }
        
        // 5. Mettre à jour le solde du compte
        compteRepository.mettreAJourSolde(compteId, collectionCompte, nouveauSolde)
        
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
        val nouveauSoldeCompte = compte.solde - montant
        compteRepository.mettreAJourSolde(compte.id, compte.collection, nouveauSoldeCompte)
        
        // CRÉER une nouvelle allocation mensuelle (pas de récupération)
        val nouvelleAllocation = com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle(
            id = "",
            utilisateurId = compte.utilisateurId,
            enveloppeId = enveloppe.id,
            mois = Date(),
            solde = montant,
            alloue = montant,
            depense = 0.0,
            compteSourceId = compte.id,
            collectionCompteSource = compte.collection
        )

        val allocationCreee = allocationMensuelleRepository.creerNouvelleAllocation(nouvelleAllocation)

        // Créer la transaction
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            utilisateurId = compte.utilisateurId,
            type = TypeTransaction.Depense,
            montant = montant,
            date = Date(),
            compteId = compte.id,
            collectionCompte = compte.collection,
            allocationMensuelleId = allocationCreee.id,
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
        
        // CRÉER une nouvelle allocation pour la destination (pas de récupération)
        val nouvelleAllocationDest = com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle(
            id = "",
            utilisateurId = enveloppeDestination.utilisateurId,
            enveloppeId = enveloppeDestination.id,
            mois = Date(),
            solde = montant,
            alloue = montant,
            depense = 0.0,
            compteSourceId = null,
            collectionCompteSource = null
        )

        val allocationDestCreee = allocationMensuelleRepository.creerNouvelleAllocation(nouvelleAllocationDest)

        // Créer une nouvelle allocation pour marquer la sortie de la source
        val nouvelleAllocationSource = com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle(
            id = "",
            utilisateurId = enveloppeSource.utilisateurId,
            enveloppeId = enveloppeSource.id,
            mois = Date(),
            solde = -montant,
            alloue = 0.0,
            depense = montant,
            compteSourceId = null,
            collectionCompteSource = null
        )

        val allocationSourceCreee = allocationMensuelleRepository.creerNouvelleAllocation(nouvelleAllocationSource)

        // Créer les transactions pour tracer les virements
        val transactionSource = Transaction(
            id = UUID.randomUUID().toString(),
            utilisateurId = enveloppeSource.utilisateurId,
            type = TypeTransaction.Depense,
            montant = montant,
            date = Date(),
            compteId = "", // Pas de compte impliqué
            collectionCompte = "",
            allocationMensuelleId = allocationSourceCreee.id,
            note = "Virement vers enveloppe ${enveloppeDestination.nom}"
        )
        
        val transactionDest = Transaction(
            id = UUID.randomUUID().toString(),
            utilisateurId = enveloppeDestination.utilisateurId,
            type = TypeTransaction.Revenu,
            montant = montant,
            date = Date(),
            compteId = "", // Pas de compte impliqué
            collectionCompte = "",
            allocationMensuelleId = allocationDestCreee.id,
            note = "Virement depuis enveloppe ${enveloppeSource.nom}"
        )

        transactionRepository.creerTransaction(transactionSource)
        transactionRepository.creerTransaction(transactionDest)
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
        println("🔍 [DEBUG] effectuerVirementEntreComptes - DÉBUT")
        println("🔍 [DEBUG] compteSourceId: $compteSourceId")
        println("🔍 [DEBUG] compteDestId: $compteDestId")
        println("🔍 [DEBUG] montant: $montant")

        if (montant <= 0) throw IllegalArgumentException("Le montant du virement doit être positif.")

        // 1. Récupérer les comptes et leurs collections
        println("🔍 [DEBUG] Récupération du compte source...")
        val compteSourceResult = compteRepository.recupererCompteParIdToutesCollections(compteSourceId)
        if (compteSourceResult.isFailure) {
            println("❌ [DEBUG] ERREUR lors de la récupération du compte source: ${compteSourceResult.exceptionOrNull()?.message}")
            throw compteSourceResult.exceptionOrNull() ?: Exception("Erreur récupération compte source")
        }
        val compteSource = compteSourceResult.getOrThrow()
        println("✅ [DEBUG] Compte source récupéré: ${compteSource.nom} (${compteSource.collection})")

        println("🔍 [DEBUG] Récupération du compte destination...")
        val compteDestResult = compteRepository.recupererCompteParIdToutesCollections(compteDestId)
        if (compteDestResult.isFailure) {
            println("❌ [DEBUG] ERREUR lors de la récupération du compte destination: ${compteDestResult.exceptionOrNull()?.message}")
            throw compteDestResult.exceptionOrNull() ?: Exception("Erreur récupération compte destination")
        }
        val compteDest = compteDestResult.getOrThrow()
        println("✅ [DEBUG] Compte destination récupéré: ${compteDest.nom} (${compteDest.collection})")

        // 2. Vérifier le solde du compte source
        if (compteSource.solde < montant) {
            println("❌ [DEBUG] Solde insuffisant: ${compteSource.solde} < $montant")
            throw IllegalStateException("Solde insuffisant sur le compte '$nomCompteSource'.")
        }
        println("✅ [DEBUG] Solde suffisant: ${compteSource.solde} >= $montant")

        // 3. Mettre à jour les soldes
        val nouveauSoldeSource = compteSource.solde - montant
        try {
            println("TRANSFERT: Mise à jour solde source")
            compteRepository.mettreAJourSolde(compteSource.id, compteSource.collection, nouveauSoldeSource)
            println("TRANSFERT: ✅ Solde source OK")
        } catch (e: Exception) {
            println("TRANSFERT: ❌ ERREUR solde source: ${e.message}")
            throw Exception("Erreur mise à jour solde source: ${e.message}")
        }

        val nouveauSoldeDest = compteDest.solde + montant
        try {
            println("TRANSFERT: Mise à jour solde destination")
            compteRepository.mettreAJourSolde(compteDest.id, compteDest.collection, nouveauSoldeDest)
            println("TRANSFERT: ✅ Solde destination OK")
        } catch (e: Exception) {
            println("TRANSFERT: ❌ ERREUR solde destination: ${e.message}")
            throw Exception("Erreur mise à jour solde destination: ${e.message}")
        }

        // 4. Gérer le "Prêt à placer" pour les comptes chèque
        // Mise à jour du prêt à placer du compte source (diminution)
        if (compteSource is com.xburnsx.toutiebudget.data.modeles.CompteCheque) {
            println("🔍 [DEBUG] Mise à jour prêt à placer du compte source...")
            val variationPretAPlacerSource = -montant // Variation négative pour retirer le montant
            try {
                compteRepository.mettreAJourPretAPlacerSeulement(compteSource.id, variationPretAPlacerSource).getOrThrow()
                println("✅ [DEBUG] Prêt à placer du compte source mis à jour avec variation: $variationPretAPlacerSource")
            } catch (e: Exception) {
                println("❌ [DEBUG] ERREUR mise à jour prêt à placer du compte source: ${e.message}")
                throw Exception("Erreur mise à jour prêt à placer du compte source: ${e.message}")
            }
        }

        // Mise à jour du prêt à placer du compte destination (augmentation)
        if (compteDest is com.xburnsx.toutiebudget.data.modeles.CompteCheque) {
            println("🔍 [DEBUG] Mise à jour prêt à placer du compte destination...")
            println("🔍 [DEBUG] Compte destination: ${compteDest.nom}")
            println("🔍 [DEBUG] ID compte destination: ${compteDest.id}")
            println("🔍 [DEBUG] Prêt à placer actuel: ${compteDest.pretAPlacer}")
            println("🔍 [DEBUG] Montant à ajouter: $montant")
            val variationPretAPlacerDest = montant // Variation positive pour ajouter le montant
            try {
                val result = compteRepository.mettreAJourPretAPlacerSeulement(compteDest.id, variationPretAPlacerDest)
                if (result.isSuccess) {
                    println("✅ [DEBUG] Prêt à placer du compte destination mis à jour avec variation: $variationPretAPlacerDest")
                } else {
                    println("❌ [DEBUG] ÉCHEC mise à jour prêt à placer du compte destination: ${result.exceptionOrNull()?.message}")
                    throw result.exceptionOrNull() ?: Exception("Erreur inconnue")
                }
            } catch (e: Exception) {
                println("❌ [DEBUG] ERREUR mise à jour prêt à placer du compte destination: ${e.message}")
                throw Exception("Erreur mise à jour prêt à placer du compte destination: ${e.message}")
            }
        } else {
            println("🔍 [DEBUG] Compte destination n'est PAS un CompteCheque: ${compteDest::class.simpleName}")
        }

        // 5. Créer la transaction de sortie
        println("🔍 [DEBUG] Création transaction sortante...")
        val transactionSortante = Transaction(
            id = "",
            utilisateurId = compteSource.utilisateurId,
            type = TypeTransaction.Pret, // Utiliser Pret au lieu de TransfertSortant
            montant = montant,
            date = Date(),
            compteId = compteSource.id,
            collectionCompte = compteSource.collection,
            allocationMensuelleId = null,
            tiers = "Argent envoyé à $nomCompteDest", // LE TIERS c'est ça ! PAS de note !
            note = null // PAS de note !
        )
        try {
            transactionRepository.creerTransaction(transactionSortante).getOrThrow()
            println("✅ [DEBUG] Transaction sortante créée")
        } catch (e: Exception) {
            println("❌ [DEBUG] ERREUR création transaction sortante: ${e.message}")
            throw Exception("Erreur création transaction sortante: ${e.message}")
        }

        // 6. Créer la transaction d'entrée
        println("🔍 [DEBUG] Création transaction entrante...")
        val transactionEntrante = Transaction(
            id = "",
            utilisateurId = compteDest.utilisateurId,
            type = TypeTransaction.Emprunt, // Utiliser Emprunt au lieu de TransfertEntrant
            montant = montant,
            date = Date(),
            compteId = compteDest.id,
            collectionCompte = compteDest.collection,
            allocationMensuelleId = null,
            tiers = "Argent reçu de $nomCompteSource", // LE TIERS c'est ça ! PAS de note !
            note = null // PAS de note !
        )
        try {
            transactionRepository.creerTransaction(transactionEntrante).getOrThrow()
            println("✅ [DEBUG] Transaction entrante créée")
        } catch (e: Exception) {
            println("❌ [DEBUG] ERREUR création transaction entrante: ${e.message}")
            throw Exception("Erreur création transaction entrante: ${e.message}")
        }

        println("✅ [DEBUG] effectuerVirementEntreComptes - SUCCÈS COMPLET")
    }
}
