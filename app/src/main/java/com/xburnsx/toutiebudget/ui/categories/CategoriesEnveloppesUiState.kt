// chemin/simule: /ui/categories/CategoriesEnveloppesUiState.kt
package com.xburnsx.toutiebudget.ui.categories

import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import java.util.Date

data class ObjectifFormState(
    val type: TypeObjectif = TypeObjectif.Mensuel, // Par défaut Mensuel au lieu d'Annuel
    val montant: String = "",
    val date: Date? = null,
    val dateDebut: Date? = null, // AJOUT : Pour la date de début
    val dateFin: Date? = null, // 🆕 NOUVEAU : Pour la date de fin des échéances
    val jour: Int? = null,
    val resetApresEcheance: Boolean = false // AJOUT : Pour les objectifs d'échéance
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

    // 🆕 NOUVEAUX ÉTATS POUR LE DÉPLACEMENT DES CATÉGORIES ET ENVELOPPES
    val isModeReorganisation: Boolean = false,
    val categorieEnDeplacement: String? = null,
    val enveloppeEnDeplacement: String? = null, // 🆕 Pour le déplacement d'enveloppes
    val ordreTemporaire: Map<String, Int> = emptyMap() // Cache temporaire des ordres pendant le drag
)
