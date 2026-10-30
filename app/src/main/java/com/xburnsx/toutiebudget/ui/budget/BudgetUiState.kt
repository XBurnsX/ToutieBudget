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
    val couleurProvenance: String?, // Pour la bulle (reset à null si solde = 0)
    val couleurObjectif: String? = null, // ← NOUVEAU : Pour les barres (toujours garder couleur du compte)
    val statutObjectif: StatutObjectif,
    val dateObjectif: String? = null, // Date choisie lors de la création de l'objectif
    val versementRecommande: Double = 0.0,
    val typeObjectif: TypeObjectif = TypeObjectif.Aucun, // Ajouter le type d'objectif
    val estArchive: Boolean = false,
    /** GN3 : montant parti au mois suivant par le rollover (mois passé seulement, 0 sinon). */
    val montantReporte: Double = 0.0
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
    val categoriesOuvertes: Map<String, Boolean> = emptyMap(), // État ouvert/fermé de chaque catégorie
    val enveloppesFavorites: Set<String> = emptySet(), // A4 : ids des enveloppes favorites
    val montantDejaCouvertMois: Double = 0.0, // GA5 : déficits déjà couverts ce mois-ci (cet appareil)
    /** Mois dont les données sont affichées : seule référence, l'écran ne fait que l'afficher. */
    val moisAffiche: java.util.Date = java.util.Date(),
    /** Le dernier chargement a échoué : les enveloppes visibles ne sont pas fiables pour assigner. */
    val chargementEchoue: Boolean = false,
    /** Problème du report de mois ou des remises à zéro : reste affiché jusqu'à ce qu'on le ferme. */
    val alertePersistante: String? = null,
    /** Argent du mois dans des enveloppes non affichées (archivées ou supprimées). */
    val argentNonAffiche: Double = 0.0
)

/** Résultat d'une répartition vers plusieurs enveloppes (A2). */
data class ResultatRepartition(
    val reussies: List<String>,
    val erreur: String? = null
)
