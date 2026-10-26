package com.xburnsx.toutiebudget.ui.statistiques

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.TiersRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.PretPersonnelRepository
import com.xburnsx.toutiebudget.data.services.RealtimeSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.*

class StatistiquesViewModel(
    private val transactionRepository: TransactionRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val tiersRepository: TiersRepository,
    private val categorieRepository: com.xburnsx.toutiebudget.data.repositories.CategorieRepository,
    private val compteRepository: CompteRepository,
    private val pretPersonnelRepository: PretPersonnelRepository,
    private val realtimeSyncService: RealtimeSyncService
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatistiquesUiState(isLoading = true))
    val uiState: StateFlow<StatistiquesUiState> = _uiState

    // Map pour résoudre allocation -> enveloppe
    private var allocationIdToEnveloppeId: Map<String, String> = emptyMap()
    private val ID_SANS_ENVELOPPE = "__SANS_ENV__"
    
    // Pagination pour les listes de transactions
    private val _pageSize = 50
    private var _currentPage = 0
    private var _hasMoreData = true
    private var chargement: Job? = null
    private var generationChargement = 0L

    init {
        rafraichirDonneesMoisCourant()
        
        // 🚀 TEMPS RÉEL : Écoute des changements PocketBase
        viewModelScope.launch {
            realtimeSyncService.budgetUpdated.collectLatest {
                // Recharger les statistiques quand le budget change
                rafraichirPeriodeSelectionnee()
            }
        }
        
        viewModelScope.launch {
            realtimeSyncService.transactionsUpdated.collectLatest {
                // Recharger les statistiques quand les transactions changent
                rafraichirPeriodeSelectionnee()
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
        // DEBUG ViewModel: 🚀 chargerPeriode appelé avec début: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.FRENCH).format(debut)}, fin: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.FRENCH).format(fin)}, label: $label
        chargement?.cancel()
        val generation = ++generationChargement
        reinitialiserPagination()
        chargement = viewModelScope.launch {
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
            val erreursAllocations = mutableListOf<String>()
            run {
                val cal = Calendar.getInstance().apply { time = debut6Mois }
                while (cal.time <= fin) {
                    val res = enveloppeRepository.recupererAllocationsPourMois(cal.time)
                    if (res.isSuccess) allocationsToutes += res.getOrNull().orEmpty()
                    else erreursAllocations += java.text.SimpleDateFormat("MMMM yyyy", Locale.FRENCH).format(cal.time)
                    cal.add(Calendar.MONTH, 1)
                }
            }
            val tiersResult = tiersRepository.recupererTousLesTiers()
            val categoriesResult = categorieRepository.recupererToutesLesCategories()

            if (transactionsResult.isFailure || enveloppesResult.isFailure || tiersResult.isFailure || categoriesResult.isFailure) {
                if (generation != generationChargement) return@launch
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    erreur = (transactionsResult.exceptionOrNull()
                        ?: enveloppesResult.exceptionOrNull()
                        ?: tiersResult.exceptionOrNull()
                        ?: categoriesResult.exceptionOrNull())?.message ?: "Calcul incomplet : données indisponibles",
                    calculIncomplet = true,
                    periode = periode
                )
                return@launch
            }
            
            // Calculer les totaux financiers
            val totauxFinanciers = try {
                calculerTotauxFinanciers()
            } catch (e: Exception) {
                if (generation != generationChargement) return@launch
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    erreur = "Patrimoine indisponible : " + (e.message ?: "chargement des comptes ou prêts échoué"),
                    calculIncomplet = true,
                    donneesIndisponibles = listOf("Comptes / prêts personnels"),
                    periode = periode
                )
                return@launch
            }

            // IF2 / IF12 / CC2 : les anciennes écritures techniques ne sont ni des revenus ni des dépenses
            // (réconciliations enregistrées « Mise à jour : … », paiements de carte enregistrés « Emprunt »)
            val filtreComptes = _uiState.value.comptesFiltres
            val transactions6Mois = transactionsResult.getOrNull().orEmpty().filterNot { tx ->
                ((tx.type == TypeTransaction.Revenu || tx.type == TypeTransaction.Depense) &&
                    tx.tiersUtiliser?.startsWith("Mise à jour : ") == true) ||
                    (tx.type == TypeTransaction.Emprunt && tx.collectionCompte == "comptes_credits")
            }.filter { filtreComptes.isEmpty() || it.compteId in filtreComptes }
            // Transactions strictement pour la période sélectionnée (tops, KPIs)
            val transactions = transactions6Mois.filter { it.date >= debut && it.date <= fin }
            
            // DEBUG: Vérifier les transactions chargées
            // DEBUG: Transactions 6 mois total: ${transactions6Mois.size}
            // DEBUG: Transactions pour la période: ${transactions.size}
            if (transactions.isNotEmpty()) {
                transactions.take(3).forEach { tx ->
                    // DEBUG: Transaction exemple - Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.FRENCH).format(tx.date)} - Type: ${tx.type} - Montant: ${tx.montant}
                }
            }

            val enveloppes = enveloppesResult.getOrNull().orEmpty()
            val allocations = allocationsToutes
            val tiers = tiersResult.getOrNull().orEmpty()

            // Exclure les enveloppes de catégories "Dettes" et "Cartes de crédit" des Top 5
            val categories = categoriesResult.getOrThrow()
            val categoriesExclues = categories.filter { it.nom.equals("Dettes", ignoreCase = true) || it.nom.equals("Cartes de crédit", ignoreCase = true) }
                .map { it.id }
                .toSet()
            val enveloppeIdsExclus = enveloppes.filter { it.categorieId in categoriesExclues }.map { it.id }.toSet()

            // Filtrer uniquement les transactions monétaires pertinentes pour les tops
            val depenses = transactions.filter(ReglesStatistiques::estDepenseBudget)
            val revenus = transactions.filter(ReglesStatistiques::estRevenuBudget)

            val totalDepenses = ReglesStatistiques.totalAuCent(depenses) { true }
            val totalRevenus = ReglesStatistiques.totalAuCent(revenus) { true }
            val totalNet = com.xburnsx.toutiebudget.utils.MoneyFormatter.roundAmount(totalRevenus - totalDepenses)
            val totalSortiesTresorerie = ReglesStatistiques.totalAuCent(transactions, ReglesStatistiques::estSortieTresorerie)
            val totalEntreesTresorerie = ReglesStatistiques.totalAuCent(transactions, ReglesStatistiques::estEntreeTresorerie)

            // Top 5 Enveloppes dépensées (exclure dettes/cartes crédit etc.)
            // Règle: ne considérer que les transactions ayant allocationMensuelleId non null
            allocationIdToEnveloppeId = allocations.associate { it.id to it.enveloppeId }

            val enveloppeIdParNom = enveloppes.associateBy({ it.id }, { it.nom })

            // 1) Agréger les transactions simples (non fractionnées) par enveloppe via allocation -> enveloppe
            val depenseParEnveloppeSimple: MutableMap<String, Double> = mutableMapOf()
            depenses.filter { !it.estFractionnee }.forEach { tx ->
                val envId = when (tx.type) {
                    TypeTransaction.PaiementEffectue -> {
                        // Pour les PaiementEffectue, utiliser un ID spécial pour les identifier
                        "__PAIEMENT_EFFECTUE__"
                    }
                    TypeTransaction.RemboursementDonne, 
                    TypeTransaction.TransfertSortant,
                    TypeTransaction.Pret,
                    TypeTransaction.Emprunt -> {
                        // Ces types n'ont pas d'enveloppe par design - les ignorer des statistiques d'enveloppes
                        null
                    }
                    else -> {
                        // Pour les vraies dépenses, essayer de récupérer l'enveloppe
                        tx.allocationMensuelleId?.let { allocationIdToEnveloppeId[it] }
                    }
                }
                
                // Ne traiter que si on a un envId valide
                if (envId != null) {
                    if (envId != "__PAIEMENT_EFFECTUE__" && envId in enveloppeIdsExclus) {
                        // ignorer les dépenses sur Dettes/Cartes de crédit
                    } else {
                        depenseParEnveloppeSimple[envId] = (depenseParEnveloppeSimple[envId] ?: 0.0) + tx.montant
                    }
                }
                // Si envId est null, on ignore complètement la transaction (pas de "Sans enveloppe")
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
                        // Ne traiter que si on a un enveloppeId valide et qu'il n'est pas exclu
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

            // 4) Construire le Top 5 par enveloppe (maintenant sans "Sans enveloppe")
            val top5Enveloppes = depenseParEnveloppe.entries
                .map { (envId, montant) ->
                    val label = when (envId) {
                        "__PAIEMENT_EFFECTUE__" -> "Paiements de dettes/cartes"
                        else -> enveloppeIdParNom[envId] ?: "Enveloppe inconnue"
                    }
                    TopItem(id = envId, label = label, montant = montant, pourcentage = 0.0)
                }
                .sortedByDescending { it.montant }
                .take(5)
                .let { items ->
                    val totalAdmissible = depenseParEnveloppe.values.sum()
                    items.map { it.copy(pourcentage = ReglesStatistiques.pourcentage(it.montant, totalAdmissible)) }
                }

            // 5) Construire la répartition complète avec 20 enveloppes
            val repartitionTriee = depenseParEnveloppe.entries
                .map { (envId, montant) ->
                    val label = when (envId) {
                        "__PAIEMENT_EFFECTUE__" -> "Paiements de dettes/cartes"
                        else -> enveloppeIdParNom[envId] ?: "Enveloppe inconnue"
                    }
                    TopItem(id = envId, label = label, montant = montant, pourcentage = 0.0)
                }
                .sortedByDescending { it.montant }
            val totalRepartition = repartitionTriee.sumOf { it.montant }
            val repartitionEnveloppes = buildList {
                addAll(repartitionTriee.take(20))
                val autres = repartitionTriee.drop(20).sumOf { it.montant }
                if (autres > 0.0) add(TopItem("__AUTRES__", "Autres", autres, 0.0))
            }.map { it.copy(pourcentage = ReglesStatistiques.pourcentage(it.montant, totalRepartition)) }

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
                .let { items ->
                    val totalAdmissible = depenseParTiers.values.sum()
                    items.map { it.copy(pourcentage = ReglesStatistiques.pourcentage(it.montant, totalAdmissible)) }
                }

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
                val total = ReglesStatistiques.totalAuCent(transactions6Mois.filter { it.date >= mStart && it.date <= mEnd }, ReglesStatistiques::estDepenseBudget)
                sixMoisLabels[idx] to total
            }
            val revenus6DerniersMois = sixMoisDates.mapIndexed { idx, (mStart, mEnd) ->
                val total = ReglesStatistiques.totalAuCent(transactions6Mois.filter { it.date >= mStart && it.date <= mEnd }, ReglesStatistiques::estRevenuBudget)
                sixMoisLabels[idx] to total
            }

            // Calculer les moyennes sur 7 jours
            val moyennes7Jours = ReglesStatistiques.moyenneDepenses7Jours(transactions6Mois, fin)


            if (generation != generationChargement) return@launch
            _uiState.value = StatistiquesUiState(
                isLoading = false,
                erreur = null,
                calculIncomplet = erreursAllocations.isNotEmpty(),
                donneesIndisponibles = erreursAllocations.map { "Allocations $it" },
                periode = periode,
                totalDepenses = totalDepenses,
                totalRevenus = totalRevenus,
                totalNet = totalNet,
                transactionsPeriode = transactions,
                top5Enveloppes = top5Enveloppes,
                repartitionEnveloppes = repartitionEnveloppes,
                top5Tiers = top5Tiers,
                depenses6DerniersMois = depenses6DerniersMois,
                revenus6DerniersMois = revenus6DerniersMois,
                tiersToNom = tiersToNom,
                moyennes7Jours = moyennes7Jours,
                totalDette = totauxFinanciers.first,
                totalValeur = totauxFinanciers.second,
                valeurNette = totauxFinanciers.third
                ,patrimoineEstActuel = estMoisCourant(debut)
                ,vue = _uiState.value.vue
                ,comptesFiltres = filtreComptes
                ,totalEntreesTresorerie = totalEntreesTresorerie
                ,totalSortiesTresorerie = totalSortiesTresorerie
            )
        }
    }

    fun rafraichirPeriodeSelectionnee() {
        val p = _uiState.value.periode
        if (p == null) rafraichirDonneesMoisCourant() else chargerPeriode(p.debut, p.fin, p.label)
    }

    fun changerVue(vue: VueStatistiques) { _uiState.value = _uiState.value.copy(vue = vue) }

    fun filtrerComptes(ids: Set<String>) {
        _uiState.value = _uiState.value.copy(comptesFiltres = ids)
        rafraichirPeriodeSelectionnee()
    }

    private fun estMoisCourant(debut: Date): Boolean {
        val a = Calendar.getInstance().apply { time = debut }
        val b = Calendar.getInstance()
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
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
        val depenses = txPeriode.filter(ReglesStatistiques::estDepenseBudget)
        ouvrirModalTransactions("Toutes les dépenses - ${_uiState.value.periode?.label ?: "Période"}", depenses)
    }

    // Ouvrir la modal pour tous les revenus de la période
    fun ouvrirModalRevenus() {
        val txPeriode = _uiState.value.transactionsPeriode
        val revenus = txPeriode.filter(ReglesStatistiques::estRevenuBudget)
        ouvrirModalTransactions("Tous les revenus - ${_uiState.value.periode?.label ?: "Période"}", revenus)
    }
    
    // Fonctions de pagination pour les listes de transactions
    fun chargerPageSuivante() {
        if (!_hasMoreData) return
        
        _currentPage++
        val offset = _currentPage * _pageSize
        
        viewModelScope.launch {
            val periode = _uiState.value.periode
            if (periode != null) {
                val transactionsSupplementaires = transactionRepository.recupererTransactionsParPeriode(
                    periode.debut, 
                    periode.fin, 
                    _pageSize, 
                    offset
                )
                
                if (transactionsSupplementaires.isSuccess) {
                    val nouvellesTransactions = transactionsSupplementaires.getOrNull().orEmpty()
                    if (nouvellesTransactions.size < _pageSize) {
                        _hasMoreData = false
                    }
                    
                    // Mettre à jour l'état avec les nouvelles transactions
                    val transactionsActuelles = _uiState.value.transactionsPeriode.toMutableList()
                    transactionsActuelles.addAll(nouvellesTransactions)
                    val sansDoublons = transactionsActuelles.distinctBy { it.id }
                    
                    _uiState.value = _uiState.value.copy(
                        transactionsPeriode = sansDoublons,
                        hasMoreData = _hasMoreData
                    )
                }
            }
        }
    }
    
    fun reinitialiserPagination() {
        _currentPage = 0
        _hasMoreData = true
    }
    
    fun rafraichirListeTransactions() {
        reinitialiserPagination()
        val periode = _uiState.value.periode
        if (periode != null) {
            chargerPeriode(periode.debut, periode.fin, periode.label)
        }
    }
    
    // Fonction pour calculer les totaux financiers
    private suspend fun calculerTotauxFinanciers(): Triple<Double, Double, Double> {
        var totalDette = 0.0
        var totalValeur = 0.0
        
        try {
            // 1. Récupérer tous les comptes et filtrer par type
            val tousLesComptes = compteRepository.recupererTousLesComptes()
            if (tousLesComptes.isFailure) throw IllegalStateException("Impossible de charger les comptes", tousLesComptes.exceptionOrNull())
            if (tousLesComptes.isSuccess) {
                // IF16 : patrimoine COURANT = comptes actifs + dettes archivées encore dues (archivée ≠ remboursée) ;
                // les comptes archivés (anciens placements, chèques fermés) ne comptent plus
                val comptes = tousLesComptes.getOrNull().orEmpty().filter { compte ->
                    !compte.estArchive || (compte is com.xburnsx.toutiebudget.data.modeles.CompteDette && compte.soldeDette < -0.005)
                }
                
                
                // Filtrer les comptes par type
                comptes.forEach { compte ->
                    
                    when (compte.collection) {
                        "comptes_dettes" -> {
                            val compteDette = compte as com.xburnsx.toutiebudget.data.modeles.CompteDette
                            // Les dettes sont de l'argent qu'on DOIT (pas qu'on a)
                            // Si soldeDette est négatif, c'est une dette (on doit de l'argent)
                            // Si soldeDette est positif, c'est un crédit (on a de l'argent)
                            if (compteDette.soldeDette < 0) {
                                totalDette += kotlin.math.abs(compteDette.soldeDette)
                            } else {
                                totalValeur += compteDette.soldeDette
                            }
                        }
                        "comptes_credits" -> {
                            val compteCredit = compte as com.xburnsx.toutiebudget.data.modeles.CompteCredit  
                            // Les soldes utilisés des cartes de crédit sont des dettes
                            // Si soldeUtilise est négatif, c'est une dette (on doit de l'argent)
                            // Si soldeUtilise est positif, c'est un crédit (on a de l'argent)
                            if (compteCredit.soldeUtilise < 0) {
                                totalDette += kotlin.math.abs(compteCredit.soldeUtilise)
                            } else {
                                totalValeur += compteCredit.soldeUtilise
                            }
                        }
                        "comptes_cheques" -> {
                            // Pour les comptes chèques, ajouter le solde (positif ou négatif)
                            if (compte.solde >= 0) {
                                totalValeur += compte.solde
                            } else {
                                totalDette += kotlin.math.abs(compte.solde)
                            }
                        }
                        "comptes_investissement" -> {
                            // Pour les comptes d'investissement, ajouter le solde
                            if (compte.solde >= 0) {
                                totalValeur += compte.solde
                            } else {
                                totalDette += kotlin.math.abs(compte.solde)
                            }
                        }
                        "comptes_epargne" -> {
                            // Pour les comptes d'épargne, ajouter le solde
                            if (compte.solde >= 0) {
                                totalValeur += compte.solde
                            } else {
                                totalDette += kotlin.math.abs(compte.solde)
                            }
                        }
                        else -> {
                            // Pour tous les autres types de comptes, ajouter le solde
                            if (compte.solde >= 0) {
                                totalValeur += compte.solde
                            } else {
                                totalDette += kotlin.math.abs(compte.solde)
                            }
                        }
                    }
                }
            } else {
            }
            
            // 2. Calculer le total des prêts personnels
            val pretsPersonnels = pretPersonnelRepository.lister()
            if (pretsPersonnels.isFailure) throw IllegalStateException("Impossible de charger les prêts personnels", pretsPersonnels.exceptionOrNull())
            if (pretsPersonnels.isSuccess) {
                val prets = pretsPersonnels.getOrNull().orEmpty()
                
                prets.forEach { pret ->
                    if (pret.solde < 0) {
                        totalDette += kotlin.math.abs(pret.solde)
                    } else {
                        totalValeur += pret.solde
                    }
                }
            } else {
            }
            
        } catch (e: Exception) {
            // ST5 : un patrimoine partiellement chargé ne doit jamais être affiché comme 0 $.
            throw e
        }
        
        // 3. Calculer la valeur nette (total valeur - total dette)
        // Si tu as 1000$ en cash et 500$ de dettes, ta valeur nette = 1000$ - 500$ = 500$
        val valeurNette = totalValeur - totalDette
        
        
        return Triple(totalDette, totalValeur, valeurNette)
    }
}

// Fonction pour calculer les moyennes sur 7 jours pour chaque jour du mois
private fun calculerMoyennes7Jours(transactions: List<Transaction>, dateFin: Date): List<Pair<String, Double>> {
    val moyennes = mutableListOf<Pair<String, Double>>()
    val cal = Calendar.getInstance().apply { 
        time = dateFin 
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, -29) // Remonter de 30 jours
    }
    
    // Calculer la moyenne sur 7 jours glissante pour chaque jour
    repeat(30) { jour ->
        val dateCourante = cal.time
        
        // Calculer la moyenne sur les 7 jours précédents (incluant le jour actuel)
        val cal7Jours = Calendar.getInstance().apply { 
            time = dateCourante 
            add(Calendar.DAY_OF_YEAR, -6) // Remonter de 6 jours
        }
        val debut7Jours = cal7Jours.time
        val fin7Jours = dateCourante
        
        val transactions7Jours = transactions.filter { 
            it.date >= debut7Jours && it.date <= fin7Jours 
        }
        
        val totalDepenses = transactions7Jours.filter { 
            it.type == TypeTransaction.Depense || 
            it.type == TypeTransaction.Pret || 
            it.type == TypeTransaction.RemboursementDonne || 
            it.type == TypeTransaction.PaiementEffectue
        }.sumOf { it.montant }
        
        val totalRevenus = transactions7Jours.filter { 
            it.type == TypeTransaction.Revenu || 
            it.type == TypeTransaction.Emprunt || 
            it.type == TypeTransaction.RemboursementRecu
        }.sumOf { it.montant }
        
        val net7Jours = totalRevenus - totalDepenses
        val moyenne7Jours = net7Jours / 7.0 // Moyenne par jour sur 7 jours
        
        val label = java.text.SimpleDateFormat("dd", java.util.Locale.FRENCH).format(dateCourante)
        moyennes.add(label to moyenne7Jours)
        
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    
    return moyennes
}

