package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * EA5 : historique mensuel réel des dépenses de l'enveloppe.
 * Utilise la portion TransactionUi, jamais le montant total de l'achat.
 * Ne mélange pas les virements et paiements spéciaux avec les dépenses.
 */
data class TendanceMensuelleEnveloppe(
    val mois: YearMonth,
    val depensesCentimes: Long,
    val revenusCentimes: Long,
    val mouvements: Int
) {
    companion object {
        fun calculer(
            transactions: List<TransactionUi>,
            zone: ZoneId = ZoneId.systemDefault()
        ): List<TendanceMensuelleEnveloppe> =
            transactions.distinctBy { it.id }
                .filter { it.type == TypeTransaction.Depense || it.type == TypeTransaction.Revenu }
                .groupBy { tx ->
                    YearMonth.from(Instant.ofEpochMilli(tx.date.time).atZone(zone))
                }
                .map { (mois, lignes) ->
                    var depenses = 0L
                    var revenus = 0L
                    for (tx in lignes) {
                        require(tx.montant.isFinite()) { "Montant historique mensuel invalide." }
                        val cents = BigDecimal.valueOf(tx.montant)
                            .movePointRight(2).longValueExact()
                        when (tx.type) {
                            TypeTransaction.Depense -> depenses = Math.addExact(depenses, cents)
                            TypeTransaction.Revenu -> revenus = Math.addExact(revenus, cents)
                            else -> Unit
                        }
                    }
                    TendanceMensuelleEnveloppe(mois, depenses, revenus, lignes.size)
                }
                .sortedByDescending { it.mois }
    }
}
