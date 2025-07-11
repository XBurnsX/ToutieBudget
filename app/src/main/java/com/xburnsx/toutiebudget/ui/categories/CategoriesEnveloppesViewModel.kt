// chemin/simule: /ui/categories/CategoriesEnveloppesViewModel.kt
package com.xburnsx.toutiebudget.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*

class CategoriesEnveloppesViewModel(
    private val enveloppeRepository: EnveloppeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesEnveloppesUiState())
    val uiState: StateFlow<CategoriesEnveloppesUiState> = _uiState.asStateFlow()

    init {
        chargerCategoriesEtEnveloppes()
    }

    fun chargerCategoriesEtEnveloppes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            enveloppeRepository.recupererToutesLesEnveloppes().onSuccess { enveloppes ->
                val groupes = enveloppes.filter { !it.estArchive }.groupBy { it.categorie }
                _uiState.update { it.copy(isLoading = false, enveloppesGroupees = groupes) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, erreur = e.message) }
            }
        }
    }

    fun onOuvrirAjoutCategorieDialog() {
        _uiState.update { it.copy(isAjoutCategorieDialogVisible = true, nomNouvelleCategorie = "") }
    }

    fun onOuvrirAjoutEnveloppeDialog(categorie: String) {
        _uiState.update { it.copy(isAjoutEnveloppeDialogVisible = true, nomNouvelleEnveloppe = "", categoriePourAjout = categorie) }
    }

    fun onFermerDialogues() {
        _uiState.update { it.copy(
            isAjoutCategorieDialogVisible = false,
            isAjoutEnveloppeDialogVisible = false,
            isObjectifDialogVisible = false
        )}
    }

    fun onNomNouvelleCategorieChange(nom: String) {
        _uiState.update { it.copy(nomNouvelleCategorie = nom) }
    }

    fun onNomNouvelleEnveloppeChange(nom: String) {
        _uiState.update { it.copy(nomNouvelleEnveloppe = nom) }
    }

    fun sauvegarderNouvelleCategorie() {
        onFermerDialogues()
        _uiState.update {
            val nouveauxGroupes = it.enveloppesGroupees.toMutableMap()
            nouveauxGroupes[it.nomNouvelleCategorie] = emptyList()
            it.copy(enveloppesGroupees = nouveauxGroupes)
        }
    }

    fun sauvegarderNouvelleEnveloppe() {
        viewModelScope.launch {
            val state = _uiState.value
            val nouvelleEnveloppe = Enveloppe(
                id = UUID.randomUUID().toString(),
                utilisateurId = "user_simule",
                nom = state.nomNouvelleEnveloppe,
                categorie = state.categoriePourAjout ?: "Autre",
                estArchive = false,
                ordre = (state.enveloppesGroupees.values.flatten().size) + 1
            )
            enveloppeRepository.creerEnveloppe(nouvelleEnveloppe).onSuccess {
                onFermerDialogues()
                chargerCategoriesEtEnveloppes()
            }.onFailure { e ->
                _uiState.update { it.copy(erreur = e.message) }
            }
        }
    }

    fun onOuvrirObjectifDialog(enveloppe: Enveloppe) {
        _uiState.update {
            it.copy(
                isObjectifDialogVisible = true,
                enveloppePourObjectif = enveloppe,
                objectifFormState = ObjectifFormState(
                    type = enveloppe.objectifType,
                    montant = if (enveloppe.objectifMontant > 0) enveloppe.objectifMontant.toString() else "",
                    date = enveloppe.objectifDate,
                    jour = enveloppe.objectifJour
                )
            )
        }
    }

    fun onObjectifFormChange(type: TypeObjectif? = null, montant: String? = null, date: Date? = null, jour: Int? = null) {
        _uiState.update {
            it.copy(
                objectifFormState = it.objectifFormState.copy(
                    type = type ?: it.objectifFormState.type,
                    montant = montant ?: it.objectifFormState.montant,
                    date = date ?: it.objectifFormState.date,
                    jour = jour ?: it.objectifFormState.jour
                )
            )
        }
    }

    fun sauvegarderObjectif() {
        viewModelScope.launch {
            val enveloppeOriginale = _uiState.value.enveloppePourObjectif ?: return@launch
            val formState = _uiState.value.objectifFormState

            val enveloppeMiseAJour = enveloppeOriginale.copy(
                objectifType = formState.type,
                objectifMontant = formState.montant.toDoubleOrNull() ?: 0.0,
                objectifDate = formState.date,
                objectifJour = formState.jour
            )
            // TODO: Remplacer par un vrai appel au repository pour mettre à jour
            println("Sauvegarde de l'objectif pour ${enveloppeMiseAJour.nom}")
            onFermerDialogues()
            chargerCategoriesEtEnveloppes()
        }
    }
}
