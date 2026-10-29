package com.xburnsx.toutiebudget.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.room.entities.HistoriqueAllocation
import com.xburnsx.toutiebudget.data.room.entities.SyncJob
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.ui.historique.StatutSyncHistorique
import com.xburnsx.toutiebudget.ui.settings.JournalHistorique.Nature
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Historique général (section 22) en deux vues distinctes :
 * - « Transactions » : les vraies opérations financières (table des transactions) ;
 * - « Mouvements du budget » : le journal des allocations, du Prêt à placer et des corrections.
 * Lecture seule : aucun solde n'est recalculé ni modifié ici (HG2).
 */
class HistoriqueGeneralViewModel(private val contexte: android.content.Context) : ViewModel() {

    enum class Vue(val libelle: String) { TRANSACTIONS("Transactions"), BUDGET("Mouvements du budget") }
    enum class Periode(val libelle: String, val mois: Long?) {
        MOIS("Ce mois", 0), TROIS_MOIS("3 derniers mois", 2), ANNEE("12 derniers mois", 11), TOUT("Tout", null)
    }

    data class Etat(
        val vue: Vue = Vue.TRANSACTIONS,
        val comptes: List<Compte> = emptyList(),
        val compteId: String? = null,
        val periode: Periode = Periode.TROIS_MOIS,
        val nature: Nature? = null,
        val recherche: String = "",
        val transactions: List<Transaction> = emptyList(),
        val journal: List<HistoriqueAllocation> = emptyList(),
        val enveloppes: Map<String, Enveloppe> = emptyMap(),
        val jobs: List<SyncJob> = emptyList(),
        val chargement: Boolean = true,
        val chargementSuite: Boolean = false,
        val finAtteinte: Boolean = false,
        val erreur: String? = null
    ) {
        fun nomCompte(id: String): String = comptes.firstOrNull { it.id == id }?.let {
            it.nom + if (it.estArchive) " (archivé)" else ""
        } ?: "Compte introuvable"
    }

    /** HG18 : détail consultable avec l'état réel de chaque référence d'origine. */
    data class Detail(val titre: String, val lignes: List<String>)

    private val _etat = MutableStateFlow(Etat())
    val etat: StateFlow<Etat> = _etat.asStateFlow()
    private var generation = 0L
    private var decalage = 0

    companion object { const val PAGE = 100 }

    init { recharger() }

    fun choisirVue(v: Vue) { if (v != _etat.value.vue) { _etat.update { it.copy(vue = v, nature = null) }; recharger() } }
    fun choisirCompte(id: String?) { _etat.update { it.copy(compteId = id) }; recharger() }
    fun choisirPeriode(p: Periode) { _etat.update { it.copy(periode = p) }; recharger() }
    fun choisirNature(n: Nature?) { _etat.update { it.copy(nature = n) } }
    fun rechercher(texte: String) { _etat.update { it.copy(recherche = texte) } }

    private fun bornes(p: Periode): Pair<Date, Date> {
        val zone = ZoneId.systemDefault()
        val fin = Date.from(LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant())
        val debut = p.mois?.let { Date.from(LocalDate.now(zone).withDayOfMonth(1).minusMonths(it).atStartOfDay(zone).toInstant()) }
            ?: Date(0)
        return debut to fin
    }

    /** HG15/HG16 : chaque rechargement efface l'ancien résultat et l'ancienne erreur ; un ancien chargement est ignoré. */
    fun recharger() {
        val gen = ++generation
        decalage = 0
        _etat.update { it.copy(chargement = true, erreur = null, transactions = emptyList(), journal = emptyList(), finAtteinte = false) }
        viewModelScope.launch {
            try {
                val comptes = AppModule.provideCompteRepository().recupererTousLesComptes().getOrThrow()
                    .sortedWith(compareBy<Compte> { it.estArchive }.thenBy { it.nom.lowercase() })
                val enveloppes = AppModule.provideEnveloppeRepository().recupererToutesLesEnveloppes().getOrThrow().associateBy { it.id }
                val jobs = runCatching { AppModule.provideSyncJobDao(contexte).getAllSyncJobs().first() }.getOrDefault(emptyList())
                if (gen != generation) return@launch
                _etat.update { it.copy(comptes = comptes, enveloppes = enveloppes, jobs = jobs) }
                chargerPage(gen)
            } catch (e: Exception) {
                if (gen == generation) _etat.update { it.copy(chargement = false, erreur = "Chargement impossible : ${e.message}") }
            }
        }
    }

    /** HG17 : page suivante, sans tout charger d'un coup. */
    fun chargerSuite() {
        val e = _etat.value
        if (e.chargement || e.chargementSuite || e.finAtteinte) return
        _etat.update { it.copy(chargementSuite = true) }
        val gen = generation
        viewModelScope.launch {
            try { chargerPage(gen) } catch (ex: Exception) {
                if (gen == generation) _etat.update { it.copy(chargementSuite = false, erreur = "Page suivante impossible : ${ex.message}") }
            }
        }
    }

    private suspend fun chargerPage(gen: Long) {
        val e = _etat.value
        when (e.vue) {
            Vue.TRANSACTIONS -> {
                val (debut, fin) = bornes(e.periode)
                val page = AppModule.provideTransactionRepository().recupererTransactionsParPeriode(debut, fin, PAGE, decalage).getOrThrow()
                if (gen != generation) return
                decalage += page.size
                _etat.update { s ->
                    s.copy(
                        transactions = (s.transactions + page.filter { s.compteId == null || it.compteId == s.compteId })
                            .distinctBy { it.id },
                        chargement = false, chargementSuite = false, finAtteinte = page.size < PAGE
                    )
                }
            }
            Vue.BUDGET -> {
                val page = AppModule.provideHistoriqueAllocationRepository().page(e.compteId, PAGE, decalage)
                if (gen != generation) return
                decalage += page.size
                val (debut, _) = bornes(e.periode)
                val dansPeriode = page.filter { h ->
                    val instant = JournalHistorique.instantJournal(h.dateAction)
                    instant == null || !instant.isBefore(debut.toInstant())
                }
                // Le journal est trié du plus récent au plus ancien : une entrée avant la période arrête la lecture
                val horsPeriode = dansPeriode.size < page.size
                _etat.update { s ->
                    s.copy(
                        journal = JournalHistorique.sansDoublons(s.journal + dansPeriode),
                        chargement = false, chargementSuite = false, finAtteinte = page.size < PAGE || horsPeriode
                    )
                }
            }
        }
    }

    /** Lignes visibles après la nature et la recherche (HGA2/HGA3). */
    fun transactionsVisibles(e: Etat = _etat.value): List<Transaction> = e.transactions
        .filter { e.nature == null || JournalHistorique.nature(it) == e.nature }
        .filter { JournalHistorique.correspond(it, e.recherche, e.nomCompte(it.compteId), nomsEnveloppes(it, e)) }
        .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id })

    fun journalVisible(e: Etat = _etat.value): List<HistoriqueAllocation> = e.journal
        .filter { e.nature == null || JournalHistorique.nature(it) == e.nature }
        .filter { JournalHistorique.correspond(it, e.recherche, e.nomCompte(it.compteId)) }
        .sortedWith(compareByDescending<HistoriqueAllocation> { JournalHistorique.instantJournal(it.dateAction) }.thenByDescending { it.id })

    fun nomsEnveloppes(t: Transaction, e: Etat = _etat.value): String {
        val ids = mutableListOf<String>()
        t.sousItems?.let { json ->
            runCatching {
                com.google.gson.JsonParser.parseString(json).asJsonArray.forEach { el ->
                    val o = el.asJsonObject
                    (o.get("enveloppeId") ?: o.get("enveloppe_id"))?.asString?.let(ids::add)
                }
            }
        }
        return ids.mapNotNull { e.enveloppes[it]?.nom }.joinToString()
    }

    /** HGA6 : provenance et état réel de synchronisation, sans rien supposer. */
    fun provenance(t: Transaction, e: Etat = _etat.value): String {
        val uid = PocketBaseClient.obtenirUtilisateurConnecte()?.id.orEmpty()
        val sync = StatutSyncHistorique.libelle(t.id, uid, e.jobs)
        return "Saisie dans l'app • $sync"
    }

    fun provenance(h: HistoriqueAllocation, e: Etat = _etat.value): String {
        val jobs = e.jobs.filter { it.type == "HISTORIQUE_ALLOCATION" && it.recordId == h.id }
        val sync = when {
            jobs.any { it.status == "FAILED" } -> "échec d'envoi"
            jobs.any { it.status == "PENDING" || it.status == "IN_PROGRESS" } -> "en attente d'envoi"
            jobs.any { it.status == "COMPLETED" } -> "envoyé au serveur"
            else -> "enregistré sur cet appareil"
        }
        return "Journalisé par l'app • $sync"
    }

    suspend fun detail(t: Transaction): Detail {
        val e = _etat.value
        val lignes = mutableListOf(
            "Nature : ${JournalHistorique.nature(t).libelle} (${t.type.libelle})",
            "Montant : ${MoneyFormatter.formatAmount(JournalHistorique.montantSigne(t))}",
            "Date : ${java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.CANADA_FRENCH).format(t.date)}",
            "Compte : ${e.nomCompte(t.compteId)}"
        )
        t.tiersUtiliser?.takeIf { it.isNotBlank() }?.let { lignes += "Tiers : $it" }
        nomsEnveloppes(t, e).takeIf { it.isNotBlank() }?.let { lignes += "Enveloppes : $it" }
        t.note?.takeIf { it.isNotBlank() }?.let { lignes += "Note : $it" }
        lignes += "Provenance : ${provenance(t, e)}"
        lignes += "Identifiant : ${t.id}"
        return Detail(t.tiersUtiliser?.takeIf { it.isNotBlank() } ?: t.type.libelle, lignes)
    }

    suspend fun detail(h: HistoriqueAllocation): Detail {
        val e = _etat.value
        val lignes = mutableListOf(
            "Nature : ${JournalHistorique.nature(h).libelle}",
            "Montant : ${MoneyFormatter.formatAmount(h.montant)}",
            "Date : ${JournalHistorique.heureLocale(JournalHistorique.instantJournal(h.dateAction))}",
            "Compte : ${e.nomCompte(h.compteId)}",
            "Solde du compte : ${MoneyFormatter.formatAmount(h.soldeAvant)} → ${MoneyFormatter.formatAmount(h.soldeApres)}"
        )
        if (JournalHistorique.aPretAPlacer(h.collectionCompte)) {
            lignes += "Prêt à placer : ${MoneyFormatter.formatAmount(h.pretAPlacerAvant)} → ${MoneyFormatter.formatAmount(h.pretAPlacerApres)}"
        }
        JournalHistorique.soldeEnveloppe(h.details)?.let { (avant, apres) ->
            lignes += "Enveloppe : ${MoneyFormatter.formatAmount(avant)} → ${MoneyFormatter.formatAmount(apres)}"
        }
        // Références d'origine et leur état actuel
        if (h.enveloppeId.isNotBlank()) {
            val env = e.enveloppes[h.enveloppeId]
            lignes += "Enveloppe d'origine : " + when {
                env == null -> "${h.enveloppeNom} — supprimée ou introuvable (ID ${h.enveloppeId})"
                env.estArchive -> "${env.nom} — archivée"
                else -> env.nom
            }
        }
        h.transactionId?.let { id ->
            val existe = AppModule.provideTransactionRepository().recupererTransactionParId(id).isSuccess
            lignes += "Transaction d'origine : " + if (existe) "existe (ID $id)" else "supprimée ou introuvable (ID $id)"
        }
        h.virementId?.let { lignes += "Virement d'origine : ID $it (toutes ses écritures partagent cet ID)" }
        if (h.allocationId.isNotBlank()) {
            val existe = runCatching { AppModule.provideAllocationMensuelleRepository().getAllocationById(h.allocationId) }.getOrNull() != null
            lignes += "Allocation : " + if (existe) "existe (ID ${h.allocationId})" else "supprimée ou introuvable (ID ${h.allocationId})"
        }
        lignes += "Provenance : ${provenance(h, e)}"
        return Detail(h.description, lignes)
    }

    /** HGA5 : CSV de TOUT ce qui correspond aux filtres (toutes les pages), sans rien modifier. */
    suspend fun csvFiltre(): String {
        val e = _etat.value
        return when (e.vue) {
            Vue.TRANSACTIONS -> {
                val (debut, fin) = bornes(e.periode)
                val tout = mutableListOf<Transaction>()
                var d = 0
                while (true) {
                    val page = AppModule.provideTransactionRepository().recupererTransactionsParPeriode(debut, fin, 500, d).getOrThrow()
                    tout += page; d += page.size
                    if (page.size < 500) break
                }
                val lignes = transactionsVisibles(e.copy(transactions = tout.filter { e.compteId == null || it.compteId == e.compteId }.distinctBy { it.id }))
                JournalHistorique.csvTransactions(lignes) { e.nomCompte(it.compteId) }
            }
            Vue.BUDGET -> {
                val (debut, _) = bornes(e.periode)
                val tout = mutableListOf<HistoriqueAllocation>()
                var d = 0
                while (true) {
                    val page = AppModule.provideHistoriqueAllocationRepository().page(e.compteId, 500, d)
                    tout += page; d += page.size
                    if (page.size < 500) break
                }
                val dansPeriode = tout.filter { h ->
                    JournalHistorique.instantJournal(h.dateAction)?.let { !it.isBefore(debut.toInstant()) } ?: true
                }
                JournalHistorique.csvJournal(journalVisible(e.copy(journal = JournalHistorique.sansDoublons(dansPeriode)))) {
                    e.nomCompte(it.compteId)
                }
            }
        }
    }
}
