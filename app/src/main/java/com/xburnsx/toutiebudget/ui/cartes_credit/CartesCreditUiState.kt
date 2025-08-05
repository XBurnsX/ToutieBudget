package com.xburnsx.toutiebudget.ui.cartes_credit

import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.repositories.PaiementPlanifie

/**
 * États de l'interface utilisateur pour la gestion des cartes de crédit.
 */
data class CartesCreditUiState(
    val cartesCredit: List<CompteCredit> = emptyList(),
    val estEnChargement: Boolean = false,
    val messageErreur: String? = null,
    val carteSelectionnee: CompteCredit? = null,
    val afficherDialogAjout: Boolean = false,
    val afficherDialogModification: Boolean = false,
    val afficherDialogSuppression: Boolean = false,
    val afficherPlanRemboursement: Boolean = false,
    val planRemboursement: List<PaiementPlanifie> = emptyList(),
    val paiementMensuelSimulation: Double = 0.0
)

/**
 * États pour l'ajout/modification d'une carte de crédit.
 */
data class FormulaireCarteCredit(
    val nom: String = "",
    val limiteCredit: String = "",
    val tauxInteret: String = "",
    val soldeActuel: String = "",
    val couleur: String = "#2196F3",
    val erreurNom: String? = null,
    val erreurLimite: String? = null,
    val erreurTaux: String? = null,
    val erreurSolde: String? = null
) {
    val estValide: Boolean
        get() = nom.isNotBlank() &&
                erreurNom == null &&
                erreurLimite == null &&
                erreurTaux == null &&
                erreurSolde == null
}

/**
 * Statistiques calculées pour une carte de crédit.
 */
data class StatistiquesCarteCredit(
    val creditDisponible: Double,
    val tauxUtilisation: Double,
    val interetsMensuels: Double,
    val paiementMinimum: Double,
    val tempsRemboursementMinimum: Int?,
    val totalInteretsAnnuels: Double
)
