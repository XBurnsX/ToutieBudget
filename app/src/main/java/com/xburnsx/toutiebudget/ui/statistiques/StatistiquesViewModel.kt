package com.xburnsx.toutiebudget.ui.statistiques

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.TiersRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

class StatistiquesViewModel(
    private val transactionRepository: TransactionRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val tiersRepository: TiersRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatistiquesUiState(isLoading = true))
    val uiState: StateFlow<StatistiquesUiState> = _uiState

    // Map pour résoudre allocation -> enveloppe
    private var allocationIdToEnveloppeId: Map<String, String> = emptyMap()
    private val ID_SANS_ENVELOPPE = "__SANS_ENV__"

    init {
        rafraichirDonneesMoisCourant()
    }

    fun rafraichirDonneesMoisCourant() {
        val now = Date()
        val cal = Calendar.getInstance().apply { time = now; set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val debut = cal.time
        val fin = Calendar.getInstance().apply { time = debut; add(Calendar.MONTH, 1); add(Calendar.MILLISECOND, -1) }.time
        val label = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.FRENCH)
            .format(debut)
            .replaceFirstChar { it.uppercase() }
        chargerPeriode(debut, fin, label)
    }

    fun chargerPeriode(debut: Date, fin: Date, label: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, erreur = null)
            val periode = Periode(debut, fin, label)

            // Charger toutes les transactions sur la fenêtre des 6 derniers mois (pour le graphique)
            val calSix = Calendar.getInstance().apply {
                time = debut
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.MONTH, -5)
            }
            val debut6Mois = calSix.time
            val transactionsResult = transactionRepository.recupererTransactionsParPeriode(debut6Mois, fin)
            val enveloppesResult = enveloppeRepository.recupererToutesLesEnveloppes()
            // Récupérer les allocations pour chaque mois couvert par la période (stabilise les noms sur 30/90 jours)
            val allocationsToutes = mutableListOf<com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle>()
            run {
                val cal = Calendar.getInstance().apply { time = debut; set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                while (cal.time <= fin) {
                    val res = enveloppeRepository.recupererAllocationsPourMois(cal.time)
                    if (res.isSuccess) allocationsToutes += res.getOrNull().orEmpty()
                    cal.add(Calendar.MONTH, 1)
                }
            }
            val tiersResult = tiersRepository.recupererTousLesTiers()

            if (transactionsResult.isFailure || enveloppesResult.isFailure || tiersResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    erreur = (transactionsResult.exceptionOrNull()
                        ?: enveloppesResult.exceptionOrNull()
                        ?: tiersResult.exceptionOrNull())?.message,
                    periode = periode
                )
                return@launch
            }

            val transactions6Mois = transactionsResult.getOrNull().orEmpty()
            // Transactions strictement pour la période sélectionnée (tops, KPIs)
            val transactions = transactions6Mois.filter { it.date >= debut && it.date <= fin }
            val enveloppes = enveloppesResult.getOrNull().orEmpty()
            val allocations = allocationsToutes
            val tiers = tiersResult.getOrNull().orEmpty()

            // Filtrer uniquement les transactions monétaires pertinentes pour les tops
            val depenses = transactions.filter { it.type == TypeTransaction.Depense }
            val revenus = transactions.filter { it.type == TypeTransaction.Revenu }

            val totalDepenses = depenses.sumOf { it.montant }
            val totalRevenus = revenus.sumOf { it.montant }
            val totalNet = totalRevenus - totalDepenses

            // Top 5 Enveloppes dépensées (exclure dettes/cartes crédit etc.)
            // Règle: ne considérer que les transactions ayant allocationMensuelleId non null
            allocationIdToEnveloppeId = allocations.associate { it.id to it.enveloppeId }

            val enveloppeIdParNom = enveloppes.associateBy({ it.id }, { it.nom })

            // 1) Agréger les transactions simples (non fractionnées) par enveloppe via allocation -> enveloppe
            val depenseParEnveloppeSimple: MutableMap<String, Double> = mutableMapOf()
            depenses.filter { !it.estFractionnee }.forEach { tx ->
                val envId = tx.allocationMensuelleId?.let { allocationIdToEnveloppeId[it] } ?: ID_SANS_ENVELOPPE
                depenseParEnveloppeSimple[envId] = (depenseParEnveloppeSimple[envId] ?: 0.0) + tx.montant
            }

            // 2) Agréger les sous-items des transactions fractionnées par enveloppeId
            val depenseParEnveloppeFractions: MutableMap<String, Double> = mutableMapOf()
            depenses.filter { it.estFractionnee && !it.sousItems.isNullOrBlank() }.forEach { tx ->
                try {
                    val arr = com.google.gson.JsonParser.parseString(tx.sousItems).asJsonArray
                    arr.forEach { elem ->
                        val obj = elem.asJsonObject
                        val envId = obj.get("enveloppeId")?.asString
                        val montant = obj.get("montant")?.asDouble ?: 0.0
                        if (!envId.isNullOrBlank()) {
                            depenseParEnveloppeFractions[envId] = (depenseParEnveloppeFractions[envId] ?: 0.0) + montant
                        }
                    }
                } catch (_: Exception) { /* ignorer */ }
            }

            // 3) Fusionner les deux sources
            val depenseParEnveloppe: Map<String, Double> =
                (depenseParEnveloppeSimple.keys + depenseParEnveloppeFractions.keys)
                    .associateWith { envId ->
                        (depenseParEnveloppeSimple[envId] ?: 0.0) + (depenseParEnveloppeFractions[envId] ?: 0.0)
                    }

            // 4) Construire le Top 5 par enveloppe
            val top5Enveloppes = depenseParEnveloppe.entries
                .map { (envId, montant) ->
                    val label = if (envId == ID_SANS_ENVELOPPE) com.xburnsx.toutiebudget.utils.LIBELLE_SANS_ENVELOPPE else (enveloppeIdParNom[envId]
                        ?: com.xburnsx.toutiebudget.utils.LIBELLE_SANS_ENVELOPPE)
                    TopItem(id = envId, label = label, montant = montant, pourcentage = 0.0)
                }
                .sortedByDescending { it.montant }
                .take(5)
                .map { it.copy(pourcentage = it.montant / totalDepenses.coerceAtLeast(0.01)) }

            // Top 5 Tiers
            val tiersIdToNom = tiers.associateBy({ it.id }, { it.nom })
            val depenseParTiers = depenses
                .filter { it.tiersId != null || !it.tiers.isNullOrBlank() }
                .groupBy { it.tiersId ?: it.tiers!! }
                .mapValues { (_, list) -> list.sumOf { it.montant } }

            val top5Tiers = depenseParTiers
                .entries
                .map { (tiersKey, montant) ->
                    val label = tiersIdToNom[tiersKey] ?: tiersKey
                    TopItem(id = tiersKey, label = label, montant = montant, pourcentage = 0.0)
                }
                .sortedByDescending { it.montant }
                .take(5)
                .map { it.copy(pourcentage = it.montant / totalDepenses.coerceAtLeast(0.01)) }

            // Dépenses des 6 derniers mois (étiquette MMM)
            val sixMoisLabels = mutableListOf<String>()
            val sixMoisDates = mutableListOf<Pair<Date, Date>>()
            run {
                val calStart = Calendar.getInstance().apply { time = debut }
                // remonter 5 mois pour avoir 6 mois glissants finissant au mois courant
                calStart.add(Calendar.MONTH, -5)
                for (i in 0 until 6) {
                    val mStart = calStart.time
                    val mEnd = Calendar.getInstance().apply { time = mStart; add(Calendar.MONTH, 1); add(Calendar.MILLISECOND, -1) }.time
                    sixMoisDates += mStart to mEnd
                    sixMoisLabels += java.text.SimpleDateFormat("MMM", java.util.Locale.FRENCH)
                        .format(mStart)
                        .replaceFirstChar { it.uppercase() }
                    calStart.add(Calendar.MONTH, 1)
                }
            }
            val depenses6DerniersMois = sixMoisDates.mapIndexed { idx, (mStart, mEnd) ->
                val total = transactions6Mois.filter { it.type == TypeTransaction.Depense && it.date >= mStart && it.date <= mEnd }.sumOf { it.montant }
                sixMoisLabels[idx] to total
            }
            val revenus6DerniersMois = sixMoisDates.mapIndexed { idx, (mStart, mEnd) ->
                val total = transactions6Mois.filter { it.type == TypeTransaction.Revenu && it.date >= mStart && it.date <= mEnd }.sumOf { it.montant }
                sixMoisLabels[idx] to total
            }

            _uiState.value = StatistiquesUiState(
                isLoading = false,
                erreur = null,
                periode = periode,
                totalDepenses = totalDepenses,
                totalRevenus = totalRevenus,
                totalNet = totalNet,
                transactionsPeriode = transactions,
                top5Enveloppes = top5Enveloppes,
                top5Tiers = top5Tiers,
                depenses6DerniersMois = depenses6DerniersMois,
                revenus6DerniersMois = revenus6DerniersMois,
                tiersIdToNom = tiersIdToNom
            )
        }
    }

    fun ouvrirModalTransactions(titre: String, transactions: List<Transaction>) {
        _uiState.value = _uiState.value.copy(
            modalOuvert = true,
            modalTitre = titre,
            modalTransactions = transactions
        )
    }

    // Ouvrir la modal pour une enveloppe spécifique (inclut transactions fractionnées)
    fun ouvrirModalTransactionsPourEnveloppe(enveloppeId: String) {
        val txPeriode = _uiState.value.transactionsPeriode
        val selection = txPeriode.filter { tx ->
            if (tx.type != TypeTransaction.Depense) return@filter false
            // 1) Cas simple: allocation -> enveloppe
            val allocId = tx.allocationMensuelleId
            val matchSimple = allocId != null && allocationIdToEnveloppeId[allocId] == enveloppeId
            if (matchSimple) return@filter true
            // 2) Cas transaction fractionnée: sous-items contenant l'enveloppe
            if (tx.estFractionnee && !tx.sousItems.isNullOrBlank()) {
                try {
                    val arr = com.google.gson.JsonParser.parseString(tx.sousItems).asJsonArray
                    arr.any { elem -> elem.asJsonObject.get("enveloppeId")?.asString == enveloppeId }
                } catch (_: Exception) { false }
            } else false
        }
        ouvrirModalTransactions("Transactions - ${_uiState.value.top5Enveloppes.firstOrNull { it.id == enveloppeId }?.label ?: "Enveloppe"}", selection)
    }

    fun fermerModalTransactions() {
        _uiState.value = _uiState.value.copy(
            modalOuvert = false,
            modalTitre = "",
            modalTransactions = emptyList()
        )
    }
}

