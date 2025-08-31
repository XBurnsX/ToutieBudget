package com.xburnsx.toutiebudget.ui.historique

/**
 * État de l'interface utilisateur pour l'écran d'historique des transactions d'une enveloppe.
 */
data class HistoriqueEnveloppeUiState(
    val isLoading: Boolean = true,
    val erreur: String? = null,
    val nomEnveloppe: String = "",
    val transactions: List<TransactionUi> = emptyList(),
    val transactionsGroupees: Map<String, List<TransactionUi>> = emptyMap(),
    val scrollPosition: Int = 0
)
