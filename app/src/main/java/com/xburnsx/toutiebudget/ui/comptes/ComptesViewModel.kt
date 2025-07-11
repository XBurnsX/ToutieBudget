// chemin/simule: /ui/comptes/ComptesViewModel.kt
package com.xburnsx.toutiebudget.ui.comptes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
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
                    type = if (compte is CompteCheque) "Compte chèque" else "Carte de crédit"
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
            val form = _uiState.value.formState
            val soldeDouble = form.solde.toDoubleOrNull() ?: 0.0
            val nouveauCompte = when (form.type) {
                "Compte chèque" -> CompteCheque(UUID.randomUUID().toString(), "user_simule", form.nom, soldeDouble, form.couleur, false, (_uiState.value.comptesGroupes.values.flatten().size) + 1)
                "Carte de crédit" -> CompteCredit(UUID.randomUUID().toString(), "user_simule", form.nom, soldeDouble, form.couleur, false, (_uiState.value.comptesGroupes.values.flatten().size) + 1, 0.0)
                else -> null
            }
            if (nouveauCompte != null) {
                compteRepository.creerCompte(nouveauCompte).onSuccess {
                    onFermerTousLesDialogues()
                    chargerComptes()
                }.onFailure { e -> _uiState.update { it.copy(erreur = e.message) } }
            }
        }
    }

    private fun sauvegarderModification() {
        viewModelScope.launch {
            val form = _uiState.value.formState
            val compteOriginal = _uiState.value.compteSelectionne ?: return@launch
            val soldeDouble = form.solde.toDoubleOrNull() ?: 0.0
            val compteModifie = when (compteOriginal) {
                is CompteCheque -> compteOriginal.copy(nom = form.nom, solde = soldeDouble, couleur = form.couleur)
                is CompteCredit -> compteOriginal.copy(nom = form.nom, solde = soldeDouble, couleur = form.couleur)
            }
            compteRepository.mettreAJourCompte(compteModifie).onSuccess {
                onFermerTousLesDialogues()
                chargerComptes()
            }.onFailure { e -> _uiState.update { it.copy(erreur = e.message) } }
        }
    }

    fun onArchiverCompte() {
        viewModelScope.launch {
            val compteAArchiver = _uiState.value.compteSelectionne ?: return@launch
            val compteModifie = when (compteAArchiver) {
                is CompteCheque -> compteAArchiver.copy(estArchive = true)
                is CompteCredit -> compteAArchiver.copy(estArchive = true)
            }
            compteRepository.mettreAJourCompte(compteModifie).onSuccess {
                _uiState.update { it.copy(isMenuContextuelVisible = false, compteSelectionne = null) }
                chargerComptes()
            }.onFailure { e -> _uiState.update { it.copy(erreur = e.message) } }
        }
    }
}
