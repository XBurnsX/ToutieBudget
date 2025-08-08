// chemin/simule: /ui/ajout_transaction/composants/SelecteurEnveloppeAvecCategories.kt
// Dépendances: Jetpack Compose, Material3

package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi

/**
 * Item de dropdown qui peut être soit une catégorie (header) soit une enveloppe
 */
sealed class ItemDropdown {
    data class Categorie(val nom: String) : ItemDropdown()
    data class Enveloppe(val enveloppeUi: EnveloppeUi) : ItemDropdown()
}

