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
    val statutObjectif: StatutObjectif,
    val dateObjectif: String? = null, // Date choisie lors de la création de l'objectif
    val versementRecommande: Double = 0.0
)

data class PretAPlacerUi(
    val compteId: String,
    val nomCompte: String,
    val montant: Double,
    val couleurCompte: String
)

data class CategorieEnveloppesUi(
    val nomCategorie: String,
    val enveloppes: List<EnveloppeUi>
)

data class BudgetUiState(
    val isLoading: Boolean = true,
    val messageChargement: String? = null,
    val erreur: String? = null,
    val bandeauxPretAPlacer: List<PretAPlacerUi> = emptyList(),
    val categoriesEnveloppes: List<CategorieEnveloppesUi> = emptyList()
)
