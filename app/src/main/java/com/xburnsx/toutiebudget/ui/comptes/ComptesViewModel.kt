// chemin/simule: /ui/comptes/ComptesViewModel.kt
package com.xburnsx.toutiebudget.ui.comptes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.data.modeles.CompteInvestissement
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

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
                println("[REALTIME] 🔄 Comptes mis à jour automatiquement")
                chargerComptes()
            }
        }
    }

    fun chargerComptes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            compteRepository.recupererTousLesComptes().onSuccess { comptes ->
                val comptesGroupes = comptes.groupBy {
                    when (it) {
                        is CompteCheque -> "Comptes chèques"
                        is CompteCredit -> "Cartes de crédit"
                        is CompteDette -> "Dettes"
                        is CompteInvestissement -> "Investissements"
                    }
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
                println("🔍 [DEBUG] === RÉCONCILIATION ===")
                println("🔍 [DEBUG] Compte: ${compte.nom}")
                println("🔍 [DEBUG] Ancien solde: ${compte.solde}")
                println("🔍 [DEBUG] Nouveau solde: $nouveauSolde")

                when (compte) {
                    is CompteCheque -> {
                        // CALCUL CORRECT : Si solde augmente de 100$, prêt à placer augmente de 100$
                        val difference = nouveauSolde - compte.solde
                        val nouveauPretAPlacer = compte.pretAPlacer + difference

                        println("🔍 [DEBUG] Différence: $difference")
                        println("🔍 [DEBUG] Ancien prêt à placer: ${compte.pretAPlacer}")
                        println("🔍 [DEBUG] Nouveau prêt à placer: $nouveauPretAPlacer")

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
                            println("✅ [DEBUG] Réconciliation réussie")
                            _uiState.update { it.copy(isReconciliationDialogVisible = false, compteSelectionne = null) }
                            chargerComptes()
                            onCompteChange?.invoke()
                        }.onFailure { erreur ->
                            println("❌ [DEBUG] Erreur: ${erreur.message}")
                            _uiState.update { it.copy(erreur = "Erreur réconciliation: ${erreur.message}") }
                        }
                    }
                    else -> {
                        // Pour les autres types de comptes, juste changer le solde
                        val compteReconcilie = when (compte) {
                            is CompteCredit -> compte.copy(solde = nouveauSolde)
                            is CompteDette -> compte.copy(solde = nouveauSolde)
                            is CompteInvestissement -> compte.copy(solde = nouveauSolde)
                            else -> return@launch
                        }

                        compteRepository.mettreAJourCompte(compteReconcilie).onSuccess {
                            println("✅ [DEBUG] Réconciliation réussie")
                            _uiState.update { it.copy(isReconciliationDialogVisible = false, compteSelectionne = null) }
                            chargerComptes()
                            onCompteChange?.invoke()
                        }.onFailure { erreur ->
                            println("❌ [DEBUG] Erreur: ${erreur.message}")
                            _uiState.update { it.copy(erreur = "Erreur réconciliation: ${erreur.message}") }
                        }
                    }
                }
            } catch (e: Exception) {
                println("❌ [DEBUG] Exception: ${e.message}")
                e.printStackTrace()
                _uiState.update { it.copy(erreur = "Erreur réconciliation: ${e.message}") }
            }
        }
    }

    fun onArchiverCompte() {
        val compte = _uiState.value.compteSelectionne ?: return
        viewModelScope.launch {
            try {
                println("🔍 [DEBUG] === ARCHIVAGE ===")
                println("🔍 [DEBUG] Compte: ${compte.nom}")
                println("🔍 [DEBUG] ID: ${compte.id}")
                println("🔍 [DEBUG] estArchive avant: ${compte.estArchive}")

                val compteArchive = when (compte) {
                    is CompteCheque -> {
                        println("🔍 [DEBUG] Archivage CompteCheque")
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
                        println("🔍 [DEBUG] Archivage CompteCredit")
                        CompteCredit(
                            id = compte.id,
                            utilisateurId = compte.utilisateurId,
                            nom = compte.nom,
                            solde = compte.solde,
                            couleur = compte.couleur,
                            estArchive = true, // ← FORCER à true
                            ordre = compte.ordre,
                            limiteCredit = compte.limiteCredit,
                            interet = compte.interet,
                            collection = "comptes_credits"
                        )
                    }
                    is CompteDette -> {
                        println("🔍 [DEBUG] Archivage CompteDette")
                        CompteDette(
                            id = compte.id,
                            utilisateurId = compte.utilisateurId,
                            nom = compte.nom,
                            solde = compte.solde,
                            estArchive = true, // ← FORCER à true
                            ordre = compte.ordre,
                            montantInitial = compte.montantInitial,
                            interet = compte.interet,
                            collection = "comptes_dettes"
                        )
                    }
                    is CompteInvestissement -> {
                        println("🔍 [DEBUG] Archivage CompteInvestissement")
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

                println("🔍 [DEBUG] estArchive après création: ${compteArchive.estArchive}")
                println("🔍 [DEBUG] Envoi au repository...")

                compteRepository.mettreAJourCompte(compteArchive).onSuccess {
                    println("✅ [DEBUG] Archivage réussi dans la base de données")
                    _uiState.update { it.copy(isMenuContextuelVisible = false, compteSelectionne = null) }

                    // FORCER LE RAFRAÎCHISSEMENT IMMÉDIAT DES DEUX ÉCRANS
                    chargerComptes() // Rafraîchit CompteScreen

                    // DÉCLENCHER LA MISE À JOUR TEMPS RÉEL POUR LE BUDGET
                    realtimeSyncService.declencherMiseAJourBudget()
                    realtimeSyncService.declencherMiseAJourComptes()

                    // FORCER LE CALLBACK POUR BUDGET SCREEN
                    onCompteChange?.invoke()

                    println("🔄 [DEBUG] Rafraîchissement forcé des écrans après archivage")
                }.onFailure { erreur ->
                    println("❌ [DEBUG] Erreur archivage: ${erreur.message}")
                    _uiState.update { it.copy(erreur = "Erreur archivage: ${erreur.message}") }
                }
            } catch (e: Exception) {
                println("❌ [DEBUG] Exception archivage: ${e.message}")
                e.printStackTrace()
                _uiState.update { it.copy(erreur = "Erreur archivage: ${e.message}") }
            }
        }
    }

    fun onOuvrirAjoutDialog() {
        _uiState.update { it.copy(isAjoutDialogVisible = true, formState = CompteFormState()) }
    }

    fun onOuvrirClavierNumerique() {
        _uiState.update { it.copy(isClavierNumeriqueVisible = true) }
    }

    fun onFermerClavierNumerique() {
        _uiState.update { it.copy(isClavierNumeriqueVisible = false) }
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
        val form = _uiState.value.formState
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
                "Carte de crédit" -> CompteCredit(nom = formState.nom, solde = soldeInitial, couleur = formState.couleur, estArchive = false, ordre = 0, limiteCredit = 0.0)
                "Dette" -> CompteDette(nom = formState.nom, solde = soldeInitial, estArchive = false, ordre = 0, montantInitial = 0.0)
                "Investissement" -> CompteInvestissement(nom = formState.nom, solde = soldeInitial, couleur = formState.couleur, estArchive = false, ordre = 0)
                else -> throw IllegalArgumentException("Type de compte inconnu")
            }
            compteRepository.creerCompte(nouveauCompte).onSuccess {
                chargerComptes()
                onFermerTousLesDialogues()

                // 🚀 DÉCLENCHER LA MISE À JOUR TEMPS RÉEL POUR TOUTES LES PAGES
                realtimeSyncService.declencherMiseAJourBudget()
                realtimeSyncService.declencherMiseAJourComptes()

                // Notifier les autres ViewModels du changement
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

                println("🔍 [DEBUG] === MODIFICATION COMPTE ===")
                println("🔍 [DEBUG] Compte original: ${compteOriginal.nom}")
                println("🔍 [DEBUG] ID: ${compteOriginal.id}")
                println("🔍 [DEBUG] Form.nom: '${form.nom}'")
                println("🔍 [DEBUG] Form.solde: '${form.solde}' -> $soldeDouble")
                println("🔍 [DEBUG] Form.pretAPlacer: '${form.pretAPlacer}' -> $pretAPlacerDouble")

                val compteModifie = when (compteOriginal) {
                    is CompteCheque -> {
                        println("🔍 [DEBUG] Modification CompteCheque")
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
                        println("🔍 [DEBUG] Modification CompteCredit")
                        CompteCredit(
                            id = compteOriginal.id,
                            utilisateurId = compteOriginal.utilisateurId,
                            nom = form.nom,
                            solde = soldeDouble,
                            couleur = form.couleur,
                            estArchive = compteOriginal.estArchive,
                            ordre = compteOriginal.ordre,
                            limiteCredit = compteOriginal.limiteCredit,
                            interet = compteOriginal.interet,
                            collection = "comptes_credits"
                        )
                    }
                    is CompteDette -> {
                        println("🔍 [DEBUG] Modification CompteDette")
                        CompteDette(
                            id = compteOriginal.id,
                            utilisateurId = compteOriginal.utilisateurId,
                            nom = form.nom,
                            solde = soldeDouble,
                            estArchive = compteOriginal.estArchive,
                            ordre = compteOriginal.ordre,
                            montantInitial = compteOriginal.montantInitial,
                            interet = compteOriginal.interet,
                            collection = "comptes_dettes"
                        )
                    }
                    is CompteInvestissement -> {
                        println("🔍 [DEBUG] Modification CompteInvestissement")
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

                println("🔍 [DEBUG] Compte modifié créé, envoi repository...")

                compteRepository.mettreAJourCompte(compteModifie).onSuccess {
                    println("✅ [DEBUG] Modification réussie dans la base de données")
                    _uiState.update { it.copy(isModificationDialogVisible = false, compteSelectionne = null) }
                    chargerComptes()
                    realtimeSyncService.declencherMiseAJourBudget()
                    realtimeSyncService.declencherMiseAJourComptes()
                    onCompteChange?.invoke()
                }.onFailure { e ->
                    println("❌ [DEBUG] Erreur modification: ${e.message}")
                    _uiState.update { it.copy(erreur = "Erreur modification: ${e.message}") }
                }
            } catch (e: Exception) {
                println("❌ [DEBUG] Exception modification: ${e.message}")
                e.printStackTrace()
                _uiState.update { it.copy(erreur = "Erreur modification: ${e.message}") }
            }
        }
    }
}
