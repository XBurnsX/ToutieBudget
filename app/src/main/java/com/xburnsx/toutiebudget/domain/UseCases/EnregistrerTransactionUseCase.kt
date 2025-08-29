// chemin/simule: /domain/usecases/EnregistrerTransactionUseCase.kt
// Dépendances: TransactionRepository, CompteRepository, EnveloppeRepository, Transaction, TypeTransaction

package com.xburnsx.toutiebudget.domain.usecases

import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.*
import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Calendar
import java.util.Date
import com.xburnsx.toutiebudget.utils.MoneyFormatter


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
     * @param tiersUtiliser Nom du tiers utilisé dans la transaction
     * @param note Note facultative
     * @param date Date de la transaction (par défaut: maintenant)
     * @param estFractionnee Si la transaction est fractionnée
     * @param sousItems JSON des sous-items pour les transactions fractionnées
     * 
     * @return Result indiquant le succès ou l'échec avec l'exception
     */
    suspend fun executer(
        typeTransaction: TypeTransaction,
        montant: Double,
        compteId: String,
        collectionCompte: String,
        enveloppeId: String? = null,
        tiersUtiliser: String? = null,
        note: String? = null,
        date: Date = Date(),
        estFractionnee: Boolean = false,
        sousItems: String? = null
    ): Result<Unit> {
        
        if (montant <= 0) {
            return Result.failure(Exception("Le montant doit être positif"))
        }

        return try {
            coroutineScope {

                // 1. Obtenir ou créer l'allocation mensuelle si c'est une dépense
                var allocationMensuelleId: String? = null
                if (typeTransaction == TypeTransaction.Depense && !enveloppeId.isNullOrBlank()) {

                    // IMPORTANT: Les allocations mensuelles utilisent la date de la transaction
                    // pour déterminer le mois approprié
                    val calendrier = Calendar.getInstance().apply {
                        time = date // Utiliser la date de la transaction sélectionnée par l'utilisateur
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
                
                // 🔍 LOGS DEBUG : Vérifier l'allocation
                println("DEBUG: allocationMensuelleId = $allocationMensuelleId")
                println("DEBUG: enveloppeId = $enveloppeId")
                
                val transaction = Transaction(
                    type = typeTransaction,
                    // Forcer arrondi exact
                    montant = MoneyFormatter.roundAmount(montant),
                    date = date, // Date sélectionnée par l'utilisateur
                    note = note,
                    compteId = compteId,
                    collectionCompte = collectionCompte,
                    allocationMensuelleId = allocationMensuelleId, // Basé sur le mois actuel
                    tiersUtiliser = tiersUtiliser,
                    estFractionnee = estFractionnee,
                    sousItems = sousItems
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
                            mettreAJourSoldeEnveloppe(allocationMensuelleId, montant, collectionCompte)
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
            return Result.failure(e)
        }
    }

    /**
     * Obtient l'allocation mensuelle existante ou en crée une nouvelle si nécessaire.
     * UNE SEULE allocation par enveloppe par mois.
     */
    private suspend fun obtenirOuCreerAllocationMensuelle(enveloppeId: String, premierJourMois: Date, compteId: String, collectionCompte: String): Result<String> {
        return try {

            // 🚨 CORRECTION : Utiliser directement le repository Room au lieu de Pocketbase !
            // Cela évite les problèmes de synchronisation et assure que l'allocation est trouvée
            val allocationExistante = allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeId, premierJourMois)

            if (allocationExistante.id.isNotBlank()) {
                // 🎯 Mettre à jour le compte source si pas déjà défini
                if (allocationExistante.compteSourceId == null) {
                    allocationMensuelleRepository.mettreAJourCompteSource(
                        allocationExistante.id,
                        compteId,
                        collectionCompte
                    )
                }
                Result.success(allocationExistante.id)
            } else {
                // Fallback : créer une nouvelle allocation
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
            utilisateurId = PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: "",
            enveloppeId = enveloppeId,
            mois = premierJourMois,
            solde = 0.0, // Solde initial à 0
            alloue = 0.0,
            depense = 0.0, // Dépense initiale à 0
            compteSourceId = compteId,
            collectionCompteSource = collectionCompte
        )
        
        return try {
            val allocationCreee = allocationMensuelleRepository.creerNouvelleAllocation(nouvelleAllocation)
            
            // 🔍 LOGS DEBUG : Vérifier l'allocation créée
            println("DEBUG: Allocation créée avec ID = ${allocationCreee.id}")
            println("DEBUG: Allocation créée avec enveloppeId = ${allocationCreee.enveloppeId}")
            
            Result.success(allocationCreee.id)
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
            TypeTransaction.Paiement -> montant   // Paiement = addition (pour rapprocher de zéro)
            TypeTransaction.PaiementEffectue -> -montant  // PaiementEffectue = soustraction (argent qui sort)
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
    private suspend fun mettreAJourSoldeEnveloppe(
        allocationMensuelleId: String,
        montant: Double,
        collectionCompte: String
    ): Result<Unit> {
        return try {
            println("🔍 DEBUG - mettreAJourSoldeEnveloppe appelé avec:")
            println("🔍 DEBUG - allocationMensuelleId: $allocationMensuelleId")
            println("🔍 DEBUG - montant: $montant")
            println("🔍 DEBUG - collectionCompte: $collectionCompte")

            if (collectionCompte == "comptes_credits") {
                // Carte de crédit: ne PAS toucher au solde d'allocation, seulement depense et alloue
                println("🔍 DEBUG - Compte de crédit détecté, mise à jour spéciale")
                val allocation = enveloppeRepository.recupererAllocationParId(allocationMensuelleId).getOrThrow()
                val allocationMaj = allocation.copy(
                    depense = MoneyFormatter.roundAmount(allocation.depense + montant),
                    alloue = MoneyFormatter.roundAmount(allocation.alloue + montant),
                    solde = MoneyFormatter.roundAmount(allocation.solde)
                )
                println("🔍 DEBUG - Allocation carte de crédit mise à jour:")
                println("🔍 DEBUG - Ancienne depense: ${allocation.depense} -> Nouvelle: ${allocationMaj.depense}")
                println("🔍 DEBUG - Ancien alloue: ${allocation.alloue} -> Nouveau: ${allocationMaj.alloue}")
                println("🔍 DEBUG - Solde inchangé: ${allocationMaj.solde}")
                
                allocationMensuelleRepository.mettreAJourAllocation(allocationMaj)
                Result.success(Unit)
            } else {
                // Comportement normal: depense += montant et solde -= montant
                println("🔍 DEBUG - Compte normal, appel de ajouterDepenseAllocation")
                val result = enveloppeRepository.ajouterDepenseAllocation(allocationMensuelleId, montant)
                if (result.isSuccess) {
                    Result.success(Unit)
                } else {
                    Result.failure(result.exceptionOrNull() ?: Exception("Erreur lors de la mise à jour de l'allocation"))
                }
            }
        } catch (e: Exception) {
            println("🔍 DEBUG - Erreur dans mettreAJourSoldeEnveloppe: ${e.message}")
            Result.failure(e)
        }
    }
}