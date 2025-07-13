// chemin/simule: /domain/usecases/EnregistrerTransactionUseCase.kt
// Dépendances: TransactionRepository, CompteRepository, EnveloppeRepository, Transaction, TypeTransaction

package com.xburnsx.toutiebudget.domain.usecases

import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Calendar
import java.util.Date

/**
 * Use case pour enregistrer une transaction et mettre à jour les soldes correspondants.
 * Gère la logique métier complète de création d'une transaction.
 */
class EnregistrerTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository
) {

    /**
     * Enregistre une transaction complète avec mise à jour des soldes.
     * 
     * @param typeTransaction Type de transaction (Dépense/Revenu)
     * @param montant Montant de la transaction
     * @param compteId ID du compte concerné
     * @param collectionCompte Collection du compte (ex: "comptes_cheque")
     * @param enveloppeId ID de l'enveloppe (pour les dépenses)
     * @param note Note facultative
     * @param date Date de la transaction (par défaut: maintenant)
     * 
     * @return Result indiquant le succès ou l'échec avec l'exception
     */
    suspend fun executer(
        typeTransaction: TypeTransaction,
        montant: Double,
        compteId: String,
        collectionCompte: String,
        enveloppeId: String? = null,
        note: String? = null,
        date: Date = Date()
    ): Result<Unit> {
        
        if (montant <= 0) {
            return Result.failure(Exception("Le montant doit être positif"))
        }

        return try {
            coroutineScope {
                // 1. Récupérer l'allocation mensuelle si c'est une dépense
                var allocationMensuelleId: String? = null
                if (typeTransaction == TypeTransaction.Depense && !enveloppeId.isNullOrBlank()) {
                    val resultAllocation = obtenirOuCreerAllocationMensuelle(enveloppeId, date)
                    if (resultAllocation.isFailure) {
                        throw resultAllocation.exceptionOrNull() ?: Exception("Erreur lors de la récupération de l'allocation")
                    }
                    allocationMensuelleId = resultAllocation.getOrNull()
                }

                // 2. Créer la transaction
                val transaction = Transaction(
                    type = typeTransaction,
                    montant = montant,
                    date = date,
                    note = note,
                    compteId = compteId,
                    collectionCompte = collectionCompte,
                    allocationMensuelleId = allocationMensuelleId
                )

                val resultTransaction = transactionRepository.creerTransaction(transaction)
                if (resultTransaction.isFailure) {
                    throw resultTransaction.exceptionOrNull() ?: Exception("Erreur lors de la création de la transaction")
                }

                // 3. Mettre à jour les soldes en parallèle
                val tachesMiseAJour = listOf(
                    async { mettreAJourSoldeCompte(compteId, collectionCompte, typeTransaction, montant) },
                    async { 
                        if (!allocationMensuelleId.isNullOrBlank()) {
                            mettreAJourSoldeEnveloppe(allocationMensuelleId, montant)
                        } else {
                            Result.success(Unit)
                        }
                    }
                )

                val resultats = tachesMiseAJour.awaitAll()
                
                // Vérifier que toutes les mises à jour ont réussi
                resultats.forEach { resultat ->
                    if (resultat.isFailure) {
                        throw resultat.exceptionOrNull() ?: Exception("Erreur lors de la mise à jour des soldes")
                    }
                }

                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Récupère ou crée l'allocation mensuelle pour une enveloppe donnée.
     */
    private suspend fun obtenirOuCreerAllocationMensuelle(enveloppeId: String, date: Date): Result<String> {
        // Calculer le premier jour du mois
        val calendrier = Calendar.getInstance().apply { 
            time = date
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val premierJourMois = calendrier.time

        // Essayer de récupérer l'allocation existante
        val resultAllocation = enveloppeRepository.recupererAllocationMensuelle(enveloppeId, premierJourMois)
        
        return if (resultAllocation.isSuccess) {
            val allocation = resultAllocation.getOrNull()
            if (allocation != null) {
                Result.success(allocation.id)
            } else {
                // Créer une nouvelle allocation
                creerNouvelleAllocation(enveloppeId, premierJourMois)
            }
        } else {
            // En cas d'erreur, essayer de créer une nouvelle allocation
            creerNouvelleAllocation(enveloppeId, premierJourMois)
        }
    }

    /**
     * Crée une nouvelle allocation mensuelle.
     */
    private suspend fun creerNouvelleAllocation(enveloppeId: String, premierJourMois: Date): Result<String> {
        val nouvelleAllocation = AllocationMensuelle(
            id = "",
            utilisateurId = "",
            enveloppeId = enveloppeId,
            mois = premierJourMois,
            solde = 0.0,
            alloue = 0.0,
            depense = 0.0,
            compteSourceId = null,
            collectionCompteSource = null
        )
        
        return enveloppeRepository.creerAllocationMensuelle(nouvelleAllocation)
            .map { it.id }
    }

    /**
     * Met à jour le solde d'un compte selon le type de transaction.
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
            TypeTransaction.Revenu -> montant     // Revenu = addition
            TypeTransaction.Pret -> -montant      // Prêt accordé = soustraction
            TypeTransaction.Emprunt -> montant    // Emprunt reçu = addition
        }
        
        return compteRepository.mettreAJourSoldeAvecVariation(compteId, collectionCompte, variationSolde)
    }

    /**
     * Met à jour le solde d'une enveloppe (allocation mensuelle).
     * Pour une dépense, soustrait le montant du solde et l'ajoute aux dépenses.
     */
    private suspend fun mettreAJourSoldeEnveloppe(allocationMensuelleId: String, montant: Double): Result<Unit> {
        return enveloppeRepository.ajouterDepenseAllocation(allocationMensuelleId, montant)
    }
}