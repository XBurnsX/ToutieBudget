package com.xburnsx.toutiebudget.domain.usecases

import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.*
import com.xburnsx.toutiebudget.domain.services.Impl.ArgentServiceImpl
import com.xburnsx.toutiebudget.domain.services.MoteurDette
import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import kotlin.math.abs
import com.xburnsx.toutiebudget.utils.OperationAtomique
import com.xburnsx.toutiebudget.utils.OperationAtomiqueDirecte

/**
 * Use case pour supprimer une transaction existante.
 * Gère la logique métier complète de suppression d'une transaction.
 *
 * DF9 : un paiement de carte/dette a deux moitiés (sortie du compte payeur, entrée sur la
 * carte/dette). Elles sont toujours annulées ENSEMBLE ; supprimer une seule moitié est refusé.
 *
 * HC2 : tout ou rien. Chaque écriture déjà faite est notée avec son inverse ; si une étape
 * échoue, les inverses sont rejoués et l'état d'origine revient.
 * HC4 : supprimer un prêt, un emprunt ou un remboursement personnel remet aussi le dossier du tiers.
 */
class SupprimerTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository,
    private val pretPersonnelRepository: PretPersonnelRepository? = null,
    private val operationAtomique: OperationAtomique = OperationAtomiqueDirecte
) {

    /**
     * Supprime une transaction existante avec mise à jour des soldes.
     *
     * @param transactionId ID de la transaction à supprimer
     * @param garderEcriture MT1/MT10 : défait seulement les effets (compte, enveloppes) et garde l'écriture,
     *   pour qu'une modification la réécrive sur place (un seul UPDATE, jamais DELETE + CREATE).
     *   Réservé aux dépenses et revenus ordinaires, sans moitié liée ni dossier de prêt.
     *
     * @return Result indiquant le succès ou l'échec avec l'exception
     */
    suspend fun executer(
        transactionId: String,
        garderEcriture: Boolean = false,
        transactionAttendue: Transaction? = null
    ): Result<Unit> = runCatching { operationAtomique.executer {
        // HC1/HC6 : relecture ET comparaison dans la même transaction Room
        // que les écritures d'annulation. Une modification entre la fenêtre
        // de confirmation et le commit ne peut plus supprimer une autre version.
        // L'argument optionnel ne change pas les parcours de modification existants.
        val transaction = transactionRepository.recupererTransactionParId(transactionId)
            .getOrThrow()
        if (transactionAttendue != null && transaction != transactionAttendue) {
            throw IllegalStateException(
                "Transaction modifiée depuis la confirmation : suppression annulée."
            )
        }
        require(transaction.montant.isFinite() && transaction.montant > 0.0) {
            "Transaction invalide : montant nul ou non fini."
        }
        if (garderEcriture) {
            // MT5/MT7 : un paiement ou un virement défait ses DEUX côtés ; un prêt personnel passe par son dossier
            require(idDossierPret(transaction.sousItems) == null) { "Un prêt personnel se modifie par son dossier." }
        }

        // 2. Un paiement lié s'annule avec son autre moitié
        val aAnnuler = if (estMoitieDePaiement(transaction)) {
            val autre = trouverMoitieLiee(transaction)
                ?: throw Exception(
                    "Ce paiement relie deux comptes, mais l'autre écriture est introuvable : rien n'a été supprimé. " +
                        "Corrigez plutôt le solde avec un ajustement."
                )
            listOf(transaction, autre)
        } else {
            listOf(transaction)
        }

        // 3. Dossiers de prêt personnel : calculés (et éventuellement refusés) avant toute écriture
        val dossiers = aAnnuler.mapNotNull { preparerDossierPret(it) }

        val retours = mutableListOf<suspend () -> Unit>()
        try {
            // 4. Annuler l'effet sur chaque compte et sur les enveloppes
            aAnnuler.forEach { tx ->
                val detteAvant = if (tx.collectionCompte == "comptes_dettes") {
                    compteRepository.getCompteById(tx.compteId, tx.collectionCompte) as? CompteDette
                } else null

                val (variation, avecPretAPlacer) = inverseSurCompte(tx.type, tx.montant)
                compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(tx.compteId, tx.collectionCompte, variation, avecPretAPlacer).getOrThrow()
                retours += {
                    compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(tx.compteId, tx.collectionCompte, -variation, avecPretAPlacer).getOrThrow()
                }

                when {
                    tx.estFractionnee && tx.sousItems != null ->
                        rembourserTransactionFractionnee(tx.sousItems, retours, surCarte = tx.collectionCompte == "comptes_credits")
                    // AT8 : un achat sur carte n'avait pas baissé le solde de l'enveloppe (dépensé et alloué +montant) ;
                    // l'annuler retire donc le dépensé et l'alloué, sans créer d'argent dans l'enveloppe
                    tx.allocationMensuelleId != null && tx.type == TypeTransaction.Depense && tx.collectionCompte == "comptes_credits" -> {
                        val avant = allocationMensuelleRepository.getAllocationById(tx.allocationMensuelleId)
                        if (avant != null) {
                            allocationMensuelleRepository.mettreAJourAllocation(
                                avant.copy(
                                    depense = MoneyFormatter.roundAmount(avant.depense - tx.montant),
                                    alloue = MoneyFormatter.roundAmount(avant.alloue - tx.montant)
                                )
                            )
                            retours += { allocationMensuelleRepository.mettreAJourAllocation(avant) }
                        }
                    }
                    tx.allocationMensuelleId != null &&
                        (tx.type == TypeTransaction.Depense || tx.type == TypeTransaction.PaiementEffectue) -> {
                        enveloppeRepository.annulerDepenseAllocation(tx.allocationMensuelleId, tx.montant).getOrThrow()
                        retours += { enveloppeRepository.ajouterDepenseAllocation(tx.allocationMensuelleId, tx.montant).getOrThrow() }
                    }
                }

                // Dette : compteur de versements et archive remis comme avant le paiement
                if (detteAvant != null && tx.type == TypeTransaction.Paiement) {
                    val avantRestauration = compteRepository.getCompteById(tx.compteId, tx.collectionCompte)
                    restaurerDette(detteAvant, tx.id)
                    if (avantRestauration != null) retours += { compteRepository.mettreAJourCompte(avantRestauration).getOrThrow() }
                }
            }

            // 5. Dossiers du tiers
            dossiers.forEach { (avant, apres) ->
                val depot = pretPersonnelRepository ?: return@forEach
                if (apres == null) {
                    depot.supprimer(avant.id).getOrThrow()
                    retours += { depot.creer(avant).getOrThrow() }
                } else {
                    depot.mettreAJour(apres).getOrThrow()
                    retours += { depot.mettreAJour(avant).getOrThrow() }
                }
            }

            // 6. Supprimer les écritures (sauf modification sur place)
            if (!garderEcriture) aAnnuler.forEach { tx ->
                transactionRepository.supprimerTransaction(tx.id).getOrThrow()
                retours += { transactionRepository.creerTransaction(tx).getOrThrow() }
            }
        } catch (e: Exception) {
            // DF9/DF10 : operationAtomique est une transaction Room. Laisser
            // remonter l'erreur provoque le rollback de TOUTES les écritures.
            // Rejouer des écritures compensatoires ici créerait de nouveaux
            // SyncJobs, voire un double remboursement.
            throw e
        }
    }
        // Rafraîchir seulement après le commit de la transaction.
        BudgetEvents.refreshManual()
    }

    internal fun estMoitieDePaiement(tx: Transaction): Boolean =
        tx.type == TypeTransaction.PaiementEffectue ||
            (tx.type == TypeTransaction.Paiement && (tx.collectionCompte == "comptes_dettes" || tx.collectionCompte == "comptes_credits")) ||
            (tx.type == TypeTransaction.Emprunt && tx.collectionCompte == "comptes_credits") || // anciens paiements de carte
            tx.type == TypeTransaction.TransfertSortant || tx.type == TypeTransaction.TransfertEntrant // IF9

    /** Même identifiant d'opération ; pour les anciens paiements, même montant, même tiers et même moment. */
    /** MT5/MT7 : l'autre côté d'un paiement ou d'un virement (même identifiant d'opération, ou ancien rapprochement). */
    internal suspend fun trouverMoitieLiee(tx: Transaction): Transaction? {
        val toutes = transactionRepository.recupererToutesLesTransactions().getOrThrow()
            .filter { it.id != tx.id }
        val idOperation = idOperation(tx.sousItems)
        val estTransfert = tx.type == TypeTransaction.TransfertSortant || tx.type == TypeTransaction.TransfertEntrant
        val typesContrepartie = when (tx.type) {
            TypeTransaction.PaiementEffectue -> setOf(TypeTransaction.Paiement, TypeTransaction.Emprunt)
            TypeTransaction.TransfertSortant -> setOf(TypeTransaction.TransfertEntrant)
            TypeTransaction.TransfertEntrant -> setOf(TypeTransaction.TransfertSortant)
            else -> setOf(TypeTransaction.PaiementEffectue)
        }
        if (idOperation != null) {
            // DF9 : même UUID ET exactement la bonne écriture bancaire.
            // firstOrNull() pouvait supprimer une moitié étrangère portant
            // accidentellement le même identifiant, ou ignorer un doublon.
            val associees = toutes.filter {
                idOperation(it.sousItems) == idOperation &&
                    it.type in typesContrepartie &&
                    it.compteId != tx.compteId &&
                    kotlin.math.abs(it.montant - tx.montant) < 0.005
            }
            if (associees.size != 1) throw IllegalStateException(
                "Paiement lié incohérent : ${associees.size} contrepartie(s) valides. " +
                    "Annulation refusée pour protéger les soldes des comptes."
            )
            return associees.single()
        }
        // Anciennes écritures sans ID de paire : ne jamais choisir une
        // contrepartie approximative s'il existe deux candidats plausibles.
        val candidates = toutes.filter {
            it.type in typesContrepartie && it.compteId != tx.compteId &&
                abs(it.montant - tx.montant) < 0.005 &&
                abs(it.date.time - tx.date.time) <= 2 * 60 * 1000 &&
                (estTransfert || (it.tiersUtiliser ?: "") == (tx.tiersUtiliser ?: "")) &&
                idOperation(it.sousItems) == null
        }
        return candidates.singleOrNull()
    }

    private fun idOperation(sousItems: String?): String? {
        if (sousItems.isNullOrBlank() || !sousItems.contains(ArgentServiceImpl.CLE_OPERATION_PAIEMENT)) return null
        return try {
            com.google.gson.JsonParser.parseString(sousItems).asJsonObject
                .get(ArgentServiceImpl.CLE_OPERATION_PAIEMENT)?.asString
        } catch (_: Exception) {
            null
        }
    }

    /** Après annulation d'un paiement : un versement de moins, et la dette ressort des archives si elle n'est plus soldée. */
    private suspend fun restaurerDette(detteAvant: CompteDette, paiementAnnuleId: String) {
        val detteApres = compteRepository.getCompteById(detteAvant.id, "comptes_dettes") as? CompteDette ?: return
        val etaitSoldeeParCePaiement = detteAvant.estArchive && abs(detteAvant.soldeDette) < 0.01
        val redevientActive = etaitSoldeeParCePaiement && abs(detteApres.soldeDette) >= 0.01
        // L'écriture annulée est encore présente dans Room jusqu'à la fin
        // de l'opération atomique. Exclure son ID AVANT de recalculer.
        val autresPaiements = transactionRepository
            .recupererTransactionsPourCompte(detteAvant.id, "comptes_dettes").getOrThrow()
            .filter { it.id != paiementAnnuleId }
        val mensualites = MoteurDette.echeancesCouvertes(detteApres, autresPaiements)
        compteRepository.mettreAJourCompte(
            detteApres.copy(
                paiementEffectue = mensualites ?: detteApres.paiementEffectue,
                estArchive = if (redevientActive) false else detteApres.estArchive
            )
        ).getOrThrow()
        if (redevientActive) {
            detteApres.details.enveloppeId?.let { id ->
                enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                    .firstOrNull { it.id == id && it.estArchive }
                    ?.let { enveloppeRepository.mettreAJourEnveloppe(it.copy(estArchive = false)).getOrThrow() }
            }
        }
    }

    /**
     * Rembourse toutes les allocations d'une transaction fractionnée ; chaque allocation touchée
     * est notée dans [retours] avec sa valeur d'avant.
     */
    private suspend fun rembourserTransactionFractionnee(sousItemsJson: String, retours: MutableList<suspend () -> Unit>, surCarte: Boolean) {
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
        val sousItems = gson.fromJson<List<Map<String, Any>>>(sousItemsJson, type)

        for (sousItem in sousItems) {
            val allocationId = sousItem["allocation_mensuelle_id"] as? String
                ?: throw IllegalStateException("Fraction sans identifiant d'allocation.")
            val montantEnDollars = (sousItem["montant"] as? Double)
                ?: throw IllegalStateException("Montant de fraction manquant.")
            require(montantEnDollars.isFinite() && montantEnDollars > 0.0) {
                "Montant de fraction invalide."
            }
            val allocation = allocationMensuelleRepository.getAllocationById(allocationId)
                ?: throw IllegalStateException("Allocation de fraction introuvable : annulation refusée.")
            // Rembourser (montant déjà en dollars dans le JSON)
            allocationMensuelleRepository.mettreAJourAllocation(
                if (surCarte) {
                    allocation.copy(depense = MoneyFormatter.roundAmount(allocation.depense - montantEnDollars), alloue = MoneyFormatter.roundAmount(allocation.alloue - montantEnDollars))
                } else {
                    allocation.copy(depense = allocation.depense - montantEnDollars, solde = allocation.solde + montantEnDollars)
                }
            )
            retours += { allocationMensuelleRepository.mettreAJourAllocation(allocation) }
        }
    }

    /**
     * L'inverse exact de l'effet d'une transaction sur son compte : variation du solde, et si le
     * « Prêt à placer » avait bougé avec (tout sauf les dépenses et les paiements, comme à l'enregistrement).
     */
    private fun inverseSurCompte(typeTransaction: TypeTransaction, montant: Double): Pair<Double, Boolean> {
        val variationSolde = when (typeTransaction) {
            TypeTransaction.Depense -> montant  // Annuler une dépense = addition
            TypeTransaction.Revenu -> -montant  // Annuler un revenu = soustraction
            TypeTransaction.Pret -> montant      // Annuler un prêt = addition
            TypeTransaction.RemboursementRecu -> -montant  // Annuler un remboursement = soustraction
            TypeTransaction.Emprunt -> -montant // Annuler un emprunt = soustraction
            TypeTransaction.RemboursementDonne -> montant  // Annuler un remboursement donné = addition
            // DF9 : un paiement avait RAPPROCHÉ la dette/carte de zéro (+montant) ; l'annuler l'en éloigne
            TypeTransaction.Paiement -> -montant
            TypeTransaction.PaiementEffectue -> montant // Annuler un paiement effectué = addition
            TypeTransaction.TransfertSortant -> montant  // Annuler un transfert sortant = addition
            TypeTransaction.TransfertEntrant -> -montant // Annuler un transfert entrant = soustraction
        }
        // MT3/HC2 : revenus, prêts, emprunts, remboursements et virements avaient aussi déplacé le Prêt à placer
        val avecPretAPlacer = typeTransaction != TypeTransaction.Depense &&
            typeTransaction != TypeTransaction.Paiement &&
            typeTransaction != TypeTransaction.PaiementEffectue
        return variationSolde to avecPretAPlacer
    }

    /**
     * HC4 : le dossier du tiers avant et après la suppression (après = null : le dossier disparaît).
     * Un prêt ou un emprunt déjà en partie remboursé est refusé : il faut d'abord supprimer ses remboursements.
     */
    private suspend fun preparerDossierPret(tx: Transaction): Pair<PretPersonnel, PretPersonnel?>? {
        val depot = pretPersonnelRepository ?: return null
        val idDossier = idDossierPret(tx.sousItems) ?: return null
        val dossier = depot.lister().getOrThrow().firstOrNull { it.id == idDossier } ?: return null
        return when (tx.type) {
            TypeTransaction.Pret, TypeTransaction.Emprunt -> {
                if (abs(abs(dossier.solde) - abs(dossier.montantInitial)) > 0.005) {
                    throw Exception(
                        "Ce ${if (tx.type == TypeTransaction.Pret) "prêt" else "emprunt"} a déjà des remboursements : " +
                            "supprimez-les d'abord. Rien n'a été supprimé."
                    )
                }
                dossier to null
            }
            TypeTransaction.RemboursementRecu ->
                dossier to dossier.copy(solde = MoneyFormatter.roundAmount(dossier.solde + tx.montant), estArchive = false)
            TypeTransaction.RemboursementDonne ->
                dossier to dossier.copy(solde = MoneyFormatter.roundAmount(dossier.solde - tx.montant), estArchive = false)
            else -> null
        }
    }

    private fun idDossierPret(sousItems: String?): String? {
        if (sousItems.isNullOrBlank() || !sousItems.contains("pret_personnel_id")) return null
        return try {
            com.google.gson.JsonParser.parseString(sousItems).asJsonObject.get("pret_personnel_id")?.asString
        } catch (_: Exception) {
            null
        }
    }
}
