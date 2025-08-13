package com.xburnsx.toutiebudget.ui.pret_personnel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.PretPersonnel
import com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel
import com.xburnsx.toutiebudget.data.repositories.PretPersonnelRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PretPersonnelViewModel(
    private val pretPersonnelRepository: PretPersonnelRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PretPersonnelUiState(isLoading = true))
    val uiState: StateFlow<PretPersonnelUiState> = _uiState.asStateFlow()

    init {
        // Ne rien faire ici; l'écran déclenchera charger() dans LaunchedEffect pour garantir un refresh à chaque ouverture
    }

    fun charger() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, erreur = null)
            val result = pretPersonnelRepository.lister()
            result.onSuccess { entries ->
                val itemsPret = entries
                    .filter { it.type == TypePretPersonnel.PRET && !it.estArchive && it.solde > 0 }
                    .map { it.toItem() }
                val itemsEmprunt = entries
                    .filter { it.type == TypePretPersonnel.DETTE && !it.estArchive && it.solde < 0 }
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

    fun chargerHistoriquePourPret(pretId: String, nomTiers: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingHistorique = true)
            val txs = transactionRepository.recupererToutesLesTransactions().getOrElse { emptyList() }
                .filter { t ->
                    (t.tiers?.equals(nomTiers, ignoreCase = true) == true) &&
                    (t.type == TypeTransaction.Pret || t.type == TypeTransaction.RemboursementRecu ||
                     t.type == TypeTransaction.Emprunt || t.type == TypeTransaction.RemboursementDonne) &&
                    (t.sousItems?.contains(pretId) == true)
                }
                .sortedByDescending { it.date }
            val items = txs.map { t ->
                val lib = when (t.type) {
                    TypeTransaction.Pret -> "Prêt accordé"
                    TypeTransaction.RemboursementRecu -> "Remboursement reçu"
                    TypeTransaction.Emprunt -> "Dette contractée"
                    TypeTransaction.RemboursementDonne -> "Remboursement donné"
                    else -> t.type.libelle
                }
                HistoriqueItem(
                    id = t.id,
                    date = t.date,
                    type = lib,
                    montant = t.montant
                )
            }
            val recordActif = pretPersonnelRepository.lister().getOrElse { emptyList() }
                .firstOrNull { it.id == pretId }
            _uiState.value = _uiState.value.copy(isLoadingHistorique = false, historique = items, detailPret = recordActif)
        }
    }

    fun clearHistorique() {
        _uiState.value = _uiState.value.copy(historique = emptyList(), isLoadingHistorique = false)
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


