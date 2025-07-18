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
                println("[DEBUG] EnregistrerTransactionUseCase: début - montant=$montant, type=$typeTransaction, enveloppeId=$enveloppeId")
                
                // 1. Récupérer l'allocation mensuelle si c'est une dépense
                var allocationMensuelleId: String? = null
                if (typeTransaction == TypeTransaction.Depense && !enveloppeId.isNullOrBlank()) {
                    println("[DEBUG] Création/récupération allocation mensuelle pour enveloppeId=$enveloppeId")
                    val resultAllocation = obtenirOuCreerAllocationMensuelle(enveloppeId, date)
                    if (resultAllocation.isFailure) {
                        println("[DEBUG] Erreur allocation mensuelle: ${resultAllocation.exceptionOrNull()?.message}")
                        throw resultAllocation.exceptionOrNull() ?: Exception("Erreur lors de la récupération de l'allocation")
                    }
                    allocationMensuelleId = resultAllocation.getOrNull()
                    println("[DEBUG] Allocation mensuelle créée/récupérée: $allocationMensuelleId")
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

                println("[DEBUG] Création transaction avec allocationMensuelleId=$allocationMensuelleId")
                val resultTransaction = transactionRepository.creerTransaction(transaction)
                if (resultTransaction.isFailure) {
                    println("[DEBUG] Erreur création transaction: ${resultTransaction.exceptionOrNull()?.message}")
                    throw resultTransaction.exceptionOrNull() ?: Exception("Erreur lors de la création de la transaction")
                }
                println("[DEBUG] Transaction créée avec succès")

                // 3. Mettre à jour les soldes en parallèle
                val tachesMiseAJour = listOf(
                    async { 
                        println("[DEBUG] Mise à jour solde compte")
                        mettreAJourSoldeCompte(compteId, collectionCompte, typeTransaction, montant) 
                    },
                    async { 
                        if (!allocationMensuelleId.isNullOrBlank()) {
                            println("[DEBUG] Mise à jour solde enveloppe avec allocationId=$allocationMensuelleId, montant=$montant")
                            mettreAJourSoldeEnveloppe(allocationMensuelleId, montant)
                        } else {
                            println("[DEBUG] Pas de mise à jour enveloppe (allocationId null)")
                            Result.success(Unit)
                        }
                    }
                )

                val resultats = tachesMiseAJour.awaitAll()
                
                // Vérifier que toutes les mises à jour ont réussi
                resultats.forEach { resultat ->
                    if (resultat.isFailure) {
                        println("[DEBUG] Erreur mise à jour soldes: ${resultat.exceptionOrNull()?.message}")
                        throw resultat.exceptionOrNull() ?: Exception("Erreur lors de la mise à jour des soldes")
                    }
                }
                println("[DEBUG] EnregistrerTransactionUseCase: succès complet")

                Result.success(Unit)
            }
        } catch (e: Exception) {
            println("[DEBUG] EnregistrerTransactionUseCase: erreur - ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Crée TOUJOURS une nouvelle allocation mensuelle pour chaque action.
     * Architecture : Chaque action = NOUVELLE allocation (pas de modification)
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

        // TOUJOURS créer une nouvelle allocation (principe de l'architecture)
        return creerNouvelleAllocation(enveloppeId, premierJourMois)
    }

    /**
     * Crée une nouvelle allocation mensuelle.
     */
    private suspend fun creerNouvelleAllocation(enveloppeId: String, premierJourMois: Date): Result<String> {
        println("[DEBUG] creerNouvelleAllocation - Création pour enveloppeId: $enveloppeId")

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
        
        println("[DEBUG] creerNouvelleAllocation - Allocation à créer: enveloppeId=${nouvelleAllocation.enveloppeId}")

        return try {
            val allocationCreee = allocationMensuelleRepository.creerNouvelleAllocation(nouvelleAllocation)
            println("[DEBUG] creerNouvelleAllocation - Allocation créée: ID=${allocationCreee.id}, enveloppeId=${allocationCreee.enveloppeId}")
            Result.success(allocationCreee.id)
        } catch (e: Exception) {
            println("[DEBUG] creerNouvelleAllocation - Erreur: ${e.message}")
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

        return compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
            compteId,
            collectionCompte,
            variationSolde,
            mettreAJourPretAPlacer
        )
    }

    /**
     * Met à jour le solde d'une enveloppe (allocation mensuelle).
     * Pour une dépense, soustrait le montant du solde et l'ajoute aux dépenses.
     */
    private suspend fun mettreAJourSoldeEnveloppe(allocationMensuelleId: String, montant: Double): Result<Unit> {
        return enveloppeRepository.ajouterDepenseAllocation(allocationMensuelleId, montant)
    }
}