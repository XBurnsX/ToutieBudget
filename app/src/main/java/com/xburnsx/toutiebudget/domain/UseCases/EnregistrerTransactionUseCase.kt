// chemin/simule: /domain/usecases/EnregistrerTransactionUseCase.kt
// Dépendances: TransactionRepository, CompteRepository, EnveloppeRepository, Transaction, TypeTransaction

package com.xburnsx.toutiebudget.domain.usecases

import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.*
import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.utils.toutOuRien
import java.util.Calendar
import java.util.Date
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import com.xburnsx.toutiebudget.utils.OperationAtomique
import com.xburnsx.toutiebudget.utils.OperationAtomiqueDirecte


/**
 * Use case pour enregistrer une transaction et mettre à jour les soldes correspondants.
 * Gère la logique métier complète de création d'une transaction.
 */
class EnregistrerTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository,
    private val historiqueAllocationService: com.xburnsx.toutiebudget.domain.services.HistoriqueAllocationService,
    private val operationAtomique: OperationAtomique = OperationAtomiqueDirecte
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
        sousItems: String? = null,
        id: String? = null // MT1 : réutiliser l'ID d'une transaction modifiée
    ): Result<Unit> {
        
        if (montant <= 0) {
            return Result.failure(Exception("Le montant doit être positif"))
        }

        return try {
            // AT4 : transaction, compte et enveloppe réussissent ensemble ou sont tous défaits
            operationAtomique.executer {
              toutOuRien {

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
                // DEBUG: allocationMensuelleId = $allocationMensuelleId
                // DEBUG: enveloppeId = $enveloppeId
                
                val transaction = Transaction(
                    id = id ?: "",
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
                val idCree = resultTransaction.getOrThrow().id
                siEchec { transactionRepository.supprimerTransaction(idCree) }

                // 📝 ENREGISTRER DANS L'HISTORIQUE
                try {
                    val compte = compteRepository.getCompteById(compteId, collectionCompte)
                    val enveloppe = if (!allocationMensuelleId.isNullOrBlank()) {
                        enveloppeRepository.recupererToutesLesEnveloppes().getOrNull()?.find { it.id == enveloppeId }
                    } else null
                    
                    if (compte is CompteCheque) {
                        android.util.Log.d("ToutieBudget", "🔄 ENREGISTRER_TRANSACTION_USE_CASE : Tentative d'enregistrement dans l'historique pour transaction ${typeTransaction}")
                        historiqueAllocationService.enregistrerTransactionDirecte(
                            compte = compte,
                            enveloppe = enveloppe,
                            typeTransaction = typeTransaction.name,
                            montant = when (typeTransaction) {
                                TypeTransaction.Depense, TypeTransaction.Pret, TypeTransaction.RemboursementDonne, TypeTransaction.PaiementEffectue, TypeTransaction.TransfertSortant -> -montant
                                TypeTransaction.Revenu, TypeTransaction.RemboursementRecu, TypeTransaction.Emprunt, TypeTransaction.Paiement, TypeTransaction.TransfertEntrant -> montant
                            },
                            soldeAvant = compte.solde,
                            soldeApres = compte.solde + when (typeTransaction) {
                                TypeTransaction.Depense, TypeTransaction.Pret, TypeTransaction.RemboursementDonne, TypeTransaction.PaiementEffectue, TypeTransaction.TransfertSortant -> -montant
                                TypeTransaction.Revenu, TypeTransaction.RemboursementRecu, TypeTransaction.Emprunt, TypeTransaction.Paiement, TypeTransaction.TransfertEntrant -> montant
                            },
                            pretAPlacerAvant = compte.pretAPlacer,
                            pretAPlacerApres = compte.pretAPlacer, // Le prêt à placer ne change pas pour les transactions directes
                            note = note
                        )
                        android.util.Log.d("ToutieBudget", "✅ ENREGISTRER_TRANSACTION_USE_CASE : Enregistrement dans l'historique réussi (transaction ${typeTransaction})")
                        
                        // 📝 ENREGISTRER L'HISTORIQUE POUR CHAQUE FRACTION SI TRANSACTION FRACTIONNÉE
                        if (estFractionnee && !sousItems.isNullOrBlank()) {
                            try {
                                android.util.Log.d("ToutieBudget", "🔄 ENREGISTRER_TRANSACTION_USE_CASE : Traitement des fractions pour transaction fractionnée")
                                val gson = com.google.gson.Gson()
                                val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
                                val fractions = gson.fromJson<List<Map<String, Any>>>(sousItems, type)
                                
                                for ((index, fraction) in fractions.withIndex()) {
                                    val enveloppeIdFraction = fraction["enveloppe_id"] as? String
                                    val montantFraction = (fraction["montant"] as? Double) ?: 0.0
                                    
                                    if (enveloppeIdFraction != null && montantFraction > 0) {
                                        val enveloppeFraction = enveloppeRepository.recupererToutesLesEnveloppes().getOrNull()?.find { it.id == enveloppeIdFraction }
                                        
                                        if (enveloppeFraction != null) {
                                            android.util.Log.d("ToutieBudget", "🔄 ENREGISTRER_TRANSACTION_USE_CASE : Enregistrement historique pour fraction ${index + 1}: ${enveloppeFraction.nom} - ${String.format("%.2f", montantFraction)}$")
                                            historiqueAllocationService.enregistrerTransactionDirecte(
                                                compte = compte,
                                                enveloppe = enveloppeFraction,
                                                typeTransaction = "FRACTION_${typeTransaction.name}",
                                                montant = -montantFraction, // Négatif car c'est une dépense
                                                soldeAvant = compte.solde,
                                                soldeApres = compte.solde, // Le solde ne change pas pour les fractions
                                                pretAPlacerAvant = compte.pretAPlacer,
                                                pretAPlacerApres = compte.pretAPlacer, // Le prêt à placer ne change pas pour les fractions
                                                note = "Fraction ${index + 1} de transaction fractionnée"
                                            )
                                        }
                                    }
                                }
                                android.util.Log.d("ToutieBudget", "✅ ENREGISTRER_TRANSACTION_USE_CASE : Enregistrement historique des fractions réussi")
                            } catch (e: Exception) {
                                android.util.Log.e("ToutieBudget", "❌ ENREGISTRER_TRANSACTION_USE_CASE : Erreur lors de l'enregistrement de l'historique des fractions: ${e.message}")
                            }
                        }
                    } else {
                        android.util.Log.d("ToutieBudget", "ℹ️ ENREGISTRER_TRANSACTION_USE_CASE : Compte n'est pas un CompteCheque, pas d'enregistrement dans l'historique (transaction ${typeTransaction})")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ToutieBudget", "❌ ENREGISTRER_TRANSACTION_USE_CASE : Erreur lors de l'enregistrement dans l'historique (transaction ${typeTransaction}): ${e.message}")
                }

                // 3. Mettre à jour les soldes, l'un après l'autre pour pouvoir défaire le premier si le second échoue
                mettreAJourSoldeCompte(compteId, collectionCompte, typeTransaction, montant).getOrThrow()
                siEchec { mettreAJourSoldeCompte(compteId, collectionCompte, typeTransaction, montant, sens = -1.0).getOrThrow() }
                if (!allocationMensuelleId.isNullOrBlank()) {
                    mettreAJourSoldeEnveloppe(allocationMensuelleId, montant, collectionCompte).getOrThrow()
                }

                // 🔄 DÉCLENCHER EXPLICITEMENT LE RAFRAÎCHISSEMENT DE L'INTERFACE
                BudgetEvents.refreshManual()

                Result.success(Unit)
              }
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
            // DEBUG: Allocation créée avec ID = ${allocationCreee.id}
            // DEBUG: Allocation créée avec enveloppeId = ${allocationCreee.enveloppeId}
            
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
        montant: Double,
        sens: Double = 1.0 // -1 : défait l'effet
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
            variationSolde * sens,
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
            // 🔍 DEBUG - mettreAJourSoldeEnveloppe appelé avec:
            // 🔍 DEBUG - allocationMensuelleId: $allocationMensuelleId
            // 🔍 DEBUG - montant: $montant
            // 🔍 DEBUG - collectionCompte: $collectionCompte

            if (collectionCompte == "comptes_credits") {
                // Carte de crédit: ne PAS toucher au solde d'allocation, seulement depense et alloue
                // 🔍 DEBUG - Compte de crédit détecté, mise à jour spéciale
                val allocation = enveloppeRepository.recupererAllocationParId(allocationMensuelleId).getOrThrow()
                val allocationMaj = allocation.copy(
                    depense = MoneyFormatter.roundAmount(allocation.depense + montant),
                    alloue = MoneyFormatter.roundAmount(allocation.alloue + montant),
                    solde = MoneyFormatter.roundAmount(allocation.solde)
                )
                // 🔍 DEBUG - Allocation carte de crédit mise à jour:
                // 🔍 DEBUG - Ancienne depense: ${allocation.depense} -> Nouvelle: ${allocationMaj.depense}
                // 🔍 DEBUG - Ancien alloue: ${allocation.alloue} -> Nouveau: ${allocationMaj.alloue}
                // 🔍 DEBUG - Solde inchangé: ${allocationMaj.solde}
                
                allocationMensuelleRepository.mettreAJourAllocation(allocationMaj)
                Result.success(Unit)
            } else {
                // Comportement normal: depense += montant et solde -= montant
                // 🔍 DEBUG - Compte normal, appel de ajouterDepenseAllocation
                val result = enveloppeRepository.ajouterDepenseAllocation(allocationMensuelleId, montant)
                if (result.isSuccess) {
                    Result.success(Unit)
                } else {
                    Result.failure(result.exceptionOrNull() ?: Exception("Erreur lors de la mise à jour de l'allocation"))
                }
            }
        } catch (e: Exception) {
            // 🔍 DEBUG - Erreur dans mettreAJourSoldeEnveloppe: ${e.message}
            Result.failure(e)
        }
    }
}
