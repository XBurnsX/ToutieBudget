// chemin/simule: /ui/budget/BudgetUiState.kt
package com.xburnsx.toutiebudget.ui.budget

import com.xburnsx.toutiebudget.data.modeles.Compte

enum class StatutObjectif { GRIS, JAUNE, VERT }

data class EnveloppeUi(
    val id: String,
    val nom: String,
    val solde: Double,
    val depense: Double,
    val objectif: Double,
    val couleurProvenance: String?,
    val statutObjectif: StatutObjectif
)

data class BudgetUiState(
    val isLoading: Boolean = true,
    val messageChargement: String = "",
    val erreur: String? = null,
    val pretAPlacer: Double = 0.0,
    val enveloppes: List<EnveloppeUi> = emptyList()
)
