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
        supprimerSiNonModifiee(transactionId, null)
    }

    fun supprimerTransactionConfirmee(affichee: TransactionUi) {
        supprimerSiNonModifiee(affichee.id, affichee)
    }

    private fun supprimerSiNonModifiee(transactionId: String, affichee: TransactionUi?) {
        viewModelScope.lancerUneFois(verrouSuppression) {
            try {
                // HE5 : ne supprimer qu'un mouvement actuellement attribué à
                // l'enveloppe ouverte ET toujours visible dans la session courante.
                require(_uiState.value.transactions.any { it.id == transactionId }) {
                    "Cette transaction ne figure plus dans cette enveloppe."
                }
                val visiblesUtilisateur = transactionRepository
                    .recupererToutesLesTransactions().getOrThrow()
                require(visiblesUtilisateur.any { it.id == transactionId }) {
                    "Cette transaction n'appartient pas à la session courante."
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
    fun selectionnerVuePeriode(vue: VuePeriodeHistorique) {
        _uiState.update { actuel ->
            val periode = actuel.periode.copy(vue = vue)
            actuel.copy(
                periode = periode,
                scrollPosition = 0, scrollOffset = 0,
                transactionsGroupees = grouperTransactionsParDate(
                    periode.filtrer(RechercheHistorique.filtrer(actuel.transactions, actuel.searchQuery))
                )
            )
        }
    }

    fun deplacerPeriode(pas: Int) {
        _uiState.update { actuel ->
            val periode = actuel.periode.deplacer(pas)
            actuel.copy(
                periode = periode,
                scrollPosition = 0, scrollOffset = 0,
                transactionsGroupees = grouperTransactionsParDate(
                    periode.filtrer(RechercheHistorique.filtrer(actuel.transactions, actuel.searchQuery))
                )
            )
        }
    }

    fun mettreAJourRecherche(query: String) {
        // Une seule émission d'état : éviter d'afficher brièvement la nouvelle
        // recherche avec la vieille liste (et les sauts de scroll associés).
        _uiState.update { actuel ->
            actuel.copy(
                searchQuery = query,
                transactionsGroupees = grouperTransactionsParDate(
                    actuel.periode.filtrer(RechercheHistorique.filtrer(actuel.transactions, query))
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

                // Les transactions sont obligatoires. Si seules les MÉTADONNÉES
                // d'enveloppes manquent, montrer quand même les montants sûrs et
                // signaler explicitement que les libellés sont incomplets.
                val transactions = resultTransactions.getOrThrow()
                var enveloppesIndisponibles = false
                val enveloppes = resultEnveloppes.getOrElse {
                    enveloppesIndisponibles = true
                    emptyList()
                }
                // HE1 : chaque allocation est retrouvée directement par son ID, quelle que soit sa période.
                val idsAllocations = transactions.mapNotNull { it.allocationMensuelleId }.distinct()
                var erreurLectureAllocations = false
                val allocations = enveloppeRepository.recupererAllocationsParIds(idsAllocations)
                    .getOrElse {
                        erreurLectureAllocations = true
                        emptyList()
                    }
                val allocationsIndisponibles = idsAllocations.size -
                    allocations.map { it.id }.toSet().size

                // ✅ OPTIMISATION : Créer des maps pour un accès rapide
                val enveloppeIdToNom = enveloppes.associateBy({ it.id }, { it.nom })
                val allocationIdToEnveloppeId = allocations.associateBy({ it.id }, { it.enveloppeId })

                // Filtrer les transactions pour cette enveloppe spécifique
                // Un JSON de fractions illisible doit être signalé, pas devenir
                // silencieusement une transaction "absente".
                var fractionsIllisibles = 0
                var fractionsNonReconciliables = 0
                val portionsValides = mutableMapOf<String, List<PortionsHistorique.Portion>>()
                val transactionsEnveloppe = transactions.filter { transaction ->
                    // Valider toutes les portions AVANT de montrer la transaction.
                    // Même une portion absent ne doit jamais apparaître à 0 $.
                    val portions = if (transaction.estFractionnee) {
                        try {
                            PortionsHistorique.lire(transaction.sousItems).also { fractions ->
                                portionsValides[transaction.id] = fractions
                                if (!PortionsHistorique.verifierTotal(fractions, transaction.montant)) {
                                    fractionsNonReconciliables++
                                }
                            }
                        } catch (_: Exception) {
                            fractionsIllisibles++
                            return@filter false
                        }
                    } else emptyList()
                    // Pour une fraction, SEULE sa ventilation prouve l'enveloppe;
                    // une allocation globale historique ne doit pas attribuer
                    // accidentellement le total à la mauvaise enveloppe.
                    if (transaction.estFractionnee) {
                        portions.any { it.enveloppeId == enveloppeId }
                    } else {
                        val allocation = transaction.allocationMensuelleId
                        allocation != null && allocationIdToEnveloppeId[allocation] == enveloppeId
                    }
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
                    val montantPourEnveloppe = if (transaction.estFractionnee) {
                        PortionsHistorique.totalPourEnveloppe(
                            portionsValides[transaction.id]
                                ?: error("Fractions validées introuvables."),
                            enveloppeId
                        )
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
                        sousItems = transaction.sousItems,
                        nomsEnveloppesFractions = portionsValides[transaction.id]
                            ?.map { portion ->
                                enveloppeIdToNom[portion.enveloppeId]
                                    ?: com.xburnsx.toutiebudget.utils.LIBELLE_SANS_ENVELOPPE
                            } ?: emptyList(),
                        compteId = transaction.compteId,
                        collectionCompte = transaction.collectionCompte,
                        montantTotalTransaction = if (transaction.estFractionnee) transaction.montant else null,
                        tiersBrut = transaction.tiersUtiliser,
                        allocationMensuelleId = transaction.allocationMensuelleId,
                        revisionTransaction = transaction.updated
                    )
                }
                val doublons = RegroupementHistorique.nombreDoublons(transactionsUi)

                // Utiliser la nouvelle méthode de groupement

                // Ne pas écraser la lecture récente par un chargement annulé.
                coroutineContext.ensureActive()
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        transactions = transactionsUi,
                        transactionsGroupees = grouperTransactionsParDate(
                            it.periode.filtrer(RechercheHistorique.filtrer(transactionsUi, it.searchQuery))
                        ),
                        erreur = null,
                        avertissement = buildList {
                            if (enveloppesIndisponibles)
                                add("Les noms d'enveloppes sont momentanément indisponibles.")
                            if (erreurLectureAllocations)
                                add("Lecture des allocations impossible.")
                            if (allocationsIndisponibles > 0)
                                add("$allocationsIndisponibles allocation(s) historique(s) introuvable(s).")
                            if (fractionsIllisibles > 0)
                                add("$fractionsIllisibles transaction(s) fractionnée(s) illisible(s).")
                            if (fractionsNonReconciliables > 0)
                                add("$fractionsNonReconciliables achat(s) avec répartition différente du total.")
                            if (doublons > 0)
                                add("$doublons doublon(s) de transaction écarté(s) de l'affichage.")
                        }.takeIf { it.isNotEmpty() }?.joinToString(" ")?.let {
                            "$it Historique possiblement incomplet."
                        }
                    )
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
