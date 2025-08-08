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
import kotlin.math.pow

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

                val montantInitial = dette.montantInitial
                val tauxAnnuel = (dette.tauxInteret ?: 0.0) / 100.0
                val dureeMois = (dette.dureeMoisPret ?: 0)
                val tauxMensuel = if (tauxAnnuel > 0) tauxAnnuel / 12.0 else 0.0

                // Paiement mensuel (amortissement classique)
                val paiementMensuel = if (tauxMensuel > 0 && dureeMois > 0) {
                    montantInitial * (tauxMensuel * (1 + tauxMensuel).pow(dureeMois)) /
                        ((1 + tauxMensuel).pow(dureeMois) - 1)
                } else if (dureeMois > 0) {
                    montantInitial / dureeMois
                } else 0.0

                // Prix total et solde mis à jour = prix total (demande utilisateur)
                val prixTotal = if (dureeMois > 0) paiementMensuel * dureeMois else montantInitial
 
                // Solde restant basé sur paiements effectués, borné à >= 0
                val paiementsEffectues = dette.paiementEffectue.coerceAtLeast(0)
                val soldeRestant = if (dureeMois > 0) {
                    (prixTotal - paiementMensuel * paiementsEffectues).coerceAtLeast(0.0)
                } else {
                    // Si pas de durée définie, on conserve le solde existant en valeur absolue
                    kotlin.math.abs(dette.soldeDette)
                }
                val nouveauSoldeDette = -soldeRestant

                val aSauver = dette.copy(
                    prixTotal = prixTotal,
                    paiementMinimum = kotlin.math.round(paiementMensuel * 100) / 100.0, // enregistrer paiement mensuel calculé
                    soldeDette = nouveauSoldeDette
                )
                
                compteRepository.mettreAJourCompte(aSauver)
                
                _uiState.value = _uiState.value.copy(
                    dette = aSauver,
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

}

data class DetteUiState(
    val dette: CompteDette? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
) 