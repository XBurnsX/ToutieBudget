package com.xburnsx.toutiebudget.ui.pret_personnel

import java.util.Date
import com.xburnsx.toutiebudget.data.modeles.PretPersonnel
import com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel

data class PretPersonnelItem(
    val key: String, // tiersUtiliser ou nom
    val nomTiers: String,
    val montantPrete: Double,
    val montantRembourse: Double,
    val soldeRestant: Double,
    val derniereDate: Date?,
    val type: TypePretPersonnel
)

enum class PretTab { PRET, EMPRUNT, ARCHIVER }

data class HistoriqueItem(
    val id: String,
    val date: Date,
    val type: String,
    val montant: Double
)

data class PretPersonnelUiState(
    val isLoading: Boolean = false,
    val erreur: String? = null,
    val items: List<PretPersonnelItem> = emptyList(),
    val itemsPret: List<PretPersonnelItem> = emptyList(),
    val itemsEmprunt: List<PretPersonnelItem> = emptyList(),
    val itemsArchives: List<PretPersonnelItem> = emptyList(),
    val currentTab: PretTab = PretTab.PRET,
    val isLoadingHistorique: Boolean = false,
    val historique: List<HistoriqueItem> = emptyList(),
    val detailPret: PretPersonnel? = null
)


