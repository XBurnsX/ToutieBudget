package com.xburnsx.toutiebudget.ui.historique

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.data.repositories.TiersRepository
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.domain.usecases.SupprimerTransactionUseCase
import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.google.gson.JsonParser

/**
 * ViewModel pour l'écran d'historique des transactions d'une enveloppe.
 * Charge et affiche les transactions associées à une enveloppe spécifique.
 */
class HistoriqueEnveloppeViewModel(
    private val transactionRepository: TransactionRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val tiersRepository: TiersRepository,
    private val supprimerTransactionUseCase: SupprimerTransactionUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoriqueEnveloppeUiState())
    val uiState: StateFlow<HistoriqueEnveloppeUiState> = _uiState.asStateFlow()

    // Événements de navigation
    private val _navigationEvents = MutableStateFlow<HistoriqueNavigationEvent?>(null)
    val navigationEvents: StateFlow<HistoriqueNavigationEvent?> = _navigationEvents.asStateFlow()

    init {
        val enveloppeId: String? = savedStateHandle["enveloppeId"]
        val nomEnveloppe: String? = savedStateHandle["nomEnveloppe"]

        if (enveloppeId != null) {
            _uiState.update { it.copy(nomEnveloppe = nomEnveloppe ?: "") }
            chargerTransactionsEnveloppe(enveloppeId)
        } else {
            _uiState.update { it.copy(isLoading = false, erreur = "ID d'enveloppe manquant.") }
        }
        
        // 🔄 RAFRAÎCHISSEMENT MANUEL : Écoute des événements de suppression/modification
        viewModelScope.launch {
            BudgetEvents.refreshBudget.collectLatest {
                // 🔄 Événement de rafraîchissement reçu - Mise à jour des transactions
                val enveloppeIdEvent: String? = savedStateHandle["enveloppeId"]
                if (enveloppeIdEvent != null) {
                    chargerTransactionsEnveloppe(enveloppeIdEvent)
                }
            }
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
     * Supprime une transaction.
     */
    fun supprimerTransaction(transactionId: String) {
        viewModelScope.launch {
            try {
                supprimerTransactionUseCase.executer(transactionId)
                // Recharger les transactions après suppression
                val enveloppeId: String? = savedStateHandle["enveloppeId"]
                if (enveloppeId != null) {
                    chargerTransactionsEnveloppe(enveloppeId)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(erreur = "Erreur lors de la suppression: ${e.message}") }
            }
        }
    }

    /**
     * Efface l'événement de navigation actuel.
     */
    fun effacerNavigationEvent() {
        _navigationEvents.value = null
    }

    /**
     * Recharge les transactions.
     */
    fun rechargerTransactions() {
        val enveloppeId: String? = savedStateHandle["enveloppeId"]
        if (enveloppeId != null) {
            chargerTransactionsEnveloppe(enveloppeId)
        }
    }

    /**
     * Met à jour la requête de recherche et filtre les transactions.
     */
    fun mettreAJourRecherche(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        filtrerTransactions()
    }

    /**
     * Filtre les transactions selon la requête de recherche.
     */
    private fun filtrerTransactions() {
        val query = _uiState.value.searchQuery.trim().lowercase()
        val allTransactions = _uiState.value.transactions

        if (query.isEmpty()) {
            // Si la recherche est vide, afficher toutes les transactions
            val transactionsGroupees = grouperTransactionsParDate(allTransactions)
            _uiState.update { 
                it.copy(transactionsGroupees = transactionsGroupees)
            }
        } else {
            // Filtrer les transactions selon la requête
            val transactionsFiltrees = allTransactions.filter { transaction ->
                // Rechercher dans le tiers/utilisateur
                transaction.tiersUtiliser.lowercase().contains(query) ||
                // Rechercher dans la note
                (transaction.note?.lowercase()?.contains(query) == true) ||
                // Rechercher dans le nom de l'enveloppe
                (transaction.nomEnveloppe?.lowercase()?.contains(query) == true) ||
                // Rechercher dans les enveloppes fractionnées
                transaction.nomsEnveloppesFractions.any { it.lowercase().contains(query) } ||
                // Rechercher dans le montant (convertir en string)
                transaction.montant.toString().contains(query) ||
                // Rechercher dans le type de transaction
                transaction.type.name.lowercase().contains(query)
            }

            val transactionsGroupees = grouperTransactionsParDate(transactionsFiltrees)
            _uiState.update { 
                it.copy(transactionsGroupees = transactionsGroupees)
            }
        }
    }

    /**
     * Groupe les transactions par date avec format français.
     */
    private fun grouperTransactionsParDate(transactions: List<TransactionUi>): Map<String, List<TransactionUi>> {
        val formateurDate = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH).apply {
            timeZone = TimeZone.getDefault()
        }

        return transactions
            .groupBy { transaction -> formateurDate.format(transaction.date) }
            .map { (dateString, transactionsDeLaDate) ->
                val dateParsee = try {
                    formateurDate.parse(dateString) ?: Date(0)
                } catch (_: Exception) {
                    Date(0)
                }
                Triple(dateString, dateParsee, transactionsDeLaDate.sortedByDescending { it.date })
            }
            .sortedByDescending { it.second }
            .associate { it.first to it.third }
    }

    /**
     * Charge les transactions pour une enveloppe spécifique.
     */
    private fun chargerTransactionsEnveloppe(enveloppeId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // ✅ OPTIMISATION : Récupérer toutes les données en parallèle
                val deferredTransactions = async { 
                    transactionRepository.recupererToutesLesTransactions()
                }
                val deferredEnveloppes = async { 
                    enveloppeRepository.recupererToutesLesEnveloppes()
                }
                val deferredAllocations = async { 
                    enveloppeRepository.recupererAllocationsPourMois(Date())
                }
                val deferredTiers = async {
                    tiersRepository.recupererTousLesTiers()
                }

                // Attendre que tous les appels soient terminés
                val resultTransactions = deferredTransactions.await()
                val resultEnveloppes = deferredEnveloppes.await()
                val resultAllocations = deferredAllocations.await()
                val resultTiers = deferredTiers.await()

                if (resultTransactions.isFailure) {
                    throw resultTransactions.exceptionOrNull() ?: Exception("Erreur lors du chargement des transactions")
                }
                
                val transactions = resultTransactions.getOrNull() ?: emptyList()
                val enveloppes = resultEnveloppes.getOrNull() ?: emptyList()
                val allocations = resultAllocations.getOrNull() ?: emptyList()
                val tiers = resultTiers.getOrNull() ?: emptyList()

                // ✅ OPTIMISATION : Créer des maps pour un accès rapide
                val enveloppeIdToNom = enveloppes.associateBy({ it.id }, { it.nom })
                val allocationIdToEnveloppeId = allocations.associateBy({ it.id }, { it.enveloppeId })
                val tiersIdToNom = tiers.associateBy({ it.id }, { it.nom })

                // Filtrer les transactions pour cette enveloppe spécifique
                val transactionsEnveloppe = transactions.filter { transaction ->
                    // 1) Cas simple: allocation -> enveloppe
                    val allocId = transaction.allocationMensuelleId
                    val matchSimple = allocId != null && allocationIdToEnveloppeId[allocId] == enveloppeId
                    if (matchSimple) return@filter true
                    
                    // 2) Cas transaction fractionnée: sous-items contenant l'enveloppe
                    if (transaction.estFractionnee && !transaction.sousItems.isNullOrBlank()) {
                        try {
                            val arr = JsonParser.parseString(transaction.sousItems).asJsonArray
                            arr.any { elem -> 
                                elem.asJsonObject.get("enveloppeId")?.asString == enveloppeId 
                            }
                        } catch (_: Exception) { false }
                    } else false
                }

                // Convertir en TransactionUi
                val transactionsUi = transactionsEnveloppe.map { transaction ->
                    // Créer un libellé descriptif selon le type de transaction
                    val nomTiers = transaction.tiersUtiliser ?: "Transaction"
                    
                    // Créer un libellé descriptif selon le type de transaction
                    val libelleDescriptif = when (transaction.type) {
                        TypeTransaction.Pret -> "Prêt accordé à : $nomTiers"
                        TypeTransaction.RemboursementRecu -> "Remboursement reçu de : $nomTiers"
                        TypeTransaction.Emprunt -> "Dette contractée de : $nomTiers"
                        TypeTransaction.RemboursementDonne -> "Remboursement donné à : $nomTiers"
                        TypeTransaction.Paiement -> "Paiement : $nomTiers"
                        TypeTransaction.PaiementEffectue -> "Paiement effectue : $nomTiers"
                        TypeTransaction.Depense -> nomTiers
                        TypeTransaction.Revenu -> nomTiers
                        else -> nomTiers
                    }

                    // Récupérer le nom de l'enveloppe
                    val nomEnveloppe = transaction.allocationMensuelleId?.let { allocationId ->
                        allocationIdToEnveloppeId[allocationId]?.let { envId ->
                            enveloppeIdToNom[envId]
                        }
                    }

                    TransactionUi(
                        id = transaction.id,
                        type = transaction.type,
                        montant = transaction.montant,
                        date = transaction.date,
                        tiersUtiliser = libelleDescriptif,
                        nomEnveloppe = nomEnveloppe,
                        note = transaction.note,
                        estFractionnee = transaction.estFractionnee,
                        sousItems = transaction.sousItems
                    )
                }

                // Utiliser la nouvelle méthode de groupement
                val transactionsGroupees = grouperTransactionsParDate(transactionsUi)

                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        transactions = transactionsUi,
                        transactionsGroupees = transactionsGroupees,
                        erreur = null
                    )
                }
                
                // Appliquer le filtrage si une recherche est active
                if (_uiState.value.searchQuery.isNotEmpty()) {
                    filtrerTransactions()
                }

            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        erreur = "Erreur lors du chargement: ${e.message}"
                    )
                }
            }
        }
    }
}
