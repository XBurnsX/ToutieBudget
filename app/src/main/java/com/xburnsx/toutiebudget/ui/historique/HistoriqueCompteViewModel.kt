// chemin/simule: /ui/historique/HistoriqueCompteViewModel.kt
package com.xburnsx.toutiebudget.ui.historique

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

class HistoriqueCompteViewModel(
    private val transactionRepository: TransactionRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoriqueCompteUiState())
    val uiState: StateFlow<HistoriqueCompteUiState> = _uiState.asStateFlow()

    init {
        val compteId: String? = savedStateHandle["compteId"]
        val collectionCompte: String? = savedStateHandle["collectionCompte"]
        val nomCompte: String? = savedStateHandle["nomCompte"]

        if (compteId != null && collectionCompte != null) {
            _uiState.update { it.copy(nomCompte = nomCompte ?: "") }
            chargerTransactions(compteId, collectionCompte)
        } else {
            _uiState.update { it.copy(isLoading = false, erreur = "ID de compte manquant.") }
        }
    }

    private fun chargerTransactions(compteId: String, collectionCompte: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val transactions = transactionRepository.recupererTransactionsPourCompte(compteId, collectionCompte).getOrThrow()
                val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
                val allocations = enveloppeRepository.recupererAllocationsPourMois(Date()).getOrThrow()

                val transactionsUi = transactions.map { transaction ->
                    val allocation = allocations.find { it.id == transaction.allocationMensuelleId }
                    val nomEnveloppe = enveloppes.find { it.id == allocation?.enveloppeId }?.nom

                    TransactionUi(
                        id = transaction.id,
                        type = transaction.type,
                        montant = transaction.montant,
                        date = transaction.date,
                        tiers = transaction.note?.split(" - ")?.firstOrNull() ?: "Transaction",
                        nomEnveloppe = nomEnveloppe,
                        note = transaction.note?.split(" - ")?.getOrNull(1)
                    )
                }.sortedByDescending { it.date }

                _uiState.update {
                    it.copy(isLoading = false, transactions = transactionsUi)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, erreur = e.message) }
            }
        }
    }
}
