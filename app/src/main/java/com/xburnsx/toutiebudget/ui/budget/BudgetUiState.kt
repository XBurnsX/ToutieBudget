// chemin/simule: /ui/budget/BudgetUiState.kt
package com.xburnsx.toutiebudget.ui.budget

import com.xburnsx.toutiebudget.data.modeles.TypeObjectif

enum class StatutObjectif { GRIS, JAUNE, VERT }

data class EnveloppeUi(
    val id: String,
    val nom: String,
    val solde: Double,
    val depense: Double,
    val alloue: Double, // Total alloué ce mois
    val alloueCumulatif: Double, // ← NOUVEAU : Total alloué depuis le début de l'objectif (pour barres de progression)
    val objectif: Double,
    val couleurProvenance: String?,
    val statutObjectif: StatutObjectif,
    val dateObjectif: String? = null, // Date choisie lors de la création de l'objectif
    val versementRecommande: Double = 0.0,
    val typeObjectif: TypeObjectif = TypeObjectif.Aucun, // Ajouter le type d'objectif
    val estArchive: Boolean = false
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
    val categoriesEnveloppes: List<CategorieEnveloppesUi> = emptyList(),
    val figerPretAPlacer: Boolean = false,
    val categoriesOuvertes: Map<String, Boolean> = emptyMap() // État ouvert/fermé de chaque catégorie
)
