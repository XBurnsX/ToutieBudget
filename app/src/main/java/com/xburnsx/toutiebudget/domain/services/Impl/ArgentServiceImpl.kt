package com.xburnsx.toutiebudget.domain.services.Impl

import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.domain.services.ArgentService
import com.xburnsx.toutiebudget.domain.usecases.VirementUseCase
import com.xburnsx.toutiebudget.utils.MoneyFormatter
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
            date = Date(), // Utilise l'heure locale actuelle du téléphone
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
        
        // 3. ✅ GÉRER L'ALLOCATION CORRECTEMENT - PAS DE DOUBLON
        var allocationMensuelleId: String? = null
        if (typeTransaction == TypeTransaction.Depense && !enveloppeId.isNullOrBlank()) {
            // ✅ Récupérer ou créer l'allocation de base pour ce mois
            val calendrier = java.util.Calendar.getInstance().apply {
                time = date
                set(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val premierJourMois = calendrier.time
            
            val allocationBase = allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeId, premierJourMois)
            
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
            TypeTransaction.Paiement -> compte.solde - montant
            TypeTransaction.TransfertSortant -> compte.solde - montant
            TypeTransaction.TransfertEntrant -> compte.solde + montant
        }
        
        // 🎯 ARRONDIR AUTOMATIQUEMENT LE NOUVEAU SOLDE
        val nouveauSolde = MoneyFormatter.roundAmount(nouveauSoldeBrut)
        
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
        val nouveauSoldeSourceBrut = compteSource.solde - montant
        val nouveauSoldeDestBrut = compteDest.solde + montant
        
        // 🎯 ARRONDIR AUTOMATIQUEMENT LES NOUVEAUX SOLDES
        val nouveauSoldeSource = MoneyFormatter.roundAmount(nouveauSoldeSourceBrut)
        val nouveauSoldeDest = MoneyFormatter.roundAmount(nouveauSoldeDestBrut)
        
        compteRepository.mettreAJourSolde(compteSourceId, collectionCompteSource, nouveauSoldeSource)
        compteRepository.mettreAJourSolde(compteDestId, collectionCompteDest, nouveauSoldeDest)
        
        // 4. Créer les transactions correspondantes
        val transactionSource = Transaction(
            id = UUID.randomUUID().toString(),
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
            id = UUID.randomUUID().toString(),
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
            id = UUID.randomUUID().toString(),
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
            id = UUID.randomUUID().toString(),
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
                    val allocationExistante = allocationMensuelleRepository.recupererOuCreerAllocation(enveloppe.id, premierJourMois)

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

        // Créer la transaction
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
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
            id = UUID.randomUUID().toString(),
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
                    val allocationSourceExistante = allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeSource.id, premierJourMois)
            val allocationDestExistante = allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeDestination.id, premierJourMois)

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

        // Créer les transactions pour tracer les virements
        val transactionSource = Transaction(
            id = UUID.randomUUID().toString(),
            utilisateurId = enveloppeSource.utilisateurId,
            type = TypeTransaction.Depense,
            montant = montant,
            date = Date(), // Utilise l'heure locale actuelle du téléphone
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
            date = Date(), // Utilise l'heure locale actuelle du téléphone
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
        if (compteSource is com.xburnsx.toutiebudget.data.modeles.CompteCheque) {
            val variationPretAPlacerSource = -montant // Variation négative pour retirer le montant
            try {
                compteRepository.mettreAJourPretAPlacerSeulement(compteSource.id, variationPretAPlacerSource).getOrThrow()
            } catch (e: Exception) {
                throw Exception("Erreur mise à jour prêt à placer du compte source: ${e.message}")
            }
        }

        // Mise à jour du prêt à placer du compte destination (augmentation)
        if (compteDest is com.xburnsx.toutiebudget.data.modeles.CompteCheque) {
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
            id = "",
            utilisateurId = compteSource.utilisateurId,
            type = TypeTransaction.Pret, // Utiliser Pret au lieu de TransfertSortant
            montant = montant,
            date = Date(), // Utilise l'heure locale actuelle du téléphone
            compteId = compteSource.id,
            collectionCompte = compteSource.collection,
            allocationMensuelleId = null,
            tiers = "Argent envoyé à $nomCompteDest", // LE TIERS c'est ça ! PAS de note !
            note = null // PAS de note !
        )
        try {
            transactionRepository.creerTransaction(transactionSortante).getOrThrow()
        } catch (e: Exception) {
            throw Exception("Erreur création transaction sortante: ${e.message}")
        }

        // 6. Créer la transaction d'entrée
        val transactionEntrante = Transaction(
            id = "",
            utilisateurId = compteDest.utilisateurId,
            type = TypeTransaction.Emprunt, // Utiliser Emprunt au lieu de TransfertEntrant
            montant = montant,
            date = Date(), // Utilise l'heure locale actuelle du téléphone
            compteId = compteDest.id,
            collectionCompte = compteDest.collection,
            allocationMensuelleId = null,
            tiers = "Argent reçu de $nomCompteSource", // LE TIERS c'est ça ! PAS de note !
            note = null // PAS de note !
        )
        try {
            transactionRepository.creerTransaction(transactionEntrante).getOrThrow()
        } catch (e: Exception) {
            throw Exception("Erreur création transaction entrante: ${e.message}")
        }
    }

    /**
     * Effectue un paiement de carte de crédit ou de dette.
     * Crée deux transactions : une sortie du compte qui paie et une entrée sur la carte/dette.
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
        
        // 1. Récupérer les comptes
        val compteQuiPaie = compteRepository.getCompteById(compteQuiPaieId, collectionCompteQuiPaie)
            ?: throw IllegalArgumentException("Compte qui paie non trouvé: $compteQuiPaieId")
            
        val carteOuDette = compteRepository.getCompteById(carteOuDetteId, collectionCarteOuDette)
            ?: throw IllegalArgumentException("Carte/Dette non trouvée: $carteOuDetteId")
        
        // 2. Vérifier que le compte qui paie a suffisamment de fonds
        if (compteQuiPaie.solde < montant) {
            throw IllegalStateException("Solde insuffisant sur le compte qui paie.")
        }
        
        // 3. Mettre à jour les soldes
        val nouveauSoldeCompteQuiPaie = compteQuiPaie.solde - montant
        val nouveauSoldeCarteOuDette = carteOuDette.solde + montant // Réduire la dette (solde négatif + montant positif)
        
        // 🎯 ARRONDIR AUTOMATIQUEMENT LES NOUVEAUX SOLDES
        val nouveauSoldeCompteQuiPaieArrondi = MoneyFormatter.roundAmount(nouveauSoldeCompteQuiPaie)
        val nouveauSoldeCarteOuDetteArrondi = MoneyFormatter.roundAmount(nouveauSoldeCarteOuDette)
        
        compteRepository.mettreAJourSolde(compteQuiPaieId, collectionCompteQuiPaie, nouveauSoldeCompteQuiPaieArrondi)
        compteRepository.mettreAJourSolde(carteOuDetteId, collectionCarteOuDette, nouveauSoldeCarteOuDetteArrondi)
        
        // 4. Créer la transaction de sortie (compte qui paie)
        val transactionSortante = Transaction(
            id = UUID.randomUUID().toString(),
            utilisateurId = compteQuiPaie.utilisateurId,
            type = TypeTransaction.Pret, // Utiliser Pret au lieu de Paiement (accepté par le backend)
            montant = montant,
            date = Date(),
            compteId = compteQuiPaieId,
            collectionCompte = collectionCompteQuiPaie,
            allocationMensuelleId = null,
            tiers = note ?: "Paiement ${carteOuDette.nom}",
            note = null
        )
        
        println("DEBUG: Création transaction sortante: ${transactionSortante.id}")
        val resultSortante = transactionRepository.creerTransaction(transactionSortante)
        println("DEBUG: Résultat création transaction sortante: ${if (resultSortante.isSuccess) "SUCCÈS" else "ÉCHEC: ${resultSortante.exceptionOrNull()?.message}"}")
        
        // 5. Créer la transaction d'entrée (carte/dette)
        val transactionEntrante = Transaction(
            id = UUID.randomUUID().toString(),
            utilisateurId = carteOuDette.utilisateurId,
            type = TypeTransaction.Emprunt, // Utiliser Emprunt au lieu de RemboursementRecu (accepté par le backend)
            montant = montant,
            date = Date(),
            compteId = carteOuDetteId,
            collectionCompte = collectionCarteOuDette,
            allocationMensuelleId = null,
            tiers = note ?: "Paiement reçu de ${compteQuiPaie.nom}",
            note = null
        )
        
        println("DEBUG: Création transaction entrante: ${transactionEntrante.id}")
        val resultEntrante = transactionRepository.creerTransaction(transactionEntrante)
        println("DEBUG: Résultat création transaction entrante: ${if (resultEntrante.isSuccess) "SUCCÈS" else "ÉCHEC: ${resultEntrante.exceptionOrNull()?.message}"}")
    }
}
