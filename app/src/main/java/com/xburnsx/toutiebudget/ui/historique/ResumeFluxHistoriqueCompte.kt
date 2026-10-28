package com.xburnsx.toutiebudget.ui.historique

import java.math.BigDecimal

/**
 * HA3 : totaux des seuls mouvements VISIBLES dans l'historique de compte.
 * On compte chaque ID bancaire une fois, jamais une fois par fraction.
 *
 * Les montants corrompus sont exclus et signalés, pas remplacés par zéro.
 * Ce résumé de flux n'est pas le solde bancaire ni le « Prêt à placer ».
 */
data class ResumeFluxHistoriqueCompte(
    val mouvements: Int,
    val entreesCentimes: Long,
    val sortiesCentimes: Long,
    val montantsExclus: Int,
    val typesNonClasses: Int
) {
    val netCentimes: Long get() = Math.subtractExact(entreesCentimes, sortiesCentimes)

    companion object {
        fun calculer(lignes: Collection<TransactionUi>): ResumeFluxHistoriqueCompte {
            var entrees = 0L
            var sorties = 0L
            var exclus = 0
            var nonClasses = 0
            val uniques = lignes.distinctBy { it.id }
            uniques.forEach { ligne ->
                val cents = runCatching {
                    require(ligne.montant.isFinite() && ligne.montant >= 0.0)
                    BigDecimal.valueOf(ligne.montant)
                        .movePointRight(2).longValueExact()
                }.getOrNull()
                if (cents == null) {
                    exclus++
                } else when (SensMontantHistorique.determiner(
                    ligne.type, ligne.collectionCompte
                )) {
                    SensMontantHistorique.Sens.ENTREE ->
                        entrees = Math.addExact(entrees, cents)
                    SensMontantHistorique.Sens.SORTIE ->
                        sorties = Math.addExact(sorties, cents)
                    SensMontantHistorique.Sens.INCONNU -> nonClasses++
                }
            }
            return ResumeFluxHistoriqueCompte(
                mouvements = uniques.size,
                entreesCentimes = entrees,
                sortiesCentimes = sorties,
                montantsExclus = exclus,
                typesNonClasses = nonClasses
            )
        }
    }
}
