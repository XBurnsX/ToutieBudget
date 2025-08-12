package com.xburnsx.toutiebudget.ui.pret_personnel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.PretPersonnel
import com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel
import com.xburnsx.toutiebudget.data.repositories.PretPersonnelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PretPersonnelViewModel(
    private val pretPersonnelRepository: PretPersonnelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PretPersonnelUiState(isLoading = true))
    val uiState: StateFlow<PretPersonnelUiState> = _uiState.asStateFlow()

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, erreur = null)
            val result = pretPersonnelRepository.lister()
            result.onSuccess { entries ->
                val itemsPret = entries
                    .filter { it.type == TypePretPersonnel.PRET && !it.estArchive }
                    .map { it.toItem() }
                val itemsEmprunt = entries
                    .filter { it.type == TypePretPersonnel.DETTE && !it.estArchive }
                    .map { it.toItem() }
                _uiState.value = PretPersonnelUiState(
                    isLoading = false,
                    items = itemsPret + itemsEmprunt,
                    itemsPret = itemsPret,
                    itemsEmprunt = itemsEmprunt
                )
            }.onFailure { e ->
                _uiState.value = PretPersonnelUiState(isLoading = false, erreur = e.message)
            }
        }
    }

    fun setTab(tab: PretTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    private fun PretPersonnel.toItem(): PretPersonnelItem = PretPersonnelItem(
        key = this.id,
        nomTiers = this.nomTiers ?: "",
        montantPrete = this.montantInitial,
        montantRembourse = 0.0,
        soldeRestant = this.solde,
        derniereDate = null
    )
}


