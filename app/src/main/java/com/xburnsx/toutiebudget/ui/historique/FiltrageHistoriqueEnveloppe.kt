package com.xburnsx.toutiebudget.ui.historique

/**
 * EA4/HE11 : applique les filtres sur les portions réellement imputées à
 * l'enveloppe. Compte + collection évitent de mélanger deux profils comptables.
 */
object FiltrageHistoriqueEnveloppe {
    fun appliquer(
        transactions: List<TransactionUi>,
        recherche: String,
        periode: PeriodeHistorique,
        compteSource: CompteSourceHistorique?
    ): List<TransactionUi> =
        periode.filtrer(RechercheHistorique.filtrer(transactions, recherche))
            .filter { ligne ->
                compteSource == null ||
                    (ligne.compteId == compteSource.compteId &&
                        ligne.collectionCompte == compteSource.collectionCompte)
            }
}
