// chemin/simule: /ui/historique/HistoriqueCompteUiState.kt
package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import java.util.Date

data class TransactionUi(
    val id: String,
    val type: TypeTransaction,
    val montant: Double,
    val date: Date,
    val tiers: String,
    val nomEnveloppe: String?,
    val note: String?
)

data class HistoriqueCompteUiState(
    val isLoading: Boolean = true,
    val nomCompte: String = "",
    val transactions: List<TransactionUi> = emptyList(),
    val erreur: String? = null
)
