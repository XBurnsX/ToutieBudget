// chemin/simule: /ui/historique/HistoriqueCompteViewModel.kt
// Dépendances: ViewModel, SavedStateHandle, Repositories, Coroutines

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
// import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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

    // AT14 : une seule exécution à la fois (double appui)
    private val verrouSuppression = com.xburnsx.toutiebudget.utils.VerrouOperation()
    // HC20 : annule le chargement précédent avant d'en démarrer un nouveau.
    private var chargementJob: Job? = null

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
        supprimerSiNonModifiee(transactionId, null)
    }

    fun supprimerTransactionConfirmee(affichee: TransactionUi) {
        supprimerSiNonModifiee(affichee.id, affichee)
    }

    private fun supprimerSiNonModifiee(transactionId: String, affichee: TransactionUi?) {
        viewModelScope.lancerUneFois(verrouSuppression) {
            try {
                // HC5 : valider l'appartenance au compte connecté AVANT l'UseCase.
                // La requête Room est filtrée par utilisateur_id + compte + collection.
                val compteId = savedStateHandle.get<String>("compteId")
                    ?: error("Compte d'origine manquant.")
                val collection = savedStateHandle.get<String>("collectionCompte")
                    ?: error("Collection du compte manquante.")
                val visiblesDuCompte = transactionRepository
                    .recupererTransactionsPourCompte(compteId, collection).getOrThrow()
                require(visiblesDuCompte.any { it.id == transactionId }) {
                    "Cette transaction n'appartient pas au compte connecté."
                }
                if (affichee != null) {
                    val actuelle = transactionRepository.recupererTransactionParId(transactionId)
                        .getOrThrow()
                    if (!VerificationSuppressionHistorique.estEncoreLaMeme(affichee, actuelle)) {
                        _uiState.update { it.copy(
                            erreur = "La transaction a changé depuis la confirmation. " +
                                "Aucune suppression effectuée. Réessaie après actualisation."
                        ) }
                        return@lancerUneFois
                    }
                }
                supprimerTransactionUseCase.executer(transactionId).getOrThrow()
                rechargerTransactions()
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    erreur = "Suppression refusée : " + (e.message ?: e.javaClass.simpleName)
                ) }
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
        // Une seule émission d'état : éviter d'afficher brièvement la nouvelle
        // recherche avec la vieille liste (et les sauts de scroll associés).
        _uiState.update { actuel ->
            actuel.copy(
                searchQuery = query,
                transactionsGroupees = grouperTransactionsParDate(
                    RechercheHistorique.filtrer(actuel.transactions, query)
                )
            )
        }
    }

    /**
     * Groupe les transactions par date avec format français.
     */
    private fun grouperTransactionsParDate(transactions: List<TransactionUi>): Map<String, List<TransactionUi>> =
        RegroupementHistorique.grouper(transactions)

    /**
     * Charge les transactions pour un compte spécifique.
     */
    private fun chargerTransactions(compteId: String, collectionCompte: String) {
        chargementJob?.cancel()
        chargementJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // ✅ OPTIMISATION : Récupérer toutes les données en parallèle
                val deferredTransactions = async { 
                    transactionRepository.recupererTransactionsPourCompte(compteId, collectionCompte)
                }
                val deferredEnveloppes = async { 
                    enveloppeRepository.recupererToutesLesEnveloppes()
                }
                // Attendre que tous les appels soient terminés
                val resultTransactions = deferredTransactions.await()
                val resultEnveloppes = deferredEnveloppes.await()

                // L'historique appartient au compte : seules ses transactions sont obligatoires.
                // Les enveloppes/allocations ne servent qu'à enrichir les libellés et ne doivent
                // jamais empêcher l'affichage des mouvements financiers.
                val transactions = resultTransactions.getOrThrow()
                val avertissements = mutableListOf<String>()
                val enveloppes = resultEnveloppes.getOrElse { cause ->
                    avertissements += "Certaines informations d'enveloppes n'ont pas pu être chargées."
                    emptyList()
                }
                val idsAllocations = transactions.mapNotNull { it.allocationMensuelleId }.distinct()
                val allocations = enveloppeRepository.recupererAllocationsParIds(idsAllocations)
                    .getOrElse {
                        avertissements += "Certaines allocations historiques sont indisponibles."
                        emptyList()
                    }
                if (allocations.map { it.id }.toSet().size < idsAllocations.size) {
                    avertissements += "Certaines allocations historiques sont introuvables."
                }

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
                            LibellesHistorique.enveloppe(allocation.enveloppeId, enveloppesMap)
                        } else {
                            LibellesHistorique.allocationIntrouvable(allocationId)
                        }
                    }

                    // ✅ OPTIMISATION : Utiliser la map des enveloppes
                    val nomsEnveloppesFractions = if (transaction.estFractionnee) {
                        try {
                            PortionsHistorique.lire(transaction.sousItems).map { portion ->
                                LibellesHistorique.enveloppe(portion.enveloppeId, enveloppesMap)
                            }
                        } catch (_: Exception) {
                            // Le mouvement complet garde son vrai montant; seules
                            // ses portions sont impossibles à afficher. Le signaler.
                            avertissements += "Certaines fractions historiques sont illisibles."
                            emptyList()
                        }
                    } else emptyList()

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
                        nomsEnveloppesFractions = nomsEnveloppesFractions,
                        compteId = transaction.compteId,
                        nomCompte = _uiState.value.nomCompte,
                        collectionCompte = transaction.collectionCompte,
                        tiersBrut = transaction.tiersUtiliser,
                        allocationMensuelleId = transaction.allocationMensuelleId,
                        revisionTransaction = transaction.updated
                    )
                }.sortedByDescending { it.date }
                val doublons = RegroupementHistorique.nombreDoublons(transactionsUi)
                if (doublons > 0) {
                    avertissements += "$doublons doublon(s) d'ID écarté(s) de l'affichage."
                }

                // Utiliser la nouvelle méthode de groupement

                // Un ancien chargement annulé ne doit jamais publier un résultat tardif.
                coroutineContext.ensureActive()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        erreur = null,
                        avertissement = avertissements.distinct().takeIf { it.isNotEmpty() }?.joinToString(" "),
                        transactions = transactionsUi,
                        transactionsGroupees = grouperTransactionsParDate(
                            RechercheHistorique.filtrer(transactionsUi, it.searchQuery)
                        )
                    )
                }
                
            } catch (annulation: CancellationException) {
                throw annulation // ancien chargement annulé, le nouveau conserve la main
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, erreur = "Chargement impossible : ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }
}
