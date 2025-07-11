// chemin/simule: /ui/ajout_transaction/AjoutTransactionUiState.kt
package com.xburnsx.toutiebudget.ui.ajout_transaction

import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi

data class AjoutTransactionUiState(
    val montant: String = "",
    val modeOperation: String = "Standard",
    val typeTransaction: String = "Dépense",
    val compteSelectionne: Compte? = null,
    val enveloppeSelectionnee: EnveloppeUi? = null,
    val tiers: String = "",
    val note: String = "",
    val comptesDisponibles: List<Compte> = emptyList(),
    val enveloppesFiltrees: Map<String, List<EnveloppeUi>> = emptyMap(),
    val isLoading: Boolean = true,
    val erreur: String? = null,
    val transactionReussie: Boolean = false
)
