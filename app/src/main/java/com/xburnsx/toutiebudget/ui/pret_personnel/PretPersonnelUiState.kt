package com.xburnsx.toutiebudget.ui.pret_personnel

import java.util.Date

data class PretPersonnelItem(
    val key: String, // tiersId ou nom
    val nomTiers: String,
    val montantPrete: Double,
    val montantRembourse: Double,
    val soldeRestant: Double,
    val derniereDate: Date?
)

enum class PretTab { PRET, EMPRUNT, ARCHIVER }

data class PretPersonnelUiState(
    val isLoading: Boolean = false,
    val erreur: String? = null,
    val items: List<PretPersonnelItem> = emptyList(),
    val itemsPret: List<PretPersonnelItem> = emptyList(),
    val itemsEmprunt: List<PretPersonnelItem> = emptyList(),
    val currentTab: PretTab = PretTab.PRET
)


