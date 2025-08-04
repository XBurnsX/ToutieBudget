package com.xburnsx.toutiebudget.domain.usecases

import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.*
import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Calendar
import java.util.Date

/**
 * Use case pour modifier une transaction existante.
 * Gère la logique métier complète de modification d'une transaction.
 */
class ModifierTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository
) {

    /**
     * Modifie une transaction existante avec mise à jour des soldes.
     * 
     * @param transactionId ID de la transaction à modifier
     * @param typeTransaction Nouveau type de transaction
     * @param montant Nouveau montant de la transaction
     * @param compteId Nouveau compte concerné
     * @param collectionCompte Nouvelle collection du compte
     * @param enveloppeId Nouvelle enveloppe (pour les dépenses)
     * @param tiersNom Nouveau tiers associé à la transaction
     * @param note Nouvelle note facultative
     * @param date Nouvelle date de la transaction
     * 
     * @return Result indiquant le succès ou l'échec avec l'exception
     */
    suspend fun executer(
        transactionId: String,
        typeTransaction: TypeTransaction,
        montant: Double,
        compteId: String,
        collectionCompte: String,
        enveloppeId: String? = null,
        tiersNom: String? = null,
        note: String? = null,
        date: Date = Date()
    ): Result<Unit> {
        
        if (montant <= 0) {
            return Result.failure(Exception("Le montant doit être positif"))
        }

        return try {
            coroutineScope {

                // 1. Récupérer la transaction existante
                val transactionExistante = transactionRepository.recupererTransactionParId(transactionId)
                    .getOrNull() ?: throw Exception("Transaction non trouvée")


                // 2. Calculer les différences pour les mises à jour
                val differenceMontant = montant - transactionExistante.montant
                val ancienType = transactionExistante.type
                val ancienCompteId = transactionExistante.compteId
                val ancienneCollectionCompte = transactionExistante.collectionCompte

                // 3. Obtenir ou créer l'allocation mensuelle si c'est une dépense
                var allocationMensuelleId: String? = null
                if (typeTransaction == TypeTransaction.Depense && !enveloppeId.isNullOrBlank()) {

                    // IMPORTANT: Les allocations mensuelles utilisent la nouvelle date de la transaction
                    val calendrier = Calendar.getInstance().apply {
                        time = date // Utiliser la nouvelle date de la transaction
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val premierJourMois = calendrier.time

                    val resultAllocation = obtenirOuCreerAllocationMensuelle(enveloppeId, premierJourMois, compteId, collectionCompte)
                    if (resultAllocation.isFailure) {
                        throw resultAllocation.exceptionOrNull() ?: Exception("Erreur lors de la gestion de l'allocation")
                    }
                    allocationMensuelleId = resultAllocation.getOrNull()
                }

                // 4. Créer la transaction modifiée
                val transactionModifiee = transactionExistante.copy(
                    type = typeTransaction,
                    montant = montant,
                    date = date,
                    note = note,
                    compteId = compteId,
                    collectionCompte = collectionCompte,
                    allocationMensuelleId = allocationMensuelleId,
                    tiers = tiersNom
                )

                val resultTransaction = transactionRepository.mettreAJourTransaction(transactionModifiee)
                if (resultTransaction.isFailure) {
                    throw resultTransaction.exceptionOrNull() ?: Exception("Erreur lors de la mise à jour de la transaction")
                }

                // 5. Mettre à jour les soldes en parallèle
                val tachesMiseAJour = mutableListOf<kotlinx.coroutines.Deferred<Result<Unit>>>()

                // Mise à jour du compte (annuler l'ancienne transaction et appliquer la nouvelle)
                tachesMiseAJour.add(async { 
                    val resultatAnnulation = annulerTransactionCompte(ancienCompteId, ancienneCollectionCompte, ancienType, transactionExistante.montant)
                    if (resultatAnnulation.isFailure) {
                        return@async resultatAnnulation
                    }
                    mettreAJourSoldeCompte(compteId, collectionCompte, typeTransaction, montant)
                })

                // Mise à jour de l'enveloppe si nécessaire
                tachesMiseAJour.add(async { 
                    // 1. Annuler l'ancienne dépense si elle existait
                    if (transactionExistante.allocationMensuelleId != null && transactionExistante.type == TypeTransaction.Depense) {
                        val resultatAnnulation = enveloppeRepository.annulerDepenseAllocation(transactionExistante.allocationMensuelleId, transactionExistante.montant)
                        if (resultatAnnulation.isFailure) {
                            return@async resultatAnnulation
                        }
                    }
                    
                    // 2. Ajouter la nouvelle dépense si c'est une dépense maintenant
                    if (typeTransaction == TypeTransaction.Depense && !allocationMensuelleId.isNullOrBlank()) {
                        enveloppeRepository.ajouterDepenseAllocation(allocationMensuelleId, montant)
                    } else {
                        Result.success(Unit)
                    }
                })

                val resultats = tachesMiseAJour.awaitAll()
                
                // Vérifier que toutes les mises à jour ont réussi
                resultats.forEach { resultat ->
                    if (resultat.isFailure) {
                        throw resultat.exceptionOrNull() ?: Exception("Erreur lors de la mise à jour des soldes")
                    }
                }

                // 🔄 DÉCLENCHER EXPLICITEMENT LE RAFRAÎCHISSEMENT DE L'INTERFACE
                BudgetEvents.refreshManual()

                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Annule l'effet d'une transaction sur un compte.
     */
    private suspend fun annulerTransactionCompte(
        compteId: String,
        collectionCompte: String,
        typeTransaction: TypeTransaction,
        montant: Double
    ): Result<Unit> {
        // Inverser la variation du solde
        val variationSolde = when (typeTransaction) {
            TypeTransaction.Depense -> montant  // Annuler une dépense = addition
            TypeTransaction.Revenu -> -montant  // Annuler un revenu = soustraction
            TypeTransaction.Pret -> montant      // Annuler un prêt = addition
            TypeTransaction.RemboursementRecu -> -montant  // Annuler un remboursement = soustraction
            TypeTransaction.Emprunt -> -montant // Annuler un emprunt = soustraction
            TypeTransaction.RemboursementDonne -> montant  // Annuler un remboursement donné = addition
            TypeTransaction.Paiement -> montant // Annuler un paiement = addition
            TypeTransaction.TransfertSortant -> montant  // Annuler un transfert sortant = addition
            TypeTransaction.TransfertEntrant -> -montant // Annuler un transfert entrant = soustraction
        }

        return compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
            compteId, 
            collectionCompte, 
            variationSolde,
            false // Ne pas mettre à jour le prêt à placer lors de l'annulation
        )
    }

    /**
     * Met à jour le solde d'un compte.
     */
    private suspend fun mettreAJourSoldeCompte(
        compteId: String,
        collectionCompte: String,
        typeTransaction: TypeTransaction,
        montant: Double
    ): Result<Unit> {
        // Calculer la variation du solde
        val variationSolde = when (typeTransaction) {
            TypeTransaction.Depense -> -montant  // Dépense = soustraction
            TypeTransaction.Revenu -> montant    // Revenu = addition
            TypeTransaction.Pret -> -montant     // Prêt = soustraction
            TypeTransaction.RemboursementRecu -> montant  // Remboursement reçu = addition
            TypeTransaction.Emprunt -> montant   // Emprunt = addition
            TypeTransaction.RemboursementDonne -> -montant // Remboursement donné = soustraction
            TypeTransaction.Paiement -> -montant // Paiement = soustraction
            TypeTransaction.TransfertSortant -> -montant  // Transfert sortant = soustraction
            TypeTransaction.TransfertEntrant -> montant   // Transfert entrant = addition
        }

        val mettreAJourPretAPlacer = when (typeTransaction) {
            TypeTransaction.Revenu -> true
            TypeTransaction.RemboursementRecu -> true
            TypeTransaction.Emprunt -> true
            TypeTransaction.TransfertEntrant -> true
            else -> false
        }

        return compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
            compteId, 
            collectionCompte, 
            variationSolde,
            mettreAJourPretAPlacer
        )
    }

    /**
     * Met à jour le solde d'une enveloppe.
     */
    private suspend fun mettreAJourSoldeEnveloppe(
        allocationMensuelleId: String,
        montant: Double
    ): Result<Unit> {
        return enveloppeRepository.ajouterDepenseAllocation(allocationMensuelleId, montant)
    }

    /**
     * Annule une dépense sur une enveloppe.
     */
    private suspend fun annulerDepenseEnveloppe(
        allocationMensuelleId: String,
        montant: Double
    ): Result<Unit> {
        return enveloppeRepository.annulerDepenseAllocation(allocationMensuelleId, montant)
    }

    /**
     * Obtient l'allocation mensuelle existante ou en crée une nouvelle si nécessaire.
     * UNE SEULE allocation par enveloppe par mois.
     */
    private suspend fun obtenirOuCreerAllocationMensuelle(enveloppeId: String, premierJourMois: Date, compteId: String, collectionCompte: String): Result<String> {
        return try {

            // Chercher l'allocation existante pour ce mois via EnveloppeRepository
            val allocationsExistantes = enveloppeRepository.recupererAllocationsPourMois(premierJourMois)
            val allocationExistante = allocationsExistantes.getOrNull()?.find { allocation -> allocation.enveloppeId == enveloppeId }

            if (allocationExistante != null) {
                Result.success(allocationExistante.id)
            } else {
                creerNouvelleAllocation(enveloppeId, premierJourMois, compteId, collectionCompte)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Crée une nouvelle allocation mensuelle avec les bonnes données.
     */
    private suspend fun creerNouvelleAllocation(enveloppeId: String, premierJourMois: Date, compteId: String, collectionCompte: String): Result<String> {
        // Récupérer l'utilisateur connecté
        val utilisateurId = try {
            com.xburnsx.toutiebudget.di.PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: return Result.failure(Exception("Utilisateur non connecté"))
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val nouvelleAllocation = AllocationMensuelle(
            id = "",
            utilisateurId = utilisateurId,
            enveloppeId = enveloppeId,
            mois = premierJourMois,
            solde = 0.0,
            alloue = 0.0,
            depense = 0.0,
            compteSourceId = compteId,
            collectionCompteSource = collectionCompte
        )
        return try {
            val allocationCreee = allocationMensuelleRepository.creerNouvelleAllocation(nouvelleAllocation)
            Result.success(allocationCreee.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 