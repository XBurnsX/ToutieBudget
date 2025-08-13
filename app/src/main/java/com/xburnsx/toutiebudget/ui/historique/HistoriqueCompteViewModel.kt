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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
     * Charge les transactions pour un compte spécifique.
     */
    private fun chargerTransactions(compteId: String, collectionCompte: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // D'abord, récupérer TOUTES les transactions de l'utilisateur pour voir la structure
                val resultToutesTransactions = transactionRepository.recupererToutesLesTransactions()
                if (resultToutesTransactions.isSuccess) {
                }

                // Ensuite, récupérer les transactions du compte spécifique
                val resultTransactions = transactionRepository.recupererTransactionsPourCompte(compteId, collectionCompte)
                if (resultTransactions.isFailure) {
                    throw resultTransactions.exceptionOrNull() ?: Exception("Erreur lors du chargement des transactions")
                }
                
                val transactions = resultTransactions.getOrNull() ?: emptyList()

                // Récupérer les enveloppes pour les noms

                // Transformer en TransactionUi directement à partir des données de transactions
                val transactionsUi = transactions.map { transaction ->
                    // Créer un libellé descriptif selon le type de transaction
                    val nomTiers = if (!transaction.tiers.isNullOrBlank()) {
                        transaction.tiers
                    } else if (!transaction.tiersId.isNullOrBlank()) {
                        // Récupérer le nom du tiers depuis l'ID
                        val resultTiers = tiersRepository.recupererTousLesTiers()
                        if (resultTiers.isSuccess) {
                            val tiers = resultTiers.getOrThrow()
                            tiers.find { it.id == transaction.tiersId }?.nom ?: transaction.tiersId
                        } else {
                            transaction.tiersId
                        }
                    } else {
                        "Transaction"
                    }
                    
                    // Créer un libellé descriptif selon le type de transaction
                    val libelleDescriptif = when (transaction.type) {
                        TypeTransaction.Pret -> "Prêt accordé à : $nomTiers"
                        TypeTransaction.RemboursementRecu -> "Remboursement reçu de : $nomTiers"
                        TypeTransaction.Emprunt -> "Dette contractée de : $nomTiers"
                        TypeTransaction.RemboursementDonne -> "Remboursement donné à : $nomTiers"
                        TypeTransaction.Depense -> nomTiers
                        TypeTransaction.Revenu -> nomTiers
                        else -> nomTiers
                    }

                    // Créer TransactionUi avec les données récupérées
                    val nomEnveloppe = transaction.allocationMensuelleId?.let { allocationId ->
                        // Récupérer le nom de l'enveloppe depuis l'allocation
                        val resultAllocation = enveloppeRepository.recupererAllocationParId(allocationId)
                        if (resultAllocation.isSuccess) {
                            val allocation = resultAllocation.getOrThrow()
                            // Récupérer l'enveloppe depuis l'allocation
                            val resultEnveloppes = enveloppeRepository.recupererToutesLesEnveloppes()
                            if (resultEnveloppes.isSuccess) {
                                val enveloppe = resultEnveloppes.getOrThrow().find { it.id == allocation.enveloppeId }
                                enveloppe?.nom
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }

                    // Pour les transactions fractionnées, récupérer les noms des enveloppes depuis le JSON
                    val nomsEnveloppesFractions = if (transaction.estFractionnee && !transaction.sousItems.isNullOrBlank()) {
                        try {
                            val jsonArray = com.google.gson.JsonParser.parseString(transaction.sousItems).asJsonArray
                            val resultEnveloppes = enveloppeRepository.recupererToutesLesEnveloppes()
                            if (resultEnveloppes.isSuccess) {
                                val enveloppes = resultEnveloppes.getOrThrow()
                                jsonArray.mapNotNull { element ->
                                    val obj = element.asJsonObject
                                    val enveloppeId = obj.get("enveloppeId")?.asString
                                    if (enveloppeId != null) {
                                        val enveloppe = enveloppes.find { it.id == enveloppeId }
                                        enveloppe?.nom ?: com.xburnsx.toutiebudget.utils.LIBELLE_SANS_ENVELOPPE
                                    } else {
                                        null
                                    }
                                }
                            } else {
                                emptyList()
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
                        tiers = libelleDescriptif, // Utiliser le libellé descriptif
                        nomEnveloppe = nomEnveloppe,
                        note = transaction.note, // Garder la note complète
                        estFractionnee = transaction.estFractionnee,
                        sousItems = transaction.sousItems,
                        nomsEnveloppesFractions = nomsEnveloppesFractions
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
                        } catch (_: Exception) {
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