// chemin/simule: /ui/virement/VirerArgentUiState.kt
package com.xburnsx.toutiebudget.ui.virement

import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.Enveloppe

sealed class ItemVirement {
    abstract val nom: String
    data class CompteItem(val compte: Compte) : ItemVirement() {
        override val nom: String get() = compte.nom
    }
    data class EnveloppeItem(val enveloppe: Enveloppe, val solde: Double, val couleurProvenance: String?) : ItemVirement() {
        override val nom: String get() = enveloppe.nom
    }
}

enum class SelecteurOuvert {
    SOURCE, DESTINATION, AUCUN
}

data class VirerArgentUiState(
    val isLoading: Boolean = true,
    val montant: String = "",
    val sourcesDisponibles: Map<String, List<ItemVirement>> = emptyMap(),
    val destinationsDisponibles: Map<String, List<ItemVirement>> = emptyMap(),
    val sourceSelectionnee: ItemVirement? = null,
    val destinationSelectionnee: ItemVirement? = null,
    val virementReussi: Boolean = false,
    val erreur: String? = null,
    val selecteurOuvert: SelecteurOuvert = SelecteurOuvert.AUCUN
)
