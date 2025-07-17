// chemin/simule: /ui/historique/HistoriqueCompteViewModel.kt
// Dépendances: ViewModel, SavedStateHandle, Repositories, Coroutines

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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ViewModel pour l'écran d'historique des transactions d'un compte.
 * Charge et affiche les transactions associées à un compte spécifique.
 */
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

        println("DEBUG INIT: compteId = $compteId")
        println("DEBUG INIT: collectionCompte = $collectionCompte")
        println("DEBUG INIT: nomCompte = $nomCompte")

        if (compteId != null && collectionCompte != null) {
            println("DEBUG INIT: Paramètres OK, chargement des transactions...")
            _uiState.update { it.copy(nomCompte = nomCompte ?: "") }
            chargerTransactions(compteId, collectionCompte)
        } else {
            println("DEBUG INIT: Paramètres manquants!")
            _uiState.update { it.copy(isLoading = false, erreur = "ID de compte manquant.") }
        }
    }

    /**
     * Charge les transactions pour un compte spécifique.
     */
    private fun chargerTransactions(compteId: String, collectionCompte: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // D'abord, récupérer TOUTES les transactions de l'utilisateur pour voir la structure
                val resultToutesTransactions = transactionRepository.recupererToutesLesTransactions()
                if (resultToutesTransactions.isSuccess) {
                    val toutesTransactions = resultToutesTransactions.getOrNull() ?: emptyList()
                    println("DEBUG: TOUTES les transactions de l'utilisateur: ${toutesTransactions.size}")
                    toutesTransactions.forEach { transaction ->
                        println("DEBUG: Transaction ID=${transaction.id}, compteId=${transaction.compteId}, collectionCompte=${transaction.collectionCompte}, type=${transaction.type}, montant=${transaction.montant}")
                    }
                }

                // Ensuite, récupérer les transactions du compte spécifique
                val resultTransactions = transactionRepository.recupererTransactionsPourCompte(compteId, collectionCompte)
                if (resultTransactions.isFailure) {
                    throw resultTransactions.exceptionOrNull() ?: Exception("Erreur lors du chargement des transactions")
                }
                
                val transactions = resultTransactions.getOrNull() ?: emptyList()
                println("DEBUG: Nombre de transactions récupérées pour le compte: ${transactions.size}")

                // Récupérer les enveloppes pour les noms
                val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrNull() ?: emptyList()
                println("DEBUG: Nombre d'enveloppes récupérées: ${enveloppes.size}")

                // Transformer en TransactionUi directement à partir des données de transactions
                val transactionsUi = transactions.map { transaction ->
                    println("DEBUG: Transaction ID: ${transaction.id}, AllocationId: ${transaction.allocationMensuelleId}")
                    println("DEBUG: Transaction date: ${transaction.date}")

                    // Si la transaction a une allocation mensuelle, trouver l'enveloppe correspondante
                    val nomEnveloppe = if (!transaction.allocationMensuelleId.isNullOrEmpty()) {
                        // Récupérer les allocations seulement si nécessaire
                        val allocations = enveloppeRepository.recupererAllocationsPourMois(Date()).getOrNull() ?: emptyList()
                        val allocation = allocations.find { it.id == transaction.allocationMensuelleId }
                        enveloppes.find { it.id == allocation?.enveloppeId }?.nom
                    } else {
                        null
                    }

                    TransactionUi(
                        id = transaction.id,
                        type = transaction.type,
                        montant = transaction.montant,
                        date = transaction.date ?: Date(), // Valeur par défaut si date est null
                        tiers = transaction.note?.split(" - ")?.firstOrNull() ?: "Transaction",
                        nomEnveloppe = nomEnveloppe,
                        note = transaction.note?.split(" - ")?.getOrNull(1)
                    )
                }.sortedByDescending { it.date }

                // Grouper les transactions par date avec format français et garantir l'ordre
                val formateurDate = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)

                // Créer une liste de paires (date, transactions) triée par date décroissante
                val transactionsGroupees = transactionsUi
                    .groupBy { transaction -> formateurDate.format(transaction.date) }
                    .map { (dateString, transactionsDeLaDate) ->
                        val dateParsee = try {
                            formateurDate.parse(dateString) ?: Date(0)
                        } catch (e: Exception) {
                            Date(0)
                        }
                        Triple(dateString, dateParsee, transactionsDeLaDate.sortedByDescending { it.date })
                    }
                    .sortedByDescending { it.second } // Trier par la date parsée
                    .associate { it.first to it.third } // Reconvertir en Map

                println("DEBUG: Nombre de transactions UI créées: ${transactionsUi.size}")
                println("DEBUG: Nombre de groupes de dates: ${transactionsGroupees.size}")
                println("DEBUG: Ordre des dates:")
                transactionsGroupees.keys.forEach { dateString ->
                    println("DEBUG: Date: $dateString")
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        transactions = transactionsUi,
                        transactionsGroupees = transactionsGroupees
                    )
                }
            } catch (e: Exception) {
                println("DEBUG: Erreur lors du chargement des transactions: ${e.message}")
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, erreur = e.message) }
            }
        }
    }
}