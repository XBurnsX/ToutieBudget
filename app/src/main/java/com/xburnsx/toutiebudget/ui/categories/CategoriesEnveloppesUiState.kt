// chemin/simule: /ui/categories/CategoriesEnveloppesUiState.kt
package com.xburnsx.toutiebudget.ui.categories

import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import java.util.Date

data class ObjectifFormState(
    val type: TypeObjectif = TypeObjectif.Aucun,
    val montant: String = "",
    val date: Date? = null,
    val jour: Int? = null
)

data class CategoriesEnveloppesUiState(
    val isLoading: Boolean = true,
    val erreur: String? = null,
    val enveloppesGroupees: Map<String, List<Enveloppe>> = emptyMap(),
    val isAjoutCategorieDialogVisible: Boolean = false,
    val isAjoutEnveloppeDialogVisible: Boolean = false,
    val isObjectifDialogVisible: Boolean = false,
    val isConfirmationSuppressionCategorieVisible: Boolean = false,
    val isConfirmationSuppressionEnveloppeVisible: Boolean = false,
    val categoriePourAjout: String? = null,
    val categoriePourSuppression: String? = null,
    val enveloppePourSuppression: Enveloppe? = null,
    val nomNouvelleCategorie: String = "",
    val nomNouvelleEnveloppe: String = "",
    val enveloppePourObjectif: Enveloppe? = null,
    val objectifFormState: ObjectifFormState = ObjectifFormState(),
    // Nouveaux champs pour le mode édition
    val isModeEdition: Boolean = false,
    val categoriesOrdonnees: List<String> = emptyList(),
    // Nouveaux champs pour le drag & drop
    val isDragMode: Boolean = false,
    val draggedItemId: String? = null,
    val draggedItemType: DragItemType? = null
)

enum class DragItemType {
    CATEGORIE,
    ENVELOPPE
}
