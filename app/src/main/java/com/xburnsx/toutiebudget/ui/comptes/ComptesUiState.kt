// chemin/simule: /ui/comptes/ComptesUiState.kt
package com.xburnsx.toutiebudget.ui.comptes

import com.xburnsx.toutiebudget.data.modeles.Compte

data class CompteFormState(
    val id: String? = null,
    val nom: String = "",
    val type: String = "Compte chèque",
    val solde: String = "",
    val pretAPlacer: String = "",
    val couleur: String = "#2196F3",
    // CC3 : création d'une carte de crédit
    val soldeCarte: String = "",
    val tauxCarte: String = "",
    val emetteurCarte: com.xburnsx.toutiebudget.data.modeles.EmetteurCarte = com.xburnsx.toutiebudget.data.modeles.EmetteurCarte.AUTRE,
    val detteDateDebut: String = "",
    val detteDureeMois: String = "",
    val detteVersementMensuel: String = "",
    val detteTauxAnnuel: String = "",
    val detteFrequence: com.xburnsx.toutiebudget.data.modeles.FrequencePaiementDette =
        com.xburnsx.toutiebudget.data.modeles.FrequencePaiementDette.MENSUELLE,
    // IA7 : type de compte de placement (métadonnée)
    val typePlacement: com.xburnsx.toutiebudget.data.modeles.TypeComptePlacement = com.xburnsx.toutiebudget.data.modeles.TypeComptePlacement.NON_PRECISE
)

data class ComptesUiState(
    val isLoading: Boolean = true,
    val erreur: String? = null,
    val comptesGroupes: Map<String, List<Compte>> = emptyMap(),
    val compteSelectionne: Compte? = null,
    val isAjoutDialogVisible: Boolean = false,
    val isModificationDialogVisible: Boolean = false,
    val isReconciliationDialogVisible: Boolean = false,
    val isMenuContextuelVisible: Boolean = false,
    val formState: CompteFormState = CompteFormState(),
    val isClavierNumeriqueVisible: Boolean = false,
    // 🆕 PROPRIÉTÉS POUR LA RÉORGANISATION
    val isModeReorganisation: Boolean = false,
    val compteEnDeplacement: String? = null,
    val versionUI: Int = 0, // 🔥 COMPTEUR DE VERSION POUR FORCER LA RECOMPOSITION
    val comptesArchives: List<Compte> = emptyList(), // CO3 : liste propre à l'écran Archives
    // ARC14 : chargement, erreur réelle et résultat de réactivation propres à l'écran Archives
    val archivesEnChargement: Boolean = false,
    val erreurArchives: String? = null,
    val messageArchives: String? = null,
    val isConfirmationArchivageVisible: Boolean = false, // CO7
    val enregistrementEnCours: Boolean = false // CO6
)
