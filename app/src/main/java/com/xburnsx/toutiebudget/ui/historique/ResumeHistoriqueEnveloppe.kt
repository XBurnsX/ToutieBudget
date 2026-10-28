package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import java.math.BigDecimal

/**
 * EA2/EA5 : aperçu des mouvements visibles de l'enveloppe.
 * Chaque TransactionUi y représente déjà sa portion (HE2), pas l'achat total.
 *
 * Paiements entre comptes, prêts, virements et autres opérations spéciales
 * ne sont PAS assimilés à des dépenses ou revenus sans lien comptable certain.
 */
data class ResumeHistoriqueEnveloppe(
    val nombre: Int,
    val depensesCentimes: Long,
    val revenusCentimes: Long,
    val autresOperations: Int
) {
    val netCentimes: Long get() = Math.subtractExact(revenusCentimes, depensesCentimes)

    companion object {
        fun calculer(lignes: Collection<TransactionUi>): ResumeHistoriqueEnveloppe {
            var depenses = 0L
            var revenus = 0L
            var autres = 0
            val uniques = lignes.distinctBy { it.id }
            uniques.forEach { ligne ->
                require(ligne.montant.isFinite()) { "Montant invalide dans l'historique." }
                val cents = BigDecimal.valueOf(ligne.montant)
                    .movePointRight(2).longValueExact()
                when (ligne.type) {
                    TypeTransaction.Depense -> depenses = Math.addExact(depenses, cents)
                    TypeTransaction.Revenu -> revenus = Math.addExact(revenus, cents)
                    else -> autres++
                }
            }
            return ResumeHistoriqueEnveloppe(
                nombre = uniques.size,
                depensesCentimes = depenses,
                revenusCentimes = revenus,
                autresOperations = autres
            )
        }
    }
}
