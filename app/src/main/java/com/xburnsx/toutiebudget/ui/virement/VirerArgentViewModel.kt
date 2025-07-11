// chemin/simule: /ui/virement/VirerArgentViewModel.kt
package com.xburnsx.toutiebudget.ui.virement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.domain.services.ArgentService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

class VirerArgentViewModel(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val argentService: ArgentService
) : ViewModel() {

    private val _uiState = MutableStateFlow(VirerArgentUiState())
    val uiState: StateFlow<VirerArgentUiState> = _uiState.asStateFlow()

    init {
        chargerSourcesEtDestinations()
    }

    private fun chargerSourcesEtDestinations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val comptes = compteRepository.recupererTousLesComptes().getOrThrow()
                val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                val allocations = enveloppeRepository.recupererAllocationsPourMois(Date()).getOrThrow()

                val itemsComptes = comptes
                    .filterIsInstance<CompteCheque>()
                    .map { ItemVirement.CompteItem(it) }

                val itemsEnveloppes = enveloppes.map { enveloppe ->
                    val alloc = allocations.find { it.enveloppeId == enveloppe.id }
                    val compteSource = alloc?.compteSourceId?.let { id -> comptes.find { it.id == id } }
                    ItemVirement.EnveloppeItem(enveloppe, alloc?.solde ?: 0.0, compteSource?.couleur)
                }

                val sources = mapOf("Prêt à placer" to itemsComptes) + itemsEnveloppes.groupBy { it.enveloppe.categorie }
                val destinations = mapOf("Prêt à placer" to itemsComptes) + itemsEnveloppes.groupBy { it.enveloppe.categorie }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sourcesDisponibles = sources,
                        destinationsDisponibles = destinations
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, erreur = e.message) }
            }
        }
    }

    fun ouvrirSelecteur(type: SelecteurOuvert) { _uiState.update { it.copy(selecteurOuvert = type) } }
    fun fermerSelecteur() { _uiState.update { it.copy(selecteurOuvert = SelecteurOuvert.AUCUN) } }
    fun onItemSelected(item: ItemVirement) {
        when (_uiState.value.selecteurOuvert) {
            SelecteurOuvert.SOURCE -> _uiState.update { it.copy(sourceSelectionnee = item) }
            SelecteurOuvert.DESTINATION -> _uiState.update { it.copy(destinationSelectionnee = item) }
            SelecteurOuvert.AUCUN -> {}
        }
        fermerSelecteur()
    }
}
