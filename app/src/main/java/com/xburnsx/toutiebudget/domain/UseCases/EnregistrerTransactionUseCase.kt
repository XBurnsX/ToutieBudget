// chemin/simule: /domain/usecases/EnregistrerTransactionUseCase.kt
// Dépendances: TransactionRepository, CompteRepository, EnveloppeRepository, Transaction, TypeTransaction

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
 * Use case pour enregistrer une transaction et mettre à jour les soldes correspondants.
 * Gère la logique métier complète de création d'une transaction.
 */
class EnregistrerTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository
) {

    /**
     * Enregistre une transaction complète avec mise à jour des soldes.
     * 
     * @param typeTransaction Type de transaction (Dépense/Revenu)
     * @param montant Montant de la transaction
     * @param compteId ID du compte concerné
     * @param collectionCompte Collection du compte (ex: "comptes_cheque")
     * @param enveloppeId ID de l'enveloppe (pour les dépenses)
     * @param tiersNom Nom du tiers associé à la transaction
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
        tiersNom: String? = null,
        note: String? = null,
        date: Date = Date()
    ): Result<Unit> {
        
        if (montant <= 0) {
            return Result.failure(Exception("Le montant doit être positif"))
        }

        return try {
            coroutineScope {

                // 1. Obtenir ou créer l'allocation mensuelle si c'est une dépense
                var allocationMensuelleId: String? = null
                if (typeTransaction == TypeTransaction.Depense && !enveloppeId.isNullOrBlank()) {

                    // IMPORTANT: Les allocations mensuelles utilisent TOUJOURS le mois actuel
                    // même si la transaction a une date différente
                    val calendrier = Calendar.getInstance().apply {
                        time = Date() // Utiliser la date actuelle, pas la date de la transaction
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

                // 2. Créer la transaction avec la date sélectionnée par l'utilisateur
                // Note: La date de la transaction peut être différente de la date actuelle
                // mais les allocations mensuelles restent toujours basées sur le mois actuel
                val transaction = Transaction(
                    type = typeTransaction,
                    montant = montant,
                    date = date, // Date sélectionnée par l'utilisateur
                    note = note,
                    compteId = compteId,
                    collectionCompte = collectionCompte,
                    allocationMensuelleId = allocationMensuelleId, // Basé sur le mois actuel
                    tiers = tiersNom
                )

                val resultTransaction = transactionRepository.creerTransaction(transaction)
                if (resultTransaction.isFailure) {
                    throw resultTransaction.exceptionOrNull() ?: Exception("Erreur lors de la création de la transaction")
                }

                // 3. Mettre à jour les soldes en parallèle
                val tachesMiseAJour = listOf(
                    async { 
                        mettreAJourSoldeCompte(compteId, collectionCompte, typeTransaction, montant) 
                    },
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

                // 🔄 DÉCLENCHER EXPLICITEMENT LE RAFRAÎCHISSEMENT DE L'INTERFACE
                BudgetEvents.refreshManual()

                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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

        val nouvelleAllocation = AllocationMensuelle(
            id = "",
            utilisateurId = "",
            enveloppeId = enveloppeId,
            mois = premierJourMois,
            solde = 0.0, // Solde initial à 0
            alloue = 0.0,
            depense = 0.0, // Dépense initiale à 0
            compteSourceId = compteId,
            collectionCompteSource = collectionCompte
        )
        
        return try {
            val resultAllocation = enveloppeRepository.creerAllocationMensuelle(nouvelleAllocation)
            resultAllocation.fold(
                onSuccess = { allocationCreee ->
                    Result.success(allocationCreee.id)
                },
                onFailure = { erreur ->
                    Result.failure(erreur)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
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
            TypeTransaction.RemboursementRecu -> montant  // Remboursement reçu = addition
            TypeTransaction.Emprunt -> montant    // Emprunt reçu = addition
            TypeTransaction.RemboursementDonne -> -montant // Remboursement donné = soustraction
            TypeTransaction.Paiement -> -montant  // Paiement = soustraction
            TypeTransaction.TransfertSortant -> -montant  // Transfert sortant = soustraction
            TypeTransaction.TransfertEntrant -> montant   // Transfert entrant = addition
        }

        // Déterminer si on doit aussi mettre à jour le "prêt à placer"
        // Seulement pour les transactions qui ajoutent de l'argent au compte
        val mettreAJourPretAPlacer = when (typeTransaction) {
            TypeTransaction.Revenu -> true              // Revenu = argent qui arrive
            TypeTransaction.RemboursementRecu -> true   // Remboursement reçu = argent qui arrive
            TypeTransaction.Emprunt -> true             // Emprunt = argent qui arrive
            TypeTransaction.TransfertEntrant -> true    // Transfert entrant = argent qui arrive
            // Pour les dépenses, prêts, paiements, etc. : le solde diminue mais pas le prêt à placer
            else -> false
        }

        val resultat = compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
            compteId,
            collectionCompte,
            variationSolde,
            mettreAJourPretAPlacer
        )

        return resultat
    }

    /**
     * Met à jour le solde d'une enveloppe (allocation mensuelle).
     * Pour une dépense, soustrait le montant du solde et l'ajoute aux dépenses.
     */
    private suspend fun mettreAJourSoldeEnveloppe(allocationMensuelleId: String, montant: Double): Result<Unit> {
        return enveloppeRepository.ajouterDepenseAllocation(allocationMensuelleId, montant)
    }
}