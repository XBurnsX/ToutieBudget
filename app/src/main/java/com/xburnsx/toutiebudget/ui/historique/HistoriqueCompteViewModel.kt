// chemin/simule: /ui/historique/HistoriqueCompteViewModel.kt
// Dépendances: ViewModel, SavedStateHandle, Repositories, Coroutines

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
// import android.util.Log
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
            // ✅ OPTIMISATION : Charger une seule fois au démarrage
            chargerTransactions(compteId, collectionCompte)
        } else {
            _uiState.update { it.copy(isLoading = false, erreur = "ID de compte manquant.") }
        }
        
        // 🔄 RAFRAÎCHISSEMENT MANUEL : Écoute des événements de suppression/modification
        viewModelScope.launch {
            BudgetEvents.refreshBudget.collectLatest {
                // 🔄 Événement de rafraîchissement reçu - Mise à jour des transactions
                val compteIdEvent: String? = savedStateHandle["compteId"]
                val collectionCompteEvent: String? = savedStateHandle["collectionCompte"]
                if (compteIdEvent != null && collectionCompteEvent != null) {
                    chargerTransactions(compteIdEvent, collectionCompteEvent)
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
     * Recharge les transactions (méthode publique pour être appelée après modification).
     */
    fun rechargerTransactions() {
        val compteId: String? = savedStateHandle["compteId"]
        val collectionCompte: String? = savedStateHandle["collectionCompte"]
        if (compteId != null && collectionCompte != null) {
            chargerTransactions(compteId, collectionCompte)
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
     * Charge les transactions pour un compte spécifique.
     */
    private fun chargerTransactions(compteId: String, collectionCompte: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // ✅ OPTIMISATION : Récupérer toutes les données en parallèle
                val deferredTransactions = async { 
                    transactionRepository.recupererTransactionsPourCompte(compteId, collectionCompte)
                }
                val deferredEnveloppes = async { 
                    enveloppeRepository.recupererToutesLesEnveloppes()
                }
                // ✅ CORRECTION : Utiliser la bonne méthode pour les allocations
                val deferredAllocations = async { 
                    enveloppeRepository.recupererAllocationsPourMois(Date())
                }

                // Attendre que tous les appels soient terminés
                val resultTransactions = deferredTransactions.await()
                val resultEnveloppes = deferredEnveloppes.await()
                val resultAllocations = deferredAllocations.await()

                if (resultTransactions.isFailure) {
                    throw resultTransactions.exceptionOrNull() ?: Exception("Erreur lors du chargement des transactions")
                }
                
                val transactions = resultTransactions.getOrNull() ?: emptyList()
                val enveloppes = resultEnveloppes.getOrNull() ?: emptyList()
                val allocations = resultAllocations.getOrNull() ?: emptyList()

                // 🔍 LOGS DEBUG : Vérifier les résultats
                // DEBUG: resultAllocations.isSuccess = ${resultAllocations.isSuccess}
                // DEBUG: resultAllocations.isFailure = ${resultAllocations.isFailure}
                // DEBUG: allocations.size = ${allocations.size}

                // ✅ OPTIMISATION : Créer des maps pour un accès rapide
                val enveloppesMap = enveloppes.associateBy { it.id }
                val allocationsMap = allocations.associateBy { it.id }
                
                // 🔍 LOGS DEBUG : Vérifier les maps
                // DEBUG: enveloppesMap.size = ${enveloppesMap.size}
                // DEBUG: allocationsMap.size = ${allocationsMap.size}

                // Transformer en TransactionUi directement à partir des données de transactions
                val transactionsUi = transactions.map { transaction ->
                    // Créer un libellé descriptif selon le type de transaction
                    val nomTiers = transaction.tiersUtiliser ?: "Transaction"
                    
                    // Créer un libellé descriptif selon le type de transaction , texte pour les paiements
                    val libelleDescriptif = when (transaction.type) {
                        TypeTransaction.Pret -> "Prêt accordé à : $nomTiers"
                        TypeTransaction.RemboursementRecu -> "Remboursement reçu de : $nomTiers"
                        TypeTransaction.Emprunt -> "Dette contractée de : $nomTiers"
                        TypeTransaction.RemboursementDonne -> "Remboursement donné à : $nomTiers"
                        TypeTransaction.Paiement -> "Paiement : $nomTiers" // Afficher "Paiement : [nom de la dette]"
                        TypeTransaction.PaiementEffectue -> "Paiement effectue : $nomTiers" // Paiement effectué sur une dette/carte
                        TypeTransaction.Depense -> nomTiers
                        TypeTransaction.Revenu -> nomTiers
                        else -> nomTiers
                    }

                    // ✅ OPTIMISATION : Utiliser les maps pour un accès rapide
                    val nomEnveloppe = transaction.allocationMensuelleId?.let { allocationId ->
                        val allocation = allocationsMap[allocationId]
                        if (allocation != null) {
                            enveloppesMap[allocation.enveloppeId]?.nom
                        } else {
                            null
                        }
                    }

                    // ✅ OPTIMISATION : Utiliser la map des enveloppes
                    val nomsEnveloppesFractions = if (transaction.estFractionnee && !transaction.sousItems.isNullOrBlank()) {
                        try {
                            val jsonArray = com.google.gson.JsonParser.parseString(transaction.sousItems).asJsonArray
                            jsonArray.mapNotNull { element ->
                                val obj = element.asJsonObject
                                val enveloppeId = obj.get("enveloppeId")?.asString
                                if (enveloppeId != null) {
                                    enveloppesMap[enveloppeId]?.nom ?: com.xburnsx.toutiebudget.utils.LIBELLE_SANS_ENVELOPPE
                                } else {
                                    null
                                }
                            }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }

                    TransactionUi(
                        id = transaction.id,
                        type = transaction.type,
                        montant = transaction.montant,
                        date = transaction.date, // Valeur par défaut si date est null
                        tiersUtiliser = libelleDescriptif, // Utiliser le libellé descriptif
                        nomEnveloppe = nomEnveloppe,
                        note = transaction.note, // Garder la note complète
                        estFractionnee = transaction.estFractionnee,
                        sousItems = transaction.sousItems,
                        nomsEnveloppesFractions = nomsEnveloppesFractions
                    )
                }.sortedByDescending { it.date }

                // Utiliser la nouvelle méthode de groupement
                val transactionsGroupees = grouperTransactionsParDate(transactionsUi)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        transactions = transactionsUi,
                        transactionsGroupees = transactionsGroupees
                    )
                }
                
                // Appliquer le filtrage si une recherche est active
                if (_uiState.value.searchQuery.isNotEmpty()) {
                    filtrerTransactions()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, erreur = e.message) }
            }
        }
    }
}