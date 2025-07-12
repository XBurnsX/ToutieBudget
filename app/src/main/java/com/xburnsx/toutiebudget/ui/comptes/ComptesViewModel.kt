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

    private val _uiState = MutableStateFlow(ComptesUiState())
    val uiState: StateFlow<ComptesUiState> = _uiState.asStateFlow()

    // Cache pour éviter les rechargements visibles
    private var donneesCachees: ComptesUiState? = null

    init {
        // État initial sans chargement visible
        _uiState.update { it.copy(isLoading = false) }
        chargerComptes()
    }

    fun chargerComptes() {
        // Si on a des données en cache, les afficher immédiatement
        donneesCachees?.let { cache ->
            _uiState.update { cache }
        }
        
        // Puis charger en arrière-plan
        chargerComptesSilencieusement()
    }

    private fun chargerComptesSilencieusement() {
        viewModelScope.launch {
            compteRepository.recupererTousLesComptes().onSuccess { comptes ->
                val comptesGroupes = comptes.groupBy {
                    when (it) {
                        is CompteCheque -> "Comptes chèques"
                        is CompteCredit -> "Cartes de crédit"
                        is CompteDette -> "Dettes"
                        is CompteInvestissement -> "Investissements"
                    }
                }
                val nouvelEtat = ComptesUiState(
                    isLoading = false,
                    comptesGroupes = comptesGroupes
                )
                
                // Mettre en cache et mettre à jour l'UI
                donneesCachees = nouvelEtat
                _uiState.update { nouvelEtat }
            }.onFailure { erreur ->
                // Erreur silencieuse - on garde les données précédentes si disponibles
                if (donneesCachees == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false, 
                            erreur = "Impossible de charger les comptes: ${erreur.message}"
                        )
                    }
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
            val nouveauCompte = when(formState.type) {
                "Compte chèque" -> CompteCheque(nom = formState.nom, solde = formState.solde.toDoubleOrNull() ?: 0.0, couleur = formState.couleur, estArchive = false, ordre = 0)
                "Carte de crédit" -> CompteCredit(nom = formState.nom, solde = formState.solde.toDoubleOrNull() ?: 0.0, couleur = formState.couleur, estArchive = false, ordre = 0, limiteCredit = 0.0) // Limite à définir
                "Dette" -> CompteDette(nom = formState.nom, solde = formState.solde.toDoubleOrNull() ?: 0.0, estArchive = false, ordre = 0, montantInitial = 0.0) // Montant initial à définir
                "Investissement" -> CompteInvestissement(nom = formState.nom, solde = formState.solde.toDoubleOrNull() ?: 0.0, couleur = formState.couleur, estArchive = false, ordre = 0)
                else -> throw IllegalArgumentException("Type de compte inconnu")
            }
            compteRepository.creerCompte(nouveauCompte).onSuccess {
                chargerComptesSilencieusement()
                onFermerTousLesDialogues()
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
                is CompteCheque -> compteOriginal.copy(nom = form.nom, solde = soldeDouble, couleur = form.couleur)
                is CompteCredit -> compteOriginal.copy(nom = form.nom, solde = soldeDouble, couleur = form.couleur)
                is CompteDette -> compteOriginal.copy(nom = form.nom, solde = soldeDouble)
                is CompteInvestissement -> compteOriginal.copy(nom = form.nom, solde = soldeDouble, couleur = form.couleur)
            }
            compteRepository.mettreAJourCompte(compteModifie).onSuccess {
                onFermerTousLesDialogues()
                chargerComptesSilencieusement()
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
                chargerComptesSilencieusement()
            }.onFailure { e -> _uiState.update { it.copy(erreur = e.message) } }
        }
    }
}
