package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.TypeTransaction

/**
 * HA1/HA2 : filtres purement visuels, sans changer les transactions ni leur
 * ordre initial. Combine période, type de transaction et texte recherché.
 */
object FiltrageHistoriqueCompte {
    fun appliquer(
        lignes: List<TransactionUi>,
        recherche: String,
        periode: PeriodeHistorique,
        type: TypeTransaction?
    ): List<TransactionUi> =
        periode.filtrer(RechercheHistorique.filtrer(lignes, recherche))
            .filter { type == null || it.type == type }
}
