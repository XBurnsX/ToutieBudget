package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.utils.lancerUneFois
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.data.repositories.TiersRepository
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.domain.usecases.SupprimerTransactionUseCase
import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

    // AT14 : une seule exécution à la fois (double appui)
    private val verrouSuppression = com.xburnsx.toutiebudget.utils.VerrouOperation()
    // HC20 : annule le chargement précédent avant d'en démarrer un nouveau.
    private var chargementJob: Job? = null

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
    fun sauvegarderPositionScroll(position: Int, offset: Int = 0) {
        _uiState.update { etat ->
            if (etat.scrollPosition == position && etat.scrollOffset == offset) etat
            else etat.copy(scrollPosition = position, scrollOffset = offset)
        }
    }

    /**
     * Supprime une transaction.
     */
    fun supprimerTransaction(transactionId: String) {
        viewModelScope.lancerUneFois(verrouSuppression) {
            try {
                // HE4 : un Result.failure ne déclenche pas catch sans getOrThrow().
                supprimerTransactionUseCase.executer(transactionId).getOrThrow()
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
        // La liste source reste intacte; on réutilise le même filtre après un rechargement.
        val source = _uiState.value
        val filtrees = RechercheHistorique.filtrer(source.transactions, source.searchQuery)
        val groupes = grouperTransactionsParDate(filtrees)
        _uiState.update { etat ->
            // Une recherche saisie pendant un chargement reste prioritaire.
            if (etat.searchQuery == source.searchQuery && etat.transactions === source.transactions)
                etat.copy(transactionsGroupees = groupes)
            else etat.copy(transactionsGroupees =
                grouperTransactionsParDate(RechercheHistorique.filtrer(etat.transactions, etat.searchQuery)))
        }
    }

    /**
     * Groupe les transactions par date avec format français.
     */
    private fun grouperTransactionsParDate(transactions: List<TransactionUi>): Map<String, List<TransactionUi>> {
        val formateurDate = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH).apply {
            timeZone = TimeZone.getDefault()
        }

        // Regrouper avec une vraie clé calendaire, plutôt que reparcourir une date
        // affichée en texte français. Cela évite les regroupements au 1er janvier
        // 1970 si l'analyse du libellé échoue et garantit l'ordre chronologique.
        val jours = transactions.groupBy { transaction ->
            java.time.Instant.ofEpochMilli(transaction.date.time)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
        }
        return jours.entries.sortedByDescending { it.key }.associate { (jour, lignes) ->
            formateurDate.format(
                Date.from(jour.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())
            ) to lignes.sortedByDescending { it.date }
        }
    }

    /**
     * Charge les transactions pour une enveloppe spécifique.
     */
    private fun chargerTransactionsEnveloppe(enveloppeId: String) {
        chargementJob?.cancel()
        chargementJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // ✅ OPTIMISATION : Récupérer toutes les données en parallèle
                val deferredTransactions = async { 
                    transactionRepository.recupererToutesLesTransactions()
                }
                val deferredEnveloppes = async { 
                    enveloppeRepository.recupererToutesLesEnveloppes()
                }
                // Un tiers n'est PAS nécessaire au rattachement des transactions.
                // Une liste de tiers indisponible ne doit pas cacher l'historique.

                // Attendre que tous les appels soient terminés
                val resultTransactions = deferredTransactions.await()
                val resultEnveloppes = deferredEnveloppes.await()

                // HE7/HE12 : aucun repository en échec ne doit être interprété
                // comme une liste vide d'enveloppes ou de tiers.
                resultTransactions.getOrThrow()
                resultEnveloppes.getOrThrow()
                
                val transactions = resultTransactions.getOrNull() ?: emptyList()
                val enveloppes = resultEnveloppes.getOrNull() ?: emptyList()
                // HE1 : chaque allocation est retrouvée directement par son ID, quelle que soit sa période.
                var allocationsIndisponibles = 0
                val allocations = transactions.mapNotNull { it.allocationMensuelleId }.distinct().mapNotNull { id ->
                    val allocation = enveloppeRepository.recupererAllocationParId(id).getOrNull()
                    if (allocation == null) allocationsIndisponibles++
                    allocation
                }

                // ✅ OPTIMISATION : Créer des maps pour un accès rapide
                val enveloppeIdToNom = enveloppes.associateBy({ it.id }, { it.nom })
                val allocationIdToEnveloppeId = allocations.associateBy({ it.id }, { it.enveloppeId })

                // Filtrer les transactions pour cette enveloppe spécifique
                // Un JSON de fractions illisible doit être signalé, pas devenir
                // silencieusement une transaction "absente".
                var fractionsIllisibles = 0
                val transactionsEnveloppe = transactions.filter { transaction ->
                    // 1) Cas simple: allocation -> enveloppe
                    val allocId = transaction.allocationMensuelleId
                    val matchSimple = allocId != null && allocationIdToEnveloppeId[allocId] == enveloppeId
                    if (matchSimple) return@filter true
                    
                    // 2) Cas transaction fractionnée: sous-items contenant l'enveloppe
                    if (transaction.estFractionnee) {
                        try {
                            require(!transaction.sousItems.isNullOrBlank()) { "Fractions absentes." }
                            val arr = JsonParser.parseString(transaction.sousItems).asJsonArray
                            require(arr.size() > 0) { "Fractions vides." }
                            arr.any { elem ->
                                val fraction = elem.asJsonObject
                                (fraction.get("enveloppe_id") ?: fraction.get("enveloppeId"))
                                    ?.takeIf { !it.isJsonNull }?.asString == enveloppeId
                            }
                        } catch (_: Exception) {
                            fractionsIllisibles++
                            false
                        }
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

                    // HE2 : dans l'historique d'une enveloppe, une transaction fractionnée montre sa portion.
                    val montantPourEnveloppe = if (transaction.estFractionnee && !transaction.sousItems.isNullOrBlank()) {
                        runCatching {
                            JsonParser.parseString(transaction.sousItems).asJsonArray
                                .filter { (it.asJsonObject.get("enveloppe_id") ?: it.asJsonObject.get("enveloppeId"))?.takeIf { !it.isJsonNull }?.asString == enveloppeId }
                                .sumOf { it.asJsonObject.get("montant")?.asDouble ?: 0.0 }
                        }.getOrElse { cause ->
                            // HE2 : ne jamais présenter le montant total de l'achat
                            // comme si c'était la portion de cette enveloppe.
                            throw IllegalStateException("Fractionnement illisible pour la transaction ${transaction.id}", cause)
                        }
                    } else transaction.montant

                    TransactionUi(
                        id = transaction.id,
                        type = transaction.type,
                        montant = montantPourEnveloppe,
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
                        erreur = null,
                        avertissement = buildList {
                            if (allocationsIndisponibles > 0)
                                add("$allocationsIndisponibles allocation(s) historique(s) introuvable(s).")
                            if (fractionsIllisibles > 0)
                                add("$fractionsIllisibles transaction(s) fractionnée(s) illisible(s).")
                        }.takeIf { it.isNotEmpty() }?.joinToString(" ")?.let {
                            "$it Historique possiblement incomplet."
                        }
                    )
                }
                
                // Appliquer le filtrage si une recherche est active
                if (_uiState.value.searchQuery.isNotEmpty()) {
                    filtrerTransactions()
                }

            } catch (annulation: CancellationException) {
                throw annulation // ancien chargement : surtout ne pas écraser le plus récent
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false,
                        erreur = "Erreur lors du chargement: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }
}
