// chemin/simule: /ui/historique/HistoriqueCompteUiState.kt
package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.ui.historique.TransactionUi
import java.util.Date

/**
 * État de l'interface utilisateur pour l'écran d'historique des transactions d'un compte.
 */
data class HistoriqueCompteUiState(
    val isLoading: Boolean = true,
    val erreur: String? = null,
    val nomCompte: String = "",
    val transactions: List<TransactionUi> = emptyList(),
    val transactionsGroupees: Map<String, List<TransactionUi>> = emptyMap(),
    val scrollPosition: Int = 0
)

/**
 * Événements de navigation pour l'écran d'historique.
 */
sealed class HistoriqueNavigationEvent {
    data class ModifierTransaction(val transactionId: String) : HistoriqueNavigationEvent()
    object TransactionModifiee : HistoriqueNavigationEvent()
}
