package com.xburnsx.toutiebudget.data.banque

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Données minimales de rapprochement, sans mutation des comptes ou des enveloppes. */
data class DepenseSaisieCandidate(
    val transactionId: String,
    val utilisateurId: String,
    val compteId: String,
    val collectionCompte: String,
    val date: LocalDate,
    val montantCentimes: Long,
    val tiersId: String?,
    val libelle: String,
    val type: String = "DEPENSE"
)

data class OperationImporteeCandidate(
    val utilisateurId: String,
    val compteId: String,
    val collectionCompte: String,
    val date: LocalDate,
    val montantCentimes: Long,
    val libelleBancaireBrut: String,
    val tiersReconnuId: String?,
    val type: String = "DEPENSE"
)

data class PropositionJumelage(
    val transactionId: String,
    val score: Int,
    val montantDifferent: Boolean,
    val decalageJours: Long,
    val memeTiers: Boolean
)

/**
 * BANQ-06 : PROPOSER seulement des rapprochements.
 * N'insère, ne supprime et ne modifie JAMAIS une transaction.
 * Le choix "Oui, jumeler" exigera une confirmation humaine explicite.
 */
object RapprochementBancaire {
    fun proposer(
        operation: OperationImporteeCandidate,
        transactionsExistantes: List<DepenseSaisieCandidate>,
        joursDeTolerance: Long = 5
    ): List<PropositionJumelage> {
        require(joursDeTolerance in 0..30) { "Fenêtre de rapprochement invalide." }
        if (operation.utilisateurId.isBlank() || operation.compteId.isBlank()) return emptyList()
        if (operation.montantCentimes <= 0L) return emptyList()
        if (operation.collectionCompte !in setOf("comptes_cheques", "comptes_credits"))
            return emptyList()
        return transactionsExistantes.asSequence()
            .filter {
                it.transactionId.isNotBlank() &&
                    it.utilisateurId == operation.utilisateurId &&
                    it.compteId == operation.compteId &&
                    it.collectionCompte == operation.collectionCompte &&
                    it.type == operation.type &&
                    it.montantCentimes > 0L
            }
            .mapNotNull { saisie ->
                val decalage = abs(ChronoUnit.DAYS.between(saisie.date, operation.date))
                if (decalage > joursDeTolerance) return@mapNotNull null

                val montantExact = saisie.montantCentimes == operation.montantCentimes
                val memeTiers = !operation.tiersReconnuId.isNullOrBlank() &&
                    operation.tiersReconnuId == saisie.tiersId
                val libellesEgaux = operation.libelleBancaireBrut.isNotBlank() &&
                    ReconnaissanceTiersBancaire.normaliser(operation.libelleBancaireBrut) ==
                    ReconnaissanceTiersBancaire.normaliser(saisie.libelle)
                // Montant modifié entre autorisation et comptabilisation :
                // proposer uniquement si une identité de tiers/libellé l'appuie.
                if (!montantExact && !memeTiers && !libellesEgaux)
                    return@mapNotNull null
                val score = (if (montantExact) 70 else 0) +
                    (if (memeTiers) 25 else 0) +
                    (if (libellesEgaux) 15 else 0) +
                    (joursDeTolerance - decalage).toInt().coerceAtLeast(0)
                PropositionJumelage(
                    transactionId = saisie.transactionId,
                    score = score,
                    montantDifferent = !montantExact,
                    decalageJours = decalage,
                    memeTiers = memeTiers
                )
            }
            .sortedWith(compareByDescending<PropositionJumelage> { it.score }
                .thenBy { it.decalageJours }.thenBy { it.transactionId })
            .toList()
    }
}
