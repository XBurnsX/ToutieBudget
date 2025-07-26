// chemin/simule: /ui/comptes/ComptesUiState.kt
package com.xburnsx.toutiebudget.ui.comptes

import com.xburnsx.toutiebudget.data.modeles.Compte

data class CompteFormState(
    val id: String? = null,
    val nom: String = "",
    val type: String = "Compte chèque",
    val solde: String = "",
    val couleur: String = "#2196F3"
)

data class ComptesUiState(
    val isLoading: Boolean = true,
    val erreur: String? = null,
    val comptesGroupes: Map<String, List<Compte>> = emptyMap(),
    val compteSelectionne: Compte? = null,
    val isAjoutDialogVisible: Boolean = false,
    val isModificationDialogVisible: Boolean = false,
    val isReconciliationDialogVisible: Boolean = false,
    val isMenuContextuelVisible: Boolean = false,
    val formState: CompteFormState = CompteFormState(),
    val isClavierNumeriqueVisible: Boolean = false
)
