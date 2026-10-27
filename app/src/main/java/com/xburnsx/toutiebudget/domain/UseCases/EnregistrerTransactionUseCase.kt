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
import com.xburnsx.toutiebudget.utils.OperationAtomiqueRoom


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
        id: String? = null, // MT1 : réutiliser l'ID d'une transaction modifiée
        verifierIdempotence: Boolean = false // AT4/AT14 : rejouer une création sans débiter deux fois
    ): Result<Unit> {
        
        if (montant <= 0) {
            return Result.failure(Exception("Le montant doit être positif"))
        }

        return try {
            // AT4 : la notification ne part QU'APRÈS le commit Room.
            val resultat = operationAtomique.executer {
              toutOuRien {

                // AT4 / AT14 : l'ID du brouillon est une clé de création unique.
                // La vérification se fait DANS la transaction Room, avant toute
                // variation de solde. MT1 (édition) laisse ce contrôle désactivé.
                if (verifierIdempotence) {
                    val operationId = id?.takeIf { it.isNotBlank() }
                        ?: throw IllegalArgumentException("Identifiant d'opération manquant.")
                    val recherche = transactionRepository.recupererTransactionParId(operationId)
                    // Une indisponibilite Room ne signifie PAS « transaction absente ».
                    if (recherche.isFailure && recherche.exceptionOrNull()?.message != "Transaction non trouvée") {
                        throw recherche.exceptionOrNull() ?: IllegalStateException("Verification du doublon impossible.")
                    }
                    val existante = recherche.getOrNull()
                    if (existante != null) {
                        val jourIdentique = existante.date.toInstant()
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate() ==
                            date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        val enveloppeIdentique = if (typeTransaction == TypeTransaction.Depense && !estFractionnee) {
                            existante.allocationMensuelleId?.let { allocationMensuelleRepository.getAllocationById(it) }
                                ?.enveloppeId == enveloppeId
                        } else true
                        if (existante.type != typeTransaction ||
                            existante.compteId != compteId ||
                            existante.collectionCompte != collectionCompte ||
                            existante.estFractionnee != estFractionnee ||
                            !jourIdentique || !enveloppeIdentique ||
                            existante.tiersUtiliser != tiersUtiliser ||
                            existante.note != note ||
                            kotlin.math.abs(existante.montant - montant) > 0.000001
                        ) throw IllegalStateException("Identifiant d'opération déjà utilisé pour une transaction différente.")
                        // Un retry déjà enregistré n'ajoute aucune écriture ni SyncJob.
                        return@toutOuRien Result.success(Unit)
                    }
                }

                // AT11/AT12/AT19 : le compte et l'enveloppe sont relus dans la
                // transaction Room. Un formulaire ouvert depuis longtemps ne
                // doit pas débiter un compte archivé ou une enveloppe supprimée.
                val compteCourant = compteRepository.getCompteById(compteId, collectionCompte)
                    ?: throw IllegalStateException("Compte introuvable : transaction refusée.")
                if (compteCourant.estArchive || compteCourant.collection != collectionCompte) {
                    throw IllegalStateException("Le compte sélectionné est archivé ou a changé de type.")
                }
                if (typeTransaction == TypeTransaction.Depense && !estFractionnee) {
                    val idEnveloppe = enveloppeId?.takeIf { it.isNotBlank() }
                        ?: throw IllegalArgumentException("Une enveloppe est requise pour cette dépense.")
                    val enveloppeCourante = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                        .find { it.id == idEnveloppe && it.utilisateurId == compteCourant.utilisateurId && !it.estArchive }
                        ?: throw IllegalStateException("L'enveloppe choisie n'est plus disponible.")
                }

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
                // Room annule automatiquement; ne pas fabriquer de SyncJob inverse
                // pendant une transaction qui va deja faire rollback.
                if (operationAtomique !is OperationAtomiqueRoom) {
                    siEchec { transactionRepository.supprimerTransaction(idCree).getOrThrow() }
                }

                // 📝 ENREGISTRER DANS L'HISTORIQUE
                try {
                    val compte = compteRepository.getCompteById(compteId, collectionCompte)
                    val enveloppe = if (!allocationMensuelleId.isNullOrBlank()) {
                        enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                            .find { it.id == enveloppeId && !it.estArchive }
                            ?: throw IllegalStateException("Enveloppe d'historique introuvable.")
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
                                    ?: throw IllegalArgumentException("Fractions manquantes.")
                                if (fractions.isEmpty()) throw IllegalArgumentException("Aucune fraction à enregistrer.")
                                val enveloppesParId = enveloppeRepository.recupererToutesLesEnveloppes()
                                    .getOrThrow().filter { !it.estArchive }.associateBy { it.id }
                                var totalCents = 0L

                                for ((index, fraction) in fractions.withIndex()) {
                                    // AT9/AT10/AT11 : lecture canonique + ancienne forme et note individuelle.
                                    val idFraction = (fraction["enveloppe_id"] ?: fraction["enveloppeId"]) as? String
                                        ?: throw IllegalArgumentException("Fraction sans enveloppe.")
                                    val montantFraction = fraction["montant"] as? Double
                                        ?: throw IllegalArgumentException("Fraction sans montant.")
                                    if (!montantFraction.isFinite() || montantFraction <= 0.0) {
                                        throw IllegalArgumentException("Montant d'une fraction invalide.")
                                    }
                                    val cents = kotlin.math.round(montantFraction * 100.0).toLong()
                                    if (cents <= 0L ||
                                        kotlin.math.abs(montantFraction * 100.0 - cents) > 0.000001
                                    ) throw IllegalArgumentException("Fraction non exacte au cent.")
                                    totalCents = Math.addExact(totalCents, cents)
                                    val enveloppeFraction = enveloppesParId[idFraction]
                                        ?: throw IllegalStateException("Enveloppe de fraction introuvable ou archivée.")
                                    val noteFraction = (fraction["note"] as? String)?.takeIf { it.isNotBlank() }
                                        ?: "Fraction ${index + 1} de transaction fractionnée"
                                    historiqueAllocationService.enregistrerTransactionDirecte(
                                        compte = compte,
                                        enveloppe = enveloppeFraction,
                                        typeTransaction = "FRACTION_${typeTransaction.name}",
                                        montant = -montantFraction,
                                        soldeAvant = compte.solde,
                                        soldeApres = compte.solde,
                                        pretAPlacerAvant = compte.pretAPlacer,
                                        pretAPlacerApres = compte.pretAPlacer,
                                        note = noteFraction
                                    )
                                }
                                if (totalCents != kotlin.math.round(montant * 100.0).toLong()) {
                                    throw IllegalArgumentException("Somme des fractions différente du montant total.")
                                }
                                android.util.Log.d("ToutieBudget", "✅ ENREGISTRER_TRANSACTION_USE_CASE : Enregistrement historique des fractions réussi")
                            } catch (e: Exception) {
                                android.util.Log.e("ToutieBudget", "❌ ENREGISTRER_TRANSACTION_USE_CASE : Erreur lors de l'enregistrement de l'historique des fractions: ${e.message}")
                                // AT4/AT15 : l'historique fait partie de l'écriture atomique.
                                // L'échec doit remonter pour annuler compte, allocations et SyncJobs.
                                throw e
                            }
                        }
                    } else {
                        android.util.Log.d("ToutieBudget", "ℹ️ ENREGISTRER_TRANSACTION_USE_CASE : Compte n'est pas un CompteCheque, pas d'enregistrement dans l'historique (transaction ${typeTransaction})")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ToutieBudget", "❌ ENREGISTRER_TRANSACTION_USE_CASE : Erreur lors de l'enregistrement dans l'historique (transaction ${typeTransaction}): ${e.message}")
                    // AT15 : aucun succès si l'historique obligatoire échoue.
                    throw e
                }

                // 3. Mettre à jour les soldes, l'un après l'autre pour pouvoir défaire le premier si le second échoue
                mettreAJourSoldeCompte(compteId, collectionCompte, typeTransaction, montant).getOrThrow()
                if (operationAtomique !is OperationAtomiqueRoom) {
                    siEchec { mettreAJourSoldeCompte(compteId, collectionCompte, typeTransaction, montant, sens = -1.0).getOrThrow() }
                }
                if (!allocationMensuelleId.isNullOrBlank()) {
                    mettreAJourSoldeEnveloppe(allocationMensuelleId, montant, collectionCompte).getOrThrow()
                }

                Result.success(Unit)
              }
            }
            BudgetEvents.refreshManual()
            resultat
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
                // AT12 : même politique de provenance pour une dépense simple
                // et fractionnée. Une allocation positive financée par un
                // autre compte ne peut pas être utilisée sans virement explicite.
                if (!allocationExistante.solde.isFinite() ||
                    !allocationExistante.alloue.isFinite() ||
                    !allocationExistante.depense.isFinite()
                ) throw IllegalStateException("Les montants de l'allocation sont invalides.")
                // Une carte de crédit dépense toujours une enveloppe financée par un compte chèque : pas de refus pour elle.
                if (collectionCompte != "comptes_credits" &&
                    allocationExistante.solde > 0.005 &&
                    allocationExistante.compteSourceId != null &&
                    (allocationExistante.compteSourceId != compteId ||
                        (allocationExistante.collectionCompteSource != null &&
                            allocationExistante.collectionCompteSource != collectionCompte))
                ) throw IllegalStateException("Cette enveloppe est financée par un autre compte.")
                if (allocationExistante.compteSourceId == null &&
                    (kotlin.math.abs(allocationExistante.alloue) > 0.005 ||
                        kotlin.math.abs(allocationExistante.depense) > 0.005)
                ) throw IllegalStateException("Allocation historique sans provenance : correction requise.")
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
