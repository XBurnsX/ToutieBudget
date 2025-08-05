package com.xburnsx.toutiebudget.domain.usecases

import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.*
import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Use case pour supprimer une transaction existante.
 * Gère la logique métier complète de suppression d'une transaction.
 */
class SupprimerTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository
) {

    /**
     * Supprime une transaction existante avec mise à jour des soldes.
     * 
     * @param transactionId ID de la transaction à supprimer
     * 
     * @return Result indiquant le succès ou l'échec avec l'exception
     */
    suspend fun executer(transactionId: String): Result<Unit> {
        return try {
            coroutineScope {

                // 1. Récupérer la transaction existante
                val transaction = transactionRepository.recupererTransactionParId(transactionId)
                    .getOrNull() ?: throw Exception("Transaction non trouvée")

                // 2. Mettre à jour les soldes en parallèle
                val tachesMiseAJour = mutableListOf<kotlinx.coroutines.Deferred<Result<Unit>>>()

                // Annuler l'effet de la transaction sur le compte
                tachesMiseAJour.add(async { 
                    annulerTransactionCompte(
                        transaction.compteId,
                        transaction.collectionCompte,
                        transaction.type,
                        transaction.montant
                    )
                })

                // Gérer les allocations selon le type de transaction
                tachesMiseAJour.add(async { 
                    if (transaction.estFractionnee && transaction.sousItems != null) {
                        // Transaction fractionnée : rembourser toutes les allocations des fractions
                        rembourserTransactionFractionnee(transaction.sousItems)
                    } else if (transaction.allocationMensuelleId != null && transaction.type == TypeTransaction.Depense) {
                        // Transaction normale : rembourser l'allocation normale
                        enveloppeRepository.annulerDepenseAllocation(transaction.allocationMensuelleId, transaction.montant)
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

                // 3. Supprimer la transaction
                val resultSuppression = transactionRepository.supprimerTransaction(transactionId)
                if (resultSuppression.isFailure) {
                    throw resultSuppression.exceptionOrNull() ?: Exception("Erreur lors de la suppression de la transaction")
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
     * Rembourse toutes les allocations d'une transaction fractionnée.
     */
    private suspend fun rembourserTransactionFractionnee(sousItemsJson: String): Result<Unit> {
        return try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
            val sousItems = gson.fromJson<List<Map<String, Any>>>(sousItemsJson, type)
            
            // Rembourser chaque fraction
            for (sousItem in sousItems) {
                val allocationId = sousItem["allocation_mensuelle_id"] as String
                val montant = (sousItem["montant"] as Double)
                
                // Récupérer l'allocation
                val allocation = allocationMensuelleRepository.getAllocationById(allocationId)
                if (allocation != null) {
                    // Rembourser en soustrayant le montant (inverse de l'ajout)
                    val allocationRemboursee = allocation.copy(
                        depense = allocation.depense - montant,
                        solde = allocation.solde + montant
                    )
                    allocationMensuelleRepository.mettreAJourAllocation(allocationRemboursee)
                }
            }
            
            Result.success(Unit)
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
} 