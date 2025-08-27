package com.xburnsx.toutiebudget.ui.statistiques

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.TiersRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

class StatistiquesViewModel(
    private val transactionRepository: TransactionRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val tiersRepository: TiersRepository,
    private val categorieRepository: com.xburnsx.toutiebudget.data.repositories.CategorieRepository,
    private val realtimeSyncService: RealtimeSyncService
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatistiquesUiState(isLoading = true))
    val uiState: StateFlow<StatistiquesUiState> = _uiState

    // Map pour résoudre allocation -> enveloppe
    private var allocationIdToEnveloppeId: Map<String, String> = emptyMap()
    private val ID_SANS_ENVELOPPE = "__SANS_ENV__"

    init {
        rafraichirDonneesMoisCourant()
        
        // 🚀 TEMPS RÉEL : Écoute des changements PocketBase
        viewModelScope.launch {
            realtimeSyncService.budgetUpdated.collectLatest {
                // Recharger les statistiques quand le budget change
                rafraichirDonneesMoisCourant()
            }
        }
        
        viewModelScope.launch {
            realtimeSyncService.transactionsUpdated.collectLatest {
                // Recharger les statistiques quand les transactions changent
                rafraichirDonneesMoisCourant()
            }
        }
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

            // Exclure les enveloppes de catégories "Dettes" et "Cartes de crédit" des Top 5
            val categories = try { categorieRepository.recupererToutesLesCategories().getOrNull().orEmpty() } catch (_: Exception) { emptyList() }
            val categoriesExclues = categories.filter { it.nom.equals("Dettes", ignoreCase = true) || it.nom.equals("Cartes de crédit", ignoreCase = true) }
                .map { it.id }
                .toSet()
            val enveloppeIdsExclus = enveloppes.filter { it.categorieId in categoriesExclues }.map { it.id }.toSet()

            // Filtrer uniquement les transactions monétaires pertinentes pour les tops
            val depenses = transactions.filter { 
                it.type == TypeTransaction.Depense || 
                it.type == TypeTransaction.Pret || 
                it.type == TypeTransaction.RemboursementDonne || 
                it.type == TypeTransaction.PaiementEffectue
            }
            val revenus = transactions.filter { 
                it.type == TypeTransaction.Revenu || 
                it.type == TypeTransaction.Emprunt || 
                it.type == TypeTransaction.RemboursementRecu
                // ❌ SUPPRIMÉ : TypeTransaction.Paiement - ce n'est pas un revenu, c'est de l'argent pour payer une dette !
            }

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
                val envId = if (tx.type == TypeTransaction.PaiementEffectue) {
                    // Pour les PaiementEffectue, utiliser un ID spécial pour les identifier
                    "__PAIEMENT_EFFECTUE__"
                } else {
                    tx.allocationMensuelleId?.let { allocationIdToEnveloppeId[it] } ?: ID_SANS_ENVELOPPE
                }
                if (envId != ID_SANS_ENVELOPPE && envId != "__PAIEMENT_EFFECTUE__" && envId in enveloppeIdsExclus) {
                    // ignorer les dépenses sur Dettes/Cartes de crédit
                } else {
                    depenseParEnveloppeSimple[envId] = (depenseParEnveloppeSimple[envId] ?: 0.0) + tx.montant
                }
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
                        if (!envId.isNullOrBlank() && envId !in enveloppeIdsExclus) {
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
                    val label = when (envId) {
                        ID_SANS_ENVELOPPE -> com.xburnsx.toutiebudget.utils.LIBELLE_SANS_ENVELOPPE
                        "__PAIEMENT_EFFECTUE__" -> "Paiements de dettes/cartes"
                        else -> enveloppeIdParNom[envId] ?: com.xburnsx.toutiebudget.utils.LIBELLE_SANS_ENVELOPPE
                    }
                    TopItem(id = envId, label = label, montant = montant, pourcentage = 0.0)
                }
                .sortedByDescending { it.montant }
                .take(5)
                .map { it.copy(pourcentage = it.montant / totalDepenses.coerceAtLeast(0.01)) }

            // Top 5 Tiers
            val tiersToNom = tiers.associateBy({ it.id }, { it.nom })
            // Calculer les dépenses par tiers en excluant les enveloppes de catégories "Dettes"/"Cartes de crédit"
            val depenseParTiersMutable = mutableMapOf<String, Double>()
            depenses.forEach { tx ->
                val tiersKey = tx.tiersUtiliser ?: return@forEach
                if (!tx.estFractionnee) {
                    val envId = tx.allocationMensuelleId?.let { allocationIdToEnveloppeId[it] }
                    if (envId == null || envId !in enveloppeIdsExclus) {
                        depenseParTiersMutable[tiersKey] = (depenseParTiersMutable[tiersKey] ?: 0.0) + tx.montant
                    }
                } else if (!tx.sousItems.isNullOrBlank()) {
                    try {
                        val arr = com.google.gson.JsonParser.parseString(tx.sousItems).asJsonArray
                        arr.forEach { elem ->
                            val obj = elem.asJsonObject
                            val envId = obj.get("enveloppeId")?.asString
                            val montant = obj.get("montant")?.asDouble ?: 0.0
                            if (!envId.isNullOrBlank() && envId !in enveloppeIdsExclus) {
                                depenseParTiersMutable[tiersKey] = (depenseParTiersMutable[tiersKey] ?: 0.0) + montant
                            }
                        }
                    } catch (_: Exception) { /* ignorer */ }
                }
            }
            val depenseParTiers = depenseParTiersMutable

            val top5Tiers = depenseParTiers
                .entries
                .map { (tiersKey, montant) ->
                    val label = tiersToNom[tiersKey] ?: tiersKey
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
                val total = transactions6Mois.filter { 
                    (it.type == TypeTransaction.Depense || 
                     it.type == TypeTransaction.Pret || 
                     it.type == TypeTransaction.RemboursementDonne || 
                     it.type == TypeTransaction.PaiementEffectue) && 
                    it.date >= mStart && it.date <= mEnd 
                }.sumOf { it.montant }
                sixMoisLabels[idx] to total
            }
            val revenus6DerniersMois = sixMoisDates.mapIndexed { idx, (mStart, mEnd) ->
                val total = transactions6Mois.filter { 
                    (it.type == TypeTransaction.Revenu || 
                     it.type == TypeTransaction.Emprunt || 
                     it.type == TypeTransaction.RemboursementRecu) && 
                    it.date >= mStart && it.date <= mEnd 
                    // ❌ SUPPRIMÉ : TypeTransaction.Paiement - ce n'est pas un revenu, c'est de l'argent pour payer une dette !
                }.sumOf { it.montant }
                sixMoisLabels[idx] to total
            }

            // Courbe cumulée du mois (cashflow net) et MM7
            // Construire la liste de tous les jours du mois sélectionné
            val jours = mutableListOf<Date>()
            val calDebut = Calendar.getInstance().apply { time = debut }
            val calFin = Calendar.getInstance().apply { time = fin }
            // Si le mois sélectionné est le mois courant, ne pas dépasser aujourd'hui
            run {
                val now = Date()
                val calNow = Calendar.getInstance().apply { time = now }
                val memeMois = calDebut.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                        calDebut.get(Calendar.MONTH) == calNow.get(Calendar.MONTH)
                if (memeMois) {
                    // Fin effective = aujourd'hui 23:59:59
                    calFin.time = calNow.time
                    calFin.set(Calendar.HOUR_OF_DAY, 23)
                    calFin.set(Calendar.MINUTE, 59)
                    calFin.set(Calendar.SECOND, 59)
                    calFin.set(Calendar.MILLISECOND, 999)
                }
            }
            val calJour = calDebut
            while (calJour.time <= calFin.time) {
                jours += calJour.time
                calJour.add(Calendar.DAY_OF_MONTH, 1)
            }

            // Net par jour
            val netParJour = jours.map { jour ->
                val start = Calendar.getInstance().apply {
                    time = jour
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.time
                val end = Calendar.getInstance().apply {
                    time = jour
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                }.time
                            val revenusJour = transactions.filter { 
                (it.type == TypeTransaction.Revenu || 
                 it.type == TypeTransaction.Emprunt || 
                 it.type == TypeTransaction.RemboursementRecu) && 
                it.date >= start && it.date <= end 
                // ❌ SUPPRIMÉ : TypeTransaction.Paiement - ce n'est pas un revenu, c'est de l'argent pour payer une dette !
            }.sumOf { it.montant }
            val depensesJour = transactions.filter { 
                (it.type == TypeTransaction.Depense || 
                 it.type == TypeTransaction.Pret || 
                 it.type == TypeTransaction.RemboursementDonne || 
                 it.type == TypeTransaction.PaiementEffectue) && 
                it.date >= start && it.date <= end 
            }.sumOf { it.montant }
                revenusJour - depensesJour
            }

            // Cumul
            var cumul = 0.0
            val cumulMensuel = netParJour.map { net ->
                cumul += net
                cumul
            }

            // MM7 sur net quotidien
            val mm7 = netParJour.indices.map { i ->
                val debutIdx = maxOf(0, i - 6)
                val sousListe = netParJour.subList(debutIdx, i + 1)
                sousListe.average()
            }

            val labelsJours = jours.map { java.text.SimpleDateFormat("d", java.util.Locale.FRENCH).format(it) }

            val cashflowCumulMensuel = labelsJours.zip(cumulMensuel)
            val moyenneMobile7Jours = labelsJours.zip(mm7)

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
                tiersToNom = tiersToNom,
                cashflowCumulMensuel = cashflowCumulMensuel,
                moyenneMobile7Jours = moyenneMobile7Jours
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
            if (tx.type != TypeTransaction.Depense && 
                tx.type != TypeTransaction.Pret && 
                tx.type != TypeTransaction.RemboursementDonne && 
                tx.type != TypeTransaction.PaiementEffectue) return@filter false
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

    // Ouvrir la modal pour toutes les dépenses de la période
    fun ouvrirModalDepenses() {
        val txPeriode = _uiState.value.transactionsPeriode
        val depenses = txPeriode.filter { tx ->
            tx.type == TypeTransaction.Depense || 
            tx.type == TypeTransaction.Pret || 
            tx.type == TypeTransaction.RemboursementDonne || 
            tx.type == TypeTransaction.PaiementEffectue
        }
        ouvrirModalTransactions("Toutes les dépenses - ${_uiState.value.periode?.label ?: "Période"}", depenses)
    }

    // Ouvrir la modal pour tous les revenus de la période
    fun ouvrirModalRevenus() {
        val txPeriode = _uiState.value.transactionsPeriode
        val revenus = txPeriode.filter { tx ->
            tx.type == TypeTransaction.Revenu || 
            tx.type == TypeTransaction.Emprunt || 
            tx.type == TypeTransaction.RemboursementRecu
            // ❌ SUPPRIMÉ : TypeTransaction.Paiement - ce n'est pas un revenu, c'est de l'argent pour payer une dette !
        }
        ouvrirModalTransactions("Tous les revenus - ${_uiState.value.periode?.label ?: "Période"}", revenus)
    }
}

