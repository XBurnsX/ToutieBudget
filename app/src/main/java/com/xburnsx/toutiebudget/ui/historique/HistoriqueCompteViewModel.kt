// chemin/simule: /ui/historique/HistoriqueCompteViewModel.kt
// Dépendances: ViewModel, SavedStateHandle, Repositories, Coroutines

package com.xburnsx.toutiebudget.ui.historique

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.data.repositories.TiersRepository
import com.xburnsx.toutiebudget.domain.usecases.SupprimerTransactionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ViewModel pour l'écran d'historique des transactions d'un compte.
 * Charge et affiche les transactions associées à un compte spécifique.
 */
class HistoriqueCompteViewModel(
    private val transactionRepository: TransactionRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val tiersRepository: TiersRepository,
    private val supprimerTransactionUseCase: SupprimerTransactionUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoriqueCompteUiState())
    val uiState: StateFlow<HistoriqueCompteUiState> = _uiState.asStateFlow()

    // Événements de navigation
    private val _navigationEvents = MutableStateFlow<HistoriqueNavigationEvent?>(null)
    val navigationEvents: StateFlow<HistoriqueNavigationEvent?> = _navigationEvents.asStateFlow()

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

    /**
     * Navigue vers l'écran de modification d'une transaction.
     */
    fun naviguerVersModification(transactionId: String) {
        _navigationEvents.value = HistoriqueNavigationEvent.ModifierTransaction(transactionId)
    }

    /**
     * Sauvegarde la position de scroll actuelle.
     */
    fun sauvegarderPositionScroll(position: Int) {
        _uiState.update { it.copy(scrollPosition = position) }
    }

    /**
     * Restaure la position de scroll après modification.
     */
    fun restaurerPositionScroll() {
        // La position est déjà sauvegardée dans l'état
        // Cette méthode peut être utilisée pour déclencher la restauration
    }

    /**
     * Supprime une transaction.
     */
    fun supprimerTransaction(transactionId: String) {
        viewModelScope.launch {
            try {
                val result = supprimerTransactionUseCase.executer(transactionId)
                if (result.isSuccess) {
                    // Recharger les transactions après suppression
                    val compteId: String? = savedStateHandle["compteId"]
                    val collectionCompte: String? = savedStateHandle["collectionCompte"]
                    if (compteId != null && collectionCompte != null) {
                        chargerTransactions(compteId, collectionCompte)
                    }
                } else {
                    _uiState.update { 
                        it.copy(erreur = "Erreur lors de la suppression: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(erreur = "Erreur lors de la suppression: ${e.message}")
                }
            }
        }
    }

    /**
     * Efface les événements de navigation.
     */
    fun effacerNavigationEvent() {
        _navigationEvents.value = null
    }

    /**
     * Charge les transactions pour un compte spécifique.
     */
    private fun chargerTransactions(compteId: String, collectionCompte: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // D'abord, récupérer TOUTES les transactions de l'utilisateur pour voir la structure
                val resultToutesTransactions = transactionRepository.recupererToutesLesTransactions()
                if (resultToutesTransactions.isSuccess) {
                    val toutesTransactions = resultToutesTransactions.getOrNull() ?: emptyList()
                }

                // Ensuite, récupérer les transactions du compte spécifique
                val resultTransactions = transactionRepository.recupererTransactionsPourCompte(compteId, collectionCompte)
                if (resultTransactions.isFailure) {
                    throw resultTransactions.exceptionOrNull() ?: Exception("Erreur lors du chargement des transactions")
                }
                
                val transactions = resultTransactions.getOrNull() ?: emptyList()

                // Récupérer les enveloppes pour les noms
                val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrNull() ?: emptyList()

                // Transformer en TransactionUi directement à partir des données de transactions
                val transactionsUi = transactions.map { transaction ->
                    // Si la transaction a une allocation mensuelle, trouver l'enveloppe correspondante
                    val nomEnveloppe = if (!transaction.allocationMensuelleId.isNullOrEmpty()) {
                        // Essayer de récupérer l'allocation directement par son ID
                        val resultAllocation = enveloppeRepository.recupererAllocationParId(transaction.allocationMensuelleId)
                        if (resultAllocation.isSuccess) {
                            val allocation = resultAllocation.getOrNull()
                            enveloppes.find { it.id == allocation?.enveloppeId }?.nom
                        } else {
                            null
                        }
                    } else {
                        null
                    }

                    // Utiliser directement le champ tiersId de la transaction (qui contient le nom)
                    val nomTiers = transaction.tiersId ?: "Transaction"

                    TransactionUi(
                        id = transaction.id,
                        type = transaction.type,
                        montant = transaction.montant,
                        date = transaction.date ?: Date(), // Valeur par défaut si date est null
                        tiers = nomTiers, // Utiliser directement le champ tiers de la transaction
                        nomEnveloppe = nomEnveloppe,
                        note = transaction.note // Garder la note complète
                    )
                }.sortedByDescending { it.date }

                // Grouper les transactions par date avec format français et garantir l'ordre
                val formateurDate = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH).apply {
                    timeZone = TimeZone.getDefault() // Utiliser le fuseau horaire local pour l'affichage
                }

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

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        transactions = transactionsUi,
                        transactionsGroupees = transactionsGroupees
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, erreur = e.message) }
            }
        }
    }
}