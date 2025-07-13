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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ComptesViewModel(
    private val compteRepository: CompteRepository
) : ViewModel() {

    // Callback pour notifier les autres ViewModels des changements
    var onCompteChange: (() -> Unit)? = null

    private val _uiState = MutableStateFlow(ComptesUiState())
    val uiState: StateFlow<ComptesUiState> = _uiState.asStateFlow()

    init {
        chargerComptes()
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
                compteSelectionne = null
            )
        }
    }

    fun onFormValueChange(nom: String? = null, type: String? = null, solde: String? = null, couleur: String? = null) {
        _uiState.update { currentState ->
            currentState.copy(
                formState = currentState.formState.copy(
                    nom = nom ?: currentState.formState.nom,
                    type = type ?: currentState.formState.type,
                    solde = solde ?: currentState.formState.solde,
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
    }

    private fun creerNouveauCompte() {
        viewModelScope.launch {
            val formState = _uiState.value.formState
            val soldeInitial = formState.solde.toDoubleOrNull() ?: 0.0
            val nouveauCompte = when(formState.type) {
                "Compte chèque" -> CompteCheque(
                    nom = formState.nom,
                    solde = soldeInitial,
                    pretAPlacerRaw = soldeInitial, // Initialiser pret_a_placer avec le solde initial
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
                // Notifier les autres ViewModels du changement
                onCompteChange?.invoke()
            }.onFailure {
                // Gérer l'erreur
            }
        }
    }

    private fun sauvegarderModification() {
        viewModelScope.launch {
            val form = _uiState.value.formState
            val compteOriginal = _uiState.value.compteSelectionne ?: return@launch
            val soldeDouble = form.solde.toDoubleOrNull() ?: 0.0
            val compteModifie = when (compteOriginal) {
                is CompteCheque -> compteOriginal.copy(
                    nom = form.nom,
                    solde = soldeDouble,
                    couleur = form.couleur
                    // Note: pretAPlacerRaw est préservé automatiquement par copy()
                )
                is CompteCredit -> compteOriginal.copy(nom = form.nom, solde = soldeDouble, couleur = form.couleur)
                is CompteDette -> compteOriginal.copy(nom = form.nom, solde = soldeDouble)
                is CompteInvestissement -> compteOriginal.copy(nom = form.nom, solde = soldeDouble, couleur = form.couleur)
            }
            compteRepository.mettreAJourCompte(compteModifie).onSuccess {
                onFermerTousLesDialogues()
                chargerComptes()
                // Notifier les autres ViewModels du changement
                onCompteChange?.invoke()
            }.onFailure { e -> _uiState.update { it.copy(erreur = e.message) } }
        }
    }

    fun onArchiverCompte() {
        viewModelScope.launch {
            val compteAArchiver = _uiState.value.compteSelectionne ?: return@launch
            val compteModifie = when (compteAArchiver) {
                is CompteCheque -> compteAArchiver.copy(estArchive = true)
                is CompteCredit -> compteAArchiver.copy(estArchive = true)
                is CompteDette -> compteAArchiver.copy(estArchive = true)
                is CompteInvestissement -> compteAArchiver.copy(estArchive = true)
            }
            compteRepository.mettreAJourCompte(compteModifie).onSuccess {
                _uiState.update { it.copy(isMenuContextuelVisible = false, compteSelectionne = null) }
                chargerComptes()
            }.onFailure { e -> _uiState.update { it.copy(erreur = e.message) } }
        }
    }
}
