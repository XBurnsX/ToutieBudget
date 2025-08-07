package com.xburnsx.toutiebudget.ui.dette

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class DetteViewModel @Inject constructor(
    private val compteRepository: CompteRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DetteUiState())
    val uiState: StateFlow<DetteUiState> = _uiState.asStateFlow()
    
    fun chargerDette(detteId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                val dette = compteRepository.getCompteById(detteId, "comptes_dettes") as? CompteDette
                if (dette != null) {
                    _uiState.value = _uiState.value.copy(
                        dette = dette,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Dette non trouvée",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Erreur lors du chargement: ${e.message}",
                    isLoading = false
                )
            }
        }
    }
    
    fun sauvegarderDette(dette: CompteDette) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSaving = true)
                
                compteRepository.mettreAJourCompte(dette)
                
                _uiState.value = _uiState.value.copy(
                    dette = dette,
                    isSaving = false,
                    isSaved = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Erreur lors de la sauvegarde: ${e.message}",
                    isSaving = false
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearSaved() {
        _uiState.value = _uiState.value.copy(isSaved = false)
    }
}

data class DetteUiState(
    val dette: CompteDette? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
) 