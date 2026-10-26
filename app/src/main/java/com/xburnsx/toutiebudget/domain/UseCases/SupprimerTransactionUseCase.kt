package com.xburnsx.toutiebudget.domain.usecases

import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.*
import com.xburnsx.toutiebudget.domain.services.Impl.ArgentServiceImpl
import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
import kotlin.math.abs

/**
 * Use case pour supprimer une transaction existante.
 * Gère la logique métier complète de suppression d'une transaction.
 *
 * DF9 : un paiement de carte/dette a deux moitiés (sortie du compte payeur, entrée sur la
 * carte/dette). Elles sont toujours annulées ENSEMBLE ; supprimer une seule moitié est refusé.
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
    suspend fun executer(transactionId: String): Result<Unit> = runCatching {
        // 1. Récupérer la transaction existante
        val transaction = transactionRepository.recupererTransactionParId(transactionId)
            .getOrNull() ?: throw Exception("Transaction non trouvée")

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

        // 3. Annuler l'effet sur chaque compte et sur les enveloppes
        aAnnuler.forEach { tx ->
            val detteAvant = if (tx.collectionCompte == "comptes_dettes") {
                compteRepository.getCompteById(tx.compteId, tx.collectionCompte) as? CompteDette
            } else null

            annulerTransactionCompte(tx.compteId, tx.collectionCompte, tx.type, tx.montant).getOrThrow()

            when {
                tx.estFractionnee && tx.sousItems != null -> rembourserTransactionFractionnee(tx.sousItems).getOrThrow()
                tx.allocationMensuelleId != null &&
                    (tx.type == TypeTransaction.Depense || tx.type == TypeTransaction.PaiementEffectue) ->
                    enveloppeRepository.annulerDepenseAllocation(tx.allocationMensuelleId, tx.montant).getOrThrow()
            }

            // Dette : compteur de versements et archive remis comme avant le paiement
            if (detteAvant != null && tx.type == TypeTransaction.Paiement) {
                restaurerDette(detteAvant)
            }
        }

        // 4. Supprimer les écritures
        aAnnuler.forEach { tx ->
            transactionRepository.supprimerTransaction(tx.id).getOrThrow()
        }

        // 🔄 DÉCLENCHER EXPLICITEMENT LE RAFRAÎCHISSEMENT DE L'INTERFACE
        BudgetEvents.refreshManual()
    }

    private fun estMoitieDePaiement(tx: Transaction): Boolean =
        tx.type == TypeTransaction.PaiementEffectue ||
            (tx.type == TypeTransaction.Paiement && (tx.collectionCompte == "comptes_dettes" || tx.collectionCompte == "comptes_credits")) ||
            (tx.type == TypeTransaction.Emprunt && tx.collectionCompte == "comptes_credits") || // anciens paiements de carte
            tx.type == TypeTransaction.TransfertSortant || tx.type == TypeTransaction.TransfertEntrant // IF9

    /** Même identifiant d'opération ; pour les anciens paiements, même montant, même tiers et même moment. */
    private suspend fun trouverMoitieLiee(tx: Transaction): Transaction? {
        val toutes = transactionRepository.recupererToutesLesTransactions().getOrElse { emptyList() }
            .filter { it.id != tx.id }
        val idOperation = idOperation(tx.sousItems)
        if (idOperation != null) {
            return toutes.firstOrNull { idOperation(it.sousItems) == idOperation }
        }
        val estTransfert = tx.type == TypeTransaction.TransfertSortant || tx.type == TypeTransaction.TransfertEntrant
        val typesContrepartie = when (tx.type) {
            TypeTransaction.PaiementEffectue -> setOf(TypeTransaction.Paiement, TypeTransaction.Emprunt)
            TypeTransaction.TransfertSortant -> setOf(TypeTransaction.TransfertEntrant)
            TypeTransaction.TransfertEntrant -> setOf(TypeTransaction.TransfertSortant)
            else -> setOf(TypeTransaction.PaiementEffectue)
        }
        return toutes
            .filter {
                it.type in typesContrepartie &&
                    it.compteId != tx.compteId &&
                    abs(it.montant - tx.montant) < 0.005 &&
                    abs(it.date.time - tx.date.time) <= 2 * 60 * 1000 &&
                    (estTransfert || (it.tiersUtiliser ?: "") == (tx.tiersUtiliser ?: "")) &&
                    idOperation(it.sousItems) == null
            }
            .minByOrNull { abs(it.date.time - tx.date.time) }
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
    private suspend fun restaurerDette(detteAvant: CompteDette) {
        val detteApres = compteRepository.getCompteById(detteAvant.id, "comptes_dettes") as? CompteDette ?: return
        val etaitSoldeeParCePaiement = detteAvant.estArchive && abs(detteAvant.soldeDette) < 0.01
        val redevientActive = etaitSoldeeParCePaiement && abs(detteApres.soldeDette) >= 0.01
        compteRepository.mettreAJourCompte(
            detteApres.copy(
                paiementEffectue = (detteApres.paiementEffectue - 1).coerceAtLeast(0),
                estArchive = if (redevientActive) false else detteApres.estArchive
            )
        ).getOrThrow()
        if (redevientActive) {
            detteApres.details.enveloppeId?.let { id ->
                enveloppeRepository.recupererToutesLesEnveloppes().getOrElse { emptyList() }
                    .firstOrNull { it.id == id && it.estArchive }
                    ?.let { enveloppeRepository.mettreAJourEnveloppe(it.copy(estArchive = false)) }
            }
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
                val allocationId = sousItem["allocation_mensuelle_id"] as? String
                val montantEnDollars = (sousItem["montant"] as? Double) ?: 0.0

                if (allocationId != null) {
                    val allocation = allocationMensuelleRepository.getAllocationById(allocationId)
                    if (allocation != null) {
                        // Rembourser en soustrayant le montant (déjà en dollars dans le JSON)
                        val allocationRemboursee = allocation.copy(
                            depense = allocation.depense - montantEnDollars,
                            solde = allocation.solde + montantEnDollars
                        )
                        allocationMensuelleRepository.mettreAJourAllocation(allocationRemboursee)
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Annule l'effet d'une transaction sur un compte (exactement l'inverse de son enregistrement).
     */
    private suspend fun annulerTransactionCompte(
        compteId: String,
        collectionCompte: String,
        typeTransaction: TypeTransaction,
        montant: Double
    ): Result<Unit> {
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

        // Un virement entre comptes avait aussi déplacé le « Prêt à placer » des comptes chèques : on le remet
        val annulerPretAPlacer = typeTransaction == TypeTransaction.TransfertSortant || typeTransaction == TypeTransaction.TransfertEntrant
        return compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(
            compteId,
            collectionCompte,
            variationSolde,
            annulerPretAPlacer
        )
    }
}
