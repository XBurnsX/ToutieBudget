package com.xburnsx.toutiebudget.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.utils.DiagnosticDonnees
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Gestionnaire de base de données (section 23) : un outil de DIAGNOSTIC.
 * - Lecture seule par défaut, données de l'utilisateur connecté seulement (BD1/BD10/BDA1).
 * - Les seules écritures permises passent par les moteurs métier validés, avec aperçu (BD2/BDA4) :
 *   suppression d'une transaction (annule tous ses effets), renommer/supprimer une catégorie,
 *   renommer/supprimer un tiers inutilisé. Tout le reste est refusé et renvoie au bon écran.
 */
class GestionBaseViewModel(private val contexte: android.content.Context) : ViewModel() {

    enum class Onglet(val libelle: String) {
        COMPTES("Comptes"), TRANSACTIONS("Transactions"), ALLOCATIONS("Allocations"), CATEGORIES("Catégories"),
        ENVELOPPES("Enveloppes"), TIERS("Tiers"), PRETS("Prêts")
    }

    enum class Action { SUPPRIMER_TRANSACTION, RENOMMER_CATEGORIE, SUPPRIMER_CATEGORIE, RENOMMER_TIERS, SUPPRIMER_TIERS }

    data class Element(
        val id: String,
        val titre: String,
        val resume: String,
        val json: String,
        val actions: List<Action> = emptyList(),
        /** Où faire la modification quand l'outil la refuse (BD2/BD6/BD7/BD8). */
        val ouModifier: String? = null
    )

    data class Sante(val lignes: List<String>)

    data class Etat(
        val onglet: Onglet = Onglet.COMPTES,
        val elements: List<Element> = emptyList(),
        val recherche: String = "",
        val chargement: Boolean = true,
        val finAtteinte: Boolean = true,
        val erreur: String? = null,
        val modeDeveloppeur: Boolean = false,
        val sante: Sante? = null,
        val constats: List<DiagnosticBase.Constat>? = null,
        val analyseEnCours: Boolean = false,
        val actionEnCours: Boolean = false,
        val message: String? = null
    )

    private val _etat = MutableStateFlow(Etat())
    val etat: StateFlow<Etat> = _etat.asStateFlow()
    private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
    private var generation = 0L
    private var decalage = 0
    private val db get() = AppModule.provideBaseDeDonnees()

    companion object { const val PAGE = 100 }

    init {
        recharger()
        chargerSante()
        // BD17 : les changements faits ailleurs (autre page, autre appareil, synchro) rechargent l'onglet
        val sync = AppModule.provideRealtimeSyncService()
        viewModelScope.launch {
            merge(sync.comptesUpdated, sync.budgetUpdated, sync.organisationUpdated).collect {
                if (!_etat.value.actionEnCours) recharger(garderListe = true)
            }
        }
    }

    private fun uid(): String = PocketBaseClient.obtenirUtilisateurConnecte()?.id
        ?: throw IllegalStateException("Connexion requise.")

    fun choisirOnglet(o: Onglet) {
        if (o == _etat.value.onglet) return
        _etat.update { it.copy(onglet = o, recherche = "") }
        recharger()
    }

    fun rechercher(texte: String) = _etat.update { it.copy(recherche = texte) }
    fun activerModeDeveloppeur(actif: Boolean) = _etat.update { it.copy(modeDeveloppeur = actif) }
    fun effacerMessage() = _etat.update { it.copy(message = null) }

    fun elementsVisibles(e: Etat = _etat.value): List<Element> {
        val q = com.xburnsx.toutiebudget.ui.historique.RechercheHistorique.normaliser(e.recherche)
        if (q.isBlank()) return e.elements
        return e.elements.filter {
            com.xburnsx.toutiebudget.ui.historique.RechercheHistorique.normaliser("${it.titre} ${it.resume} ${it.id}").contains(q)
        }
    }

    /** BD16 : une erreur reste affichée ; jamais un faux « aucune donnée ». */
    fun recharger(garderListe: Boolean = false) {
        val gen = ++generation
        decalage = 0
        _etat.update { it.copy(chargement = !garderListe, erreur = null, elements = if (garderListe) it.elements else emptyList()) }
        viewModelScope.launch {
            try {
                val page = chargerPage()
                if (gen != generation) return@launch
                _etat.update { it.copy(elements = page.first, finAtteinte = page.second, chargement = false) }
            } catch (e: Exception) {
                if (gen == generation) _etat.update { it.copy(chargement = false, erreur = "Lecture impossible : ${e.message}") }
            }
        }
    }

    /** BD19 : page suivante pour les grandes tables. */
    fun chargerSuite() {
        val e = _etat.value
        if (e.chargement || e.finAtteinte) return
        val gen = generation
        viewModelScope.launch {
            try {
                val page = chargerPage()
                if (gen != generation) return@launch
                _etat.update { s -> s.copy(elements = (s.elements + page.first).distinctBy { it.id }, finAtteinte = page.second) }
            } catch (e: Exception) {
                if (gen == generation) _etat.update { it.copy(erreur = "Page suivante impossible : ${e.message}") }
            }
        }
    }

    private fun montant(v: Double?) = if (v == null) "—" else MoneyFormatter.formatAmount(v)

    private suspend fun chargerPage(): Pair<List<Element>, Boolean> {
        val u = uid()
        return when (_etat.value.onglet) {
            Onglet.COMPTES -> {
                val refus = "Solde et Prêt à placer : Comptes > appui long > Réconcilier (correction tracée). " +
                    "Dette : page de la dette. Archivage : Comptes."
                val liste = db.compteChequeDao().getComptesByUtilisateur(u).first().map {
                    Element(it.id, it.nom + (if (it.estArchive) " (archivé)" else ""),
                        "Chèque • solde ${montant(it.solde)} • Prêt à placer ${montant(it.pretAPlacerRaw ?: 0.0)}", gson.toJson(it), ouModifier = refus)
                } + db.compteCreditDao().getComptesByUtilisateur(u).first().map {
                    Element(it.id, it.nom + (if (it.estArchive) " (archivé)" else ""),
                        "Carte • utilisé ${montant(it.soldeUtilise)} • limite ${montant(it.limiteCredit)}", gson.toJson(it), ouModifier = refus)
                } + db.compteDetteDao().getComptesByUtilisateur(u).first().map {
                    Element(it.id, it.nom + (if (it.estArchive) " (archivé)" else ""),
                        "Dette • restant ${montant(it.soldeDette)} • initial ${montant(it.montantInitial)}", gson.toJson(it), ouModifier = refus)
                } + db.compteInvestissementDao().getComptesByUtilisateur(u).first().map {
                    Element(it.id, it.nom + (if (it.estArchive) " (archivé)" else ""),
                        "Placement • valeur ${montant(it.solde)}", gson.toJson(it), ouModifier = refus)
                }
                liste to true
            }
            Onglet.TRANSACTIONS -> {
                val page = db.transactionDao().pageUtilisateur(u, PAGE, decalage)
                decalage += page.size
                page.map {
                    Element(it.id, (it.tiersUtiliser?.takeIf { t -> t.isNotBlank() } ?: it.type) + " • " + montant(it.montant),
                        "${it.date.take(10)} • ${it.type}" + (if (it.estFractionnee) " • fractionnée" else ""),
                        gson.toJson(it), actions = listOf(Action.SUPPRIMER_TRANSACTION),
                        ouModifier = "Pour modifier : Comptes > historique du compte > Modifier (tous les effets sont refaits).")
                } to (page.size < PAGE)
            }
            Onglet.ALLOCATIONS -> {
                val enveloppes = db.enveloppeDao().getEnveloppesByUtilisateur(u).first().associate { it.id to it.nom }
                val page = db.allocationMensuelleDao().pageUtilisateur(u, PAGE, decalage)
                decalage += page.size
                page.map {
                    Element(it.id, "${enveloppes[it.enveloppeId] ?: "Enveloppe introuvable"} • ${it.mois.take(7)}",
                        "Solde ${montant(it.solde)} • alloué ${montant(it.alloue)} • dépensé ${montant(it.depense)}" +
                            (if (kotlin.math.abs(it.montantReporte) >= 0.005) " • reporté ${montant(it.montantReporte)}" else ""),
                        gson.toJson(it),
                        ouModifier = "Les allocations se changent par Budget (assigner), Virer argent ou Paramètres > Refaire la répartition : " +
                            "jamais d'argent inventé ni de déficit effacé.")
                } to (page.size < PAGE)
            }
            Onglet.CATEGORIES -> db.categorieDao().getCategoriesByUtilisateur(u).first().sortedBy { it.ordre }.map {
                Element(it.id, it.nom, "Ordre ${it.ordre}", gson.toJson(it),
                    actions = listOf(Action.RENOMMER_CATEGORIE, Action.SUPPRIMER_CATEGORIE))
            } to true
            Onglet.ENVELOPPES -> {
                val categories = db.categorieDao().getCategoriesByUtilisateur(u).first().associate { it.id to it.nom }
                db.enveloppeDao().getEnveloppesByUtilisateur(u).first().map {
                    Element(it.id, it.nom + (if (it.estArchive) " (archivée)" else ""),
                        "${categories[it.categorieId] ?: "Catégorie introuvable"} • objectif ${montant(it.objectifMontant)} (${it.typeObjectif})",
                        gson.toJson(it), ouModifier = "Catégories et enveloppes : renommer, déplacer, archiver ou restaurer (règles CE/CA).")
                } to true
            }
            Onglet.TIERS -> db.tiersDao().getTiersByUtilisateur(u).first().sortedBy { it.nom.lowercase() }.map {
                Element(it.id, it.nom, "Tiers", gson.toJson(it), actions = listOf(Action.RENOMMER_TIERS, Action.SUPPRIMER_TIERS))
            } to true
            Onglet.PRETS -> db.pretPersonnelDao().getPretsByUtilisateur(u).first().map {
                Element(it.id, it.nomTiers + (if (it.estArchive) " (archivé)" else ""),
                    "${it.type} • solde ${montant(it.solde)} • initial ${montant(it.montantInitial)}", gson.toJson(it),
                    ouModifier = "Les prêts changent seulement par des transactions (Prêt, Remboursement) : le capital et l'historique restent liés.")
            } to true
        }
    }

    /** BDA5 : santé de la base. */
    fun chargerSante() {
        viewModelScope.launch {
            val lignes = runCatching {
                val u = uid()
                val jobs = db.syncJobDao().getAllSyncJobs().first()
                listOf(
                    "Comptes : ${db.compteChequeDao().getComptesByUtilisateur(u).first().size} chèques, " +
                        "${db.compteCreditDao().getComptesByUtilisateur(u).first().size} cartes, " +
                        "${db.compteDetteDao().getComptesByUtilisateur(u).first().size} dettes, " +
                        "${db.compteInvestissementDao().getComptesByUtilisateur(u).first().size} placements",
                    "Transactions : ${db.transactionDao().compterUtilisateur(u)}",
                    "Allocations : ${db.allocationMensuelleDao().getAllocationsCount(u)}",
                    "Catégories : ${db.categorieDao().getCategoriesByUtilisateur(u).first().size} • " +
                        "Enveloppes : ${db.enveloppeDao().getEnveloppesByUtilisateur(u).first().size}",
                    "Tiers : ${db.tiersDao().getTiersByUtilisateur(u).first().size} • " +
                        "Prêts : ${db.pretPersonnelDao().getPretsByUtilisateur(u).first().size}",
                    "Allocations sans propriétaire (anciennes) : ${db.allocationMensuelleDao().compterSansProprietaire()}",
                    "Synchro : ${jobs.count { it.status == "PENDING" || it.status == "IN_PROGRESS" }} en attente, " +
                        "${jobs.count { it.status == "FAILED" }} en échec",
                    "Dernier échange réussi avec le serveur : ${DiagnosticDonnees.formater(DiagnosticDonnees.dernierEchange(contexte))}"
                )
            }.getOrElse { listOf("Santé indisponible : ${it.message}") }
            _etat.update { it.copy(sante = Sante(lignes)) }
        }
    }

    /** BDA2 : diagnostic de cohérence, lecture seule. */
    fun analyser() {
        if (_etat.value.analyseEnCours) return
        _etat.update { it.copy(analyseEnCours = true, constats = null) }
        viewModelScope.launch {
            val constats = runCatching { DiagnosticBase.analyser(AppModule.provideSauvegardeBudget().contenuActuel()) }
                .getOrElse { listOf(DiagnosticBase.Constat(DiagnosticBase.Gravite.ERREUR, "Analyse impossible : ${it.message}")) }
            _etat.update { it.copy(analyseEnCours = false, constats = constats) }
        }
    }

    /** BDA4 : aperçu exact de ce que l'action va faire, avant toute confirmation. */
    suspend fun apercu(action: Action, element: Element, nouveauNom: String = ""): String = when (action) {
        Action.SUPPRIMER_TRANSACTION -> {
            val t = db.transactionDao().getTransactionById(element.id)
                ?: throw IllegalStateException("Transaction introuvable.")
            "Supprimer « ${element.titre} » du ${t.date.take(10)}.\n" +
                "Ses effets sont annulés exactement par le moteur de suppression : solde du compte, enveloppes et fractions, " +
                "carte ou dette, prêt, Prêt à placer et opération liée (paiement ou virement). Irréversible."
        }
        Action.RENOMMER_CATEGORIE, Action.RENOMMER_TIERS ->
            "Nom : « ${element.titre} » → « ${nouveauNom.trim()} ».\nL'identifiant (${element.id}) et tous les liens restent les mêmes." +
                if (action == Action.RENOMMER_TIERS) "\nLes transactions déjà saisies gardent le nom écrit à leur création." else ""
        Action.SUPPRIMER_CATEGORIE ->
            "Supprimer la catégorie « ${element.titre} ». Refusé si elle contient encore des enveloppes (même archivées)."
        Action.SUPPRIMER_TIERS -> {
            val n = db.transactionDao().compterParTiers(uid(), element.titre)
            if (n > 0) "Ce tiers est utilisé par $n opération(s) : la suppression sera REFUSÉE pour garder leurs références."
            else "Supprimer le tiers « ${element.titre} » : aucune opération ne l'utilise."
        }
    }

    /** BD2/BD9/BD11/BD22 : exécute par le moteur métier, une seule fois, et dit le vrai résultat. */
    fun executer(action: Action, element: Element, nouveauNom: String = "") {
        if (_etat.value.actionEnCours || !_etat.value.modeDeveloppeur) return
        _etat.update { it.copy(actionEnCours = true) }
        viewModelScope.launch {
            val resultat = runCatching {
                when (action) {
                    Action.SUPPRIMER_TRANSACTION -> {
                        val attendue = AppModule.provideTransactionRepository().recupererTransactionParId(element.id).getOrThrow()
                        AppModule.provideSupprimerTransactionUseCase().executer(element.id, transactionAttendue = attendue).getOrThrow()
                        com.xburnsx.toutiebudget.ui.budget.BudgetEvents.onAllocationUpdated()
                        "Transaction supprimée et tous ses effets annulés."
                    }
                    Action.RENOMMER_CATEGORIE -> {
                        val nom = nouveauNom.trim()
                        require(nom.isNotBlank()) { "Le nom ne peut pas être vide." }
                        val repo = AppModule.provideCategorieRepository()
                        val attendue = repo.recupererToutesLesCategories().getOrThrow().firstOrNull { it.id == element.id }
                            ?: throw IllegalStateException("Catégorie introuvable.")
                        repo.modifierCategorieSiInchangee(attendue, attendue.copy(nom = nom)).getOrThrow()
                        "Catégorie renommée."
                    }
                    Action.SUPPRIMER_CATEGORIE -> {
                        AppModule.provideCategorieRepository().supprimerCategorie(element.id).getOrThrow()
                        "Catégorie supprimée."
                    }
                    Action.RENOMMER_TIERS -> {
                        val nom = nouveauNom.trim()
                        require(nom.isNotBlank()) { "Le nom ne peut pas être vide." }
                        val repo = AppModule.provideTiersRepository()
                        val tiers = repo.recupererTousLesTiers().getOrThrow().firstOrNull { it.id == element.id }
                            ?: throw IllegalStateException("Tiers introuvable.")
                        // BD18 : ne jamais écraser un changement fait ailleurs depuis l'ouverture
                        check(tiers.nom == element.titre) { "Ce tiers a été modifié ailleurs ; actualise avant de recommencer." }
                        repo.mettreAJourTiers(tiers.copy(nom = nom)).getOrThrow()
                        "Tiers renommé."
                    }
                    Action.SUPPRIMER_TIERS -> {
                        val n = db.transactionDao().compterParTiers(uid(), element.titre)
                        check(n == 0) { "Refusé : $n opération(s) utilisent ce tiers." }
                        AppModule.provideTiersRepository().supprimerTiers(element.id).getOrThrow()
                        "Tiers supprimé."
                    }
                }
            }
            _etat.update {
                it.copy(actionEnCours = false, message = resultat.fold({ m -> m }, { e -> "Échec, rien n'a été changé : ${e.message}" }))
            }
            recharger(garderListe = true)
            chargerSante()
        }
    }
}
