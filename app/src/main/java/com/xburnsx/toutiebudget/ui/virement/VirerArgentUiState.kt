// chemin/simule: /ui/virement/VirerArgentUiState.kt
package com.xburnsx.toutiebudget.ui.virement

import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import java.util.Date

sealed class ItemVirement {
    abstract val nom: String
    data class CompteItem(val compte: Compte) : ItemVirement() {
        override val nom: String get() = compte.nom
    }
    data class EnveloppeItem(val enveloppe: EnveloppeUi) : ItemVirement() {
        override val nom: String get() = enveloppe.nom
    }
}

enum class SelecteurOuvert {
    SOURCE, DESTINATION, AUCUN
}

enum class VirementMode {
    ENVELOPPES, COMPTES
}

data class VirerArgentUiState(
    val isLoading: Boolean = false,
    val montant: String = "",
    val sourcesDisponibles: Map<String, List<ItemVirement>> = emptyMap(),
    val destinationsDisponibles: Map<String, List<ItemVirement>> = emptyMap(),
    val sourceSelectionnee: ItemVirement? = null,
    val destinationSelectionnee: ItemVirement? = null,
    val virementReussi: Boolean = false,
    val erreur: String? = null,
    val selecteurOuvert: SelecteurOuvert = SelecteurOuvert.AUCUN,
    val isVirementButtonEnabled: Boolean = false,
    val mode: VirementMode = VirementMode.ENVELOPPES,
    val moisSelectionne: Date = Date() // ← DÉFAUT : Mois actuel, mais sera changé par l'UI
)
