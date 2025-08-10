// chemin/simule: /ui/comptes/ComptesViewModel.kt
package com.xburnsx.toutiebudget.ui.comptes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.data.modeles.CompteInvestissement
import com.xburnsx.toutiebudget.data.modeles.Categorie
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ComptesViewModel(
    private val compteRepository: CompteRepository,
    private val realtimeSyncService: RealtimeSyncService
) : ViewModel() {

    // Callback pour notifier les autres ViewModels des changements
    var onCompteChange: (() -> Unit)? = null

    private val _uiState = MutableStateFlow(ComptesUiState())
    val uiState: StateFlow<ComptesUiState> = _uiState.asStateFlow()

    init {
        chargerComptes()

        // 🚀 TEMPS RÉEL : Écoute des changements PocketBase
        viewModelScope.launch {
            realtimeSyncService.comptesUpdated.collectLatest {
                chargerComptes()
            }
        }
    }

    private fun chargerComptesInternal(showArchived: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            compteRepository.recupererTousLesComptes().onSuccess { comptes ->
                val comptesGroupes = comptes.groupBy {
                    when (it) {
                        is CompteCheque -> "Comptes chèques"
                        is CompteCredit -> "Cartes de crédit"
                        is CompteDette -> "Dettes"
                        is CompteInvestissement -> "Investissements"
                    }
                }.mapValues { (_, comptesType) ->
                    comptesType.filter { showArchived || !it.estArchive }.sortedBy { it.ordre }
                }
                _uiState.update {
                    it.copy(isLoading = false, comptesGroupes = comptesGroupes)
                }
            }.onFailure { erreur ->
                _uiState.update {
                    it.copy(isLoading = false, erreur = "Impossible de charger les comptes: ${erreur.message}")
                }
            }
        }
    }

    fun chargerComptes() = chargerComptesInternal(showArchived = false)

    fun chargerComptesArchives() = chargerComptesInternal(showArchived = true)

    fun onCompteLongPress(compte: Compte) {
        _uiState.update { it.copy(compteSelectionne = compte, isMenuContextuelVisible = true) }
    }

    fun onDismissMenu() {
        _uiState.update { it.copy(isMenuContextuelVisible = false) }
    }

    fun onOuvrirReconciliationDialog() {
        _uiState.update { 
            it.copy(
                isReconciliationDialogVisible = true,
                isMenuContextuelVisible = false
            ) 
        }
    }

    fun onReconcilierCompte(nouveauSolde: Double) {
        val compte = _uiState.value.compteSelectionne ?: return
        viewModelScope.launch {
            try {
                when (compte) {
                    is CompteCheque -> {
                        // CALCUL CORRECT : Si solde augmente de 100$, prêt à placer augmente de 100$
                        val difference = nouveauSolde - compte.solde
                        val nouveauPretAPlacer = compte.pretAPlacer + difference

                        val compteReconcilie = CompteCheque(
                            id = compte.id,
                            utilisateurId = compte.utilisateurId,
                            nom = compte.nom,
                            solde = nouveauSolde,
                            pretAPlacerRaw = nouveauPretAPlacer,
                            couleur = compte.couleur,
                            estArchive = compte.estArchive,
                            ordre = compte.ordre,
                            collection = "comptes_cheques"
                        )

                        compteRepository.mettreAJourCompte(compteReconcilie).onSuccess {
                            _uiState.update { it.copy(isReconciliationDialogVisible = false, compteSelectionne = null) }
                            chargerComptes()
                            onCompteChange?.invoke()
                        }.onFailure { erreur ->
                            _uiState.update { it.copy(erreur = "Erreur réconciliation: ${erreur.message}") }
                        }
                    }
                    else -> {
                        // Pour les autres types de comptes, juste changer le solde
                        val compteReconcilie = when (compte) {
                            is CompteCredit -> compte.copy(soldeUtilise = nouveauSolde, collection = "comptes_credits") // Changé solde vers soldeUtilise
                                                         is CompteDette -> compte.copy(soldeDette = nouveauSolde)
                            is CompteInvestissement -> compte.copy(solde = nouveauSolde)
                            else -> return@launch
                        }

                        compteRepository.mettreAJourCompte(compteReconcilie).onSuccess {
                            _uiState.update { it.copy(isReconciliationDialogVisible = false, compteSelectionne = null) }
                            chargerComptes()
                            onCompteChange?.invoke()
                        }.onFailure { erreur ->
                            _uiState.update { it.copy(erreur = "Erreur réconciliation: ${erreur.message}") }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur réconciliation: ${e.message}") }
            }
        }
    }

    fun onArchiverCompte() {
        val compte = _uiState.value.compteSelectionne ?: return
        viewModelScope.launch {
            try {
                val compteArchive = when (compte) {
                    is CompteCheque -> {
                        CompteCheque(
                            id = compte.id,
                            utilisateurId = compte.utilisateurId,
                            nom = compte.nom,
                            solde = compte.solde,
                            pretAPlacerRaw = compte.pretAPlacerRaw,
                            couleur = compte.couleur,
                            estArchive = true, // ← FORCER à true
                            ordre = compte.ordre,
                            collection = "comptes_cheques"
                        )
                    }
                    is CompteCredit -> {
                        CompteCredit(
                            id = compte.id,
                            utilisateurId = compte.utilisateurId,
                            nom = compte.nom,
                            soldeUtilise = compte.soldeUtilise, // Utiliser soldeUtilise au lieu de solde
                            couleur = compte.couleur,
                            estArchive = true, // ← FORCER à true
                            ordre = compte.ordre,
                            limiteCredit = compte.limiteCredit,
                            tauxInteret = compte.tauxInteret, // Corriger interet vers tauxInteret
                            collection = "comptes_credits"
                        )
                    }
                                         is CompteDette -> {
                         CompteDette(
                             id = compte.id,
                             utilisateurId = compte.utilisateurId,
                             nom = compte.nom,
                             soldeDette = compte.soldeDette,
                             estArchive = true, // ← FORCER à true
                             ordre = compte.ordre,
                             montantInitial = compte.montantInitial,
                             tauxInteret = compte.tauxInteret,
                             collection = "comptes_dettes"
                         )
                     }
                    is CompteInvestissement -> {
                        CompteInvestissement(
                            id = compte.id,
                            utilisateurId = compte.utilisateurId,
                            nom = compte.nom,
                            solde = compte.solde,
                            couleur = compte.couleur,
                            estArchive = true, // ← FORCER à true
                            ordre = compte.ordre,
                            collection = "comptes_investissement"
                        )
                    }
                }

                compteRepository.mettreAJourCompte(compteArchive).onSuccess {
                    // Si dette archivée: supprimer enveloppe associée et nettoyer la catégorie si vide
                    try {
                        if (compte is CompteDette) {
                            val categorieRepository = com.xburnsx.toutiebudget.data.repositories.impl.CategorieRepositoryImpl()
                            val enveloppeRepository = com.xburnsx.toutiebudget.data.repositories.impl.EnveloppeRepositoryImpl()
                            // Trouver l'enveloppe portant le même nom dans la catégorie Dettes
                            val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrElse { emptyList() }
                            val categories = categorieRepository.recupererToutesLesCategories().getOrElse { emptyList() }
                            val catDettes = categories.firstOrNull { it.nom.equals("Dettes", ignoreCase = true) }
                            val envDette = enveloppes.firstOrNull { it.nom.equals(compte.nom, ignoreCase = true) && (catDettes == null || it.categorieId == catDettes.id) }
                            envDette?.let { enveloppeRepository.supprimerEnveloppe(it.id) }
                            // Si la catégorie Dettes est vide après suppression, la supprimer
                            if (catDettes != null) {
                                val reste = enveloppes.any { it.id != envDette?.id && it.categorieId == catDettes.id }
                                if (!reste) {
                                    categorieRepository.supprimerCategorie(catDettes.id)
                                }
                            }
                        }
                    } catch (_: Exception) { }
                    _uiState.update { it.copy(isMenuContextuelVisible = false, compteSelectionne = null) }

                    // FORCER LE RAFRAÎCHISSEMENT IMMÉDIAT DES DEUX ÉCRANS
                    chargerComptes() // Rafraîchit CompteScreen

                    // DÉCLENCHER LA MISE À JOUR TEMPS RÉEL POUR LE BUDGET
                    realtimeSyncService.declencherMiseAJourBudget()
                    realtimeSyncService.declencherMiseAJourComptes()

                    // FORCER LE CALLBACK POUR BUDGET SCREEN
                    onCompteChange?.invoke()

                }.onFailure { erreur ->
                    _uiState.update { it.copy(erreur = "Erreur archivage: ${erreur.message}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur archivage: ${e.message}") }
            }
        }
    }

    fun onOuvrirAjoutDialog() {
        _uiState.update { it.copy(isAjoutDialogVisible = true, formState = CompteFormState()) }
    }

    fun onOuvrirModificationDialog() {
        val compte = _uiState.value.compteSelectionne ?: return
        _uiState.update {
            it.copy(
                isModificationDialogVisible = true,
                isMenuContextuelVisible = false,
                formState = CompteFormState(
                    id = compte.id,
                    nom = compte.nom,
                    solde = compte.solde.toString(),
                    pretAPlacer = if (compte is CompteCheque) compte.pretAPlacer.toString() else "",
                    couleur = compte.couleur,
                    type = when (compte) {
                        is CompteCheque -> "Compte chèque"
                        is CompteCredit -> "Carte de crédit"
                        is CompteDette -> "Dette"
                        is CompteInvestissement -> "Investissement"
                    }
                )
            )
        }
    }

    fun onFermerTousLesDialogues() {
        _uiState.update {
            it.copy(
                isAjoutDialogVisible = false,
                isModificationDialogVisible = false,
                isReconciliationDialogVisible = false,
                isMenuContextuelVisible = false,
                isClavierNumeriqueVisible = false
            )
        }
    }

    fun onFormValueChange(nom: String? = null, type: String? = null, solde: String? = null, pretAPlacer: String? = null, couleur: String? = null) {
        _uiState.update { currentState ->
            currentState.copy(
                formState = currentState.formState.copy(
                    nom = nom ?: currentState.formState.nom,
                    type = type ?: currentState.formState.type,
                    solde = solde ?: currentState.formState.solde,
                    pretAPlacer = pretAPlacer ?: currentState.formState.pretAPlacer,
                    couleur = couleur ?: currentState.formState.couleur
                )
            )
        }
    }

    fun onSauvegarderCompte() {
        if (_uiState.value.formState.id != null) {
            sauvegarderModification()
        } else {
            creerNouveauCompte()
        }
        onFermerTousLesDialogues() // Fermer tout après la sauvegarde
    }

    private fun creerNouveauCompte() {
        viewModelScope.launch {
            val formState = _uiState.value.formState
            val soldeInitial = formState.solde.toDoubleOrNull() ?: 0.0
            val nouveauCompte = when(formState.type) {
                "Compte chèque" -> CompteCheque(
                    nom = formState.nom,
                    solde = soldeInitial,
                    pretAPlacerRaw = soldeInitial, // Pour l'ajout, prêt à placer = solde initial
                    couleur = formState.couleur,
                    estArchive = false,
                    ordre = 0
                )
                "Carte de crédit" -> CompteCredit(
                    nom = formState.nom,
                    soldeUtilise = 0.0, // Pas de dette initiale - utiliser soldeUtilise
                    couleur = formState.couleur,
                    estArchive = false,
                    ordre = 0,
                    limiteCredit = soldeInitial // Le montant saisi devient la limite de crédit
                )
                "Dette" -> CompteDette(
                    nom = formState.nom,
                    soldeDette = -soldeInitial, // dette négative à la création
                    estArchive = false,
                    ordre = 0,
                    montantInitial = soldeInitial,
                    prixTotal = soldeInitial
                )
                "Investissement" -> CompteInvestissement(nom = formState.nom, solde = soldeInitial, couleur = formState.couleur, estArchive = false, ordre = 0)
                else -> throw IllegalArgumentException("Type de compte inconnu")
            }
            compteRepository.creerCompte(nouveauCompte).onSuccess {
                // Si c'est une dette, créer cat. "Dettes" + enveloppe correspondante
                if (nouveauCompte is CompteDette) {
                    try {
                        val utilisateurId = com.xburnsx.toutiebudget.di.PocketBaseClient.obtenirUtilisateurConnecte()?.id
                            ?: return@onSuccess
                        // Création via repositories directs
                        val categorieRepository = com.xburnsx.toutiebudget.data.repositories.impl.CategorieRepositoryImpl()
                        val enveloppeRepository = com.xburnsx.toutiebudget.data.repositories.impl.EnveloppeRepositoryImpl()

                    // 1) Assurer la catégorie "Dettes" et la forcer en première position
                    val categories = categorieRepository.recupererToutesLesCategories().getOrElse { emptyList() }
                    val catDettes = categories.firstOrNull { it.nom.equals("Dettes", ignoreCase = true) } ?: run {
                        val nouvelleCat = com.xburnsx.toutiebudget.data.modeles.Categorie(
                            id = "",
                            utilisateurId = utilisateurId,
                            nom = "Dettes",
                            ordre = 0
                        )
                        categorieRepository.creerCategorie(nouvelleCat).getOrNull() ?: nouvelleCat
                    }
                    try {
                        if (catDettes.ordre != 0) {
                            categorieRepository.mettreAJourCategorie(catDettes.copy(ordre = 0))
                        }
                        val autres = categories.filter { it.id != catDettes.id }.sortedBy { it.ordre }
                        autres.forEachIndexed { index, c ->
                            val targetOrdre = index + 1
                            if (c.ordre != targetOrdre) {
                                categorieRepository.mettreAJourCategorie(c.copy(ordre = targetOrdre))
                            }
                        }
                    } catch (_: Exception) {}

                        // 2) Créer l'enveloppe associée à la dette (nom = dette)
                        val env = com.xburnsx.toutiebudget.data.modeles.Enveloppe(
                        id = "",
                            utilisateurId = utilisateurId,
                            nom = nouveauCompte.nom,
                        categorieId = catDettes.id,
                        estArchive = false,
                        ordre = 0,
                        typeObjectif = com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Aucun,
                        objectifMontant = 0.0,
                        dateObjectif = null,
                        dateDebutObjectif = null,
                        objectifJour = null,
                        resetApresEcheance = false
                    )
                    enveloppeRepository.creerEnveloppe(env)
                    } catch (_: Exception) {}
                }

                // Si c'est une carte de crédit, créer cat. "Cartes de crédit" + enveloppe correspondante
                if (nouveauCompte is CompteCredit) {
                    try {
                        val utilisateurId = com.xburnsx.toutiebudget.di.PocketBaseClient.obtenirUtilisateurConnecte()?.id
                            ?: return@onSuccess
                        val categorieRepository = com.xburnsx.toutiebudget.data.repositories.impl.CategorieRepositoryImpl()
                        val enveloppeRepository = com.xburnsx.toutiebudget.data.repositories.impl.EnveloppeRepositoryImpl()

                        // Assurer catégories et ordre: Cartes de crédit (0), Dettes (1), autres (2+)
                        val categories = categorieRepository.recupererToutesLesCategories().getOrElse { emptyList() }
                        val catCartes = categories.firstOrNull { it.nom.equals("Cartes de crédit", ignoreCase = true) } ?: run {
                            val nouvelleCat = com.xburnsx.toutiebudget.data.modeles.Categorie(
                                id = "",
                                utilisateurId = utilisateurId,
                                nom = "Cartes de crédit",
                                ordre = 0
                            )
                            categorieRepository.creerCategorie(nouvelleCat).getOrNull() ?: nouvelleCat
                        }
                        val catDettes = categories.firstOrNull { it.nom.equals("Dettes", ignoreCase = true) }

                        // Forcer ordres
                        try {
                            if (catCartes.ordre != 0) categorieRepository.mettreAJourCategorie(catCartes.copy(ordre = 0))
                            if (catDettes != null && catDettes.ordre != 1) categorieRepository.mettreAJourCategorie(catDettes.copy(ordre = 1))
                            // Renuméroter autres à partir de 2
                            val bloqueIds = setOf(catCartes.id) + (catDettes?.id?.let { setOf(it) } ?: emptySet())
                            val autres = categories.filter { it.id !in bloqueIds }.sortedBy { it.ordre }
                            autres.forEachIndexed { index, c ->
                                val target = index + 2
                                if (c.ordre != target) categorieRepository.mettreAJourCategorie(c.copy(ordre = target))
                            }
                        } catch (_: Exception) {}

                        // Créer l'enveloppe associée à la carte
                        val env = com.xburnsx.toutiebudget.data.modeles.Enveloppe(
                            id = "",
                            utilisateurId = utilisateurId,
                            nom = nouveauCompte.nom,
                            categorieId = catCartes.id,
                            estArchive = false,
                            ordre = 0,
                            typeObjectif = com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Aucun,
                            objectifMontant = 0.0,
                            dateObjectif = null,
                            dateDebutObjectif = null,
                            objectifJour = null,
                            resetApresEcheance = false
                        )
                        enveloppeRepository.creerEnveloppe(env)
                    } catch (_: Exception) {}
                }

                chargerComptes()
                onFermerTousLesDialogues()

                realtimeSyncService.declencherMiseAJourBudget()
                realtimeSyncService.declencherMiseAJourComptes()

                onCompteChange?.invoke()
            }.onFailure {
                // Gérer l'erreur
            }
        }
    }

    private fun sauvegarderModification() {
        viewModelScope.launch {
            try {
                val form = _uiState.value.formState
                val compteOriginal = _uiState.value.compteSelectionne ?: return@launch
                val soldeDouble = form.solde.toDoubleOrNull() ?: 0.0
                val pretAPlacerDouble = form.pretAPlacer.toDoubleOrNull() ?: 0.0

                val compteModifie = when (compteOriginal) {
                    is CompteCheque -> {
                        CompteCheque(
                            id = compteOriginal.id,
                            utilisateurId = compteOriginal.utilisateurId,
                            nom = form.nom,
                            solde = soldeDouble,
                            pretAPlacerRaw = pretAPlacerDouble,
                            couleur = form.couleur,
                            estArchive = compteOriginal.estArchive,
                            ordre = compteOriginal.ordre,
                            collection = "comptes_cheques"
                        )
                    }
                    is CompteCredit -> {
                        CompteCredit(
                            id = compteOriginal.id,
                            utilisateurId = compteOriginal.utilisateurId,
                            nom = form.nom,
                            soldeUtilise = soldeDouble, // Changé solde vers soldeUtilise
                            couleur = form.couleur,
                            estArchive = compteOriginal.estArchive,
                            ordre = compteOriginal.ordre,
                            limiteCredit = compteOriginal.limiteCredit,
                            tauxInteret = compteOriginal.tauxInteret, // Changé interet vers tauxInteret
                            collection = "comptes_credits"
                        )
                    }
                    is CompteDette -> {
                        CompteDette(
                            id = compteOriginal.id,
                            utilisateurId = compteOriginal.utilisateurId,
                            nom = form.nom,
                            soldeDette = soldeDouble,
                            estArchive = compteOriginal.estArchive,
                            ordre = compteOriginal.ordre,
                            montantInitial = compteOriginal.montantInitial,
                            tauxInteret = compteOriginal.tauxInteret,
                            collection = "comptes_dettes"
                        )
                    }
                    is CompteInvestissement -> {
                        CompteInvestissement(
                            id = compteOriginal.id,
                            utilisateurId = compteOriginal.utilisateurId,
                            nom = form.nom,
                            solde = soldeDouble,
                            couleur = form.couleur,
                            estArchive = compteOriginal.estArchive,
                            ordre = compteOriginal.ordre,
                            collection = "comptes_investissement"
                        )
                    }
                }

                compteRepository.mettreAJourCompte(compteModifie).onSuccess {
                    _uiState.update { it.copy(isModificationDialogVisible = false, compteSelectionne = null) }
                    chargerComptes()
                    realtimeSyncService.declencherMiseAJourBudget()
                    realtimeSyncService.declencherMiseAJourComptes()
                    onCompteChange?.invoke()
                }.onFailure { e ->
                    _uiState.update { it.copy(erreur = "Erreur modification: ${e.message}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur modification: ${e.message}") }
            }
        }
    }

    // ===== GESTION DE LA RÉORGANISATION DES COMPTES =====

    /**
     * Active ou désactive le mode de réorganisation des comptes.
     */
    fun onToggleModeReorganisation() {
        _uiState.update {
            it.copy(
                isModeReorganisation = !it.isModeReorganisation,
                compteEnDeplacement = null
            )
        }
    }

    /**
     * Déplace un compte vers une nouvelle position.
     */
    fun onDeplacerCompte(compteId: String, nouvellePosition: Int) {
        viewModelScope.launch {
            try {
                // Obtenir tous les comptes non archivés
                val tousComptes = compteRepository.recupererTousLesComptes().getOrElse { emptyList() }
                val comptesActifs = tousComptes.filter { !it.estArchive }.sortedBy { it.ordre }

                // Trouver le compte à déplacer
                val compteADeplacer = comptesActifs.find { it.id == compteId }
                    ?: return@launch

                // Trouver le type de compte pour ne déplacer que dans le même groupe
                val typeCompteADeplacer = when (compteADeplacer) {
                    is CompteCheque -> "Comptes chèques"
                    is CompteCredit -> "Cartes de crédit"
                    is CompteDette -> "Dettes"
                    is CompteInvestissement -> "Investissements"
                }

                // Filtrer seulement les comptes du même type
                val comptesMemeType = comptesActifs.filter { compte ->
                    when (compte) {
                        is CompteCheque -> typeCompteADeplacer == "Comptes chèques"
                        is CompteCredit -> typeCompteADeplacer == "Cartes de crédit"
                        is CompteDette -> typeCompteADeplacer == "Dettes"
                        is CompteInvestissement -> typeCompteADeplacer == "Investissements"
                    }
                }.sortedBy { it.ordre }

                // Calculer les nouveaux ordres dans le groupe
                val nouveauxComptes = calculerNouveauxOrdresComptes(
                    comptesMemeType,
                    compteADeplacer,
                    nouvellePosition
                )

                // Mettre à jour tous les comptes modifiés
                val misesAJour = nouveauxComptes.filter { nouveau ->
                    val ancien = comptesMemeType.find { it.id == nouveau.id }
                    ancien?.ordre != nouveau.ordre
                }

                // Synchroniser avec PocketBase
                misesAJour.forEach { compte ->
                    compteRepository.mettreAJourCompte(compte).onFailure { erreur ->
                        _uiState.update { it.copy(erreur = "Erreur déplacement: ${erreur.message}") }
                        return@launch
                    }
                }

                // Recharger les données
                chargerComptes()

                // 🔥 FORCER LA RECOMPOSITION DE L'INTERFACE
                _uiState.update { currentState ->
                    currentState.copy(versionUI = currentState.versionUI + 1)
                }

                // Notifier les autres ViewModels
                realtimeSyncService.declencherMiseAJourComptes()

            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur: ${e.message}") }
            }
        }
    }

    /**
     * Calcule les nouveaux ordres après déplacement d'un compte.
     */
    private fun calculerNouveauxOrdresComptes(
        comptesOrdonnes: List<Compte>,
        compteADeplacer: Compte,
        nouvellePosition: Int
    ): List<Compte> {
        val listeModifiable = comptesOrdonnes.toMutableList()

        // Retirer le compte de sa position actuelle
        val positionActuelle = listeModifiable.indexOfFirst { it.id == compteADeplacer.id }
        if (positionActuelle == -1) return comptesOrdonnes

        listeModifiable.removeAt(positionActuelle)

        // Insérer à la nouvelle position
        val positionCible = nouvellePosition.coerceIn(0, listeModifiable.size)
        listeModifiable.add(positionCible, compteADeplacer)

        // Recalculer tous les ordres
        return listeModifiable.mapIndexed { index, compte ->
            when (compte) {
                is CompteCheque -> compte.copy(ordre = index, collection = "comptes_cheques")
                is CompteCredit -> compte.copy(ordre = index, collection = "comptes_credits")
                is CompteDette -> compte.copy(ordre = index, collection = "comptes_dettes")
                is CompteInvestissement -> compte.copy(ordre = index, collection = "comptes_investissement")
            }
        }
    }
}
