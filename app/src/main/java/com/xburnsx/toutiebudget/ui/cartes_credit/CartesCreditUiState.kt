package com.xburnsx.toutiebudget.ui.cartes_credit

import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.EmetteurCarte
import com.xburnsx.toutiebudget.data.modeles.ReleveCarte
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeFrais
import com.xburnsx.toutiebudget.data.services.AlerteCarteCredit

/**
 * États de l'interface utilisateur pour la gestion des cartes de crédit.
 */
data class CartesCreditUiState(
    val cartesCredit: List<CompteCredit> = emptyList(),
    val estEnChargement: Boolean = false,
    val messageErreur: String? = null,
    val messageInfo: String? = null,
    val carteSelectionnee: CompteCredit? = null,
    val transactionsCarte: List<Transaction> = emptyList(),
    val alertes: List<AlerteCarteCredit> = emptyList(),
    val afficherDialogModification: Boolean = false,
    val afficherDialogModificationFrais: Boolean = false,
    /** Relevé en cours d'édition (nouveau ou existant) ; null = dialogue fermé. */
    val releveEnEdition: ReleveCarte? = null,
    val enregistrementEnCours: Boolean = false
)

/**
 * Formulaire de la carte (CC3, CC4, CC7, CC10). Les montants sont du texte saisi :
 * le solde est SIGNÉ tel quel (-400,00 = dette de 400 $), sans inversion cachée.
 */
data class FormulaireCarteCredit(
    val nom: String = "",
    val emetteur: EmetteurCarte = EmetteurCarte.AUTRE,
    val soldeActuel: String = "",
    val limiteCredit: String = "",
    val tauxInteret: String = "",
    val tauxAvances: String = "",
    val tauxTransferts: String = "",
    val tauxPromo: String = "",
    val finPromo: String = "",
    val tauxRetard: String = "",
    val jourCloture: String = "",
    val joursAvantEcheance: String = "",
    val pourcentageMinimum: String = "",
    val plancherMinimum: String = "",
    val paiementMinimum: String = "",
    val couleur: String = "#2196F3",
    // Ajout d'un frais (CC10)
    val nomFrais: String = "",
    val montantFrais: String = "",
    val typeFrais: TypeFrais = TypeFrais.MENSUEL,
    val dateFrais: String = "",
    val erreurs: Map<String, String> = emptyMap()
) {
    val estValide: Boolean get() = erreurs.isEmpty() && nom.isNotBlank()
}
