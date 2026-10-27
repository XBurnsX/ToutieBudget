package com.xburnsx.toutiebudget.fakes

import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Categorie
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.data.modeles.CompteInvestissement
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.domain.services.HistoriqueAllocationService
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.util.Calendar
import java.util.Date

/**
 * Base de données en mémoire qui imite les dépôts Room (mêmes règles de solde et de Prêt à placer),
 * sans téléphone ni serveur. `echouerSur` fait échouer un appel précis pour tester les retours arrière.
 */
class BanqueEnMemoire {
    val comptes = linkedMapOf<String, Compte>()
    val transactions = linkedMapOf<String, Transaction>()
    val allocations = linkedMapOf<String, AllocationMensuelle>()
    val enveloppes = linkedMapOf<String, Enveloppe>()
    val categories = linkedMapOf<String, Categorie>()
    val historique = mutableListOf<String>()

    /** Photo de toute la base, pour imiter l'annulation d'une transaction Room. */
    fun instantane(): () -> Unit {
        val c = LinkedHashMap(comptes); val t = LinkedHashMap(transactions); val a = LinkedHashMap(allocations)
        val e = LinkedHashMap(enveloppes); val k = LinkedHashMap(categories); val h = historique.toList()
        return {
            comptes.clear(); comptes.putAll(c); transactions.clear(); transactions.putAll(t)
            allocations.clear(); allocations.putAll(a); enveloppes.clear(); enveloppes.putAll(e)
            categories.clear(); categories.putAll(k); historique.clear(); historique.addAll(h)
        }
    }

    /** Comme OperationAtomiqueRoom : tout le bloc est validé ensemble, ou toute la base revient. */
    fun transaction(autres: AutresDepotsEnMemoire? = null) = object : com.xburnsx.toutiebudget.utils.OperationAtomique {
        override suspend fun <T> executer(bloc: suspend () -> T): T {
            val remettre = instantane()
            val remettreAutres = autres?.instantane()
            try {
                return bloc()
            } catch (e: Throwable) {
                remettre(); remettreAutres?.invoke()
                throw e
            }
        }
    }

    /** Nom d'une opération à faire échouer (ex. "creerTransaction#2" = le 2e appel). */
    var echouerSur: String? = null
    private val compteurs = mutableMapOf<String, Int>()
    private var prochainId = 1

    private fun id(prefixe: String) = "$prefixe${prochainId++}"

    /** Combien de fois une écriture a été appelée (ex. « creerTransaction »). */
    fun nombreAppels(operation: String) = compteurs[operation] ?: 0

    private fun verifier(operation: String) {
        val n = (compteurs[operation] ?: 0) + 1
        compteurs[operation] = n
        if (echouerSur == operation || echouerSur == "$operation#$n") throw IllegalStateException("Échec simulé : $operation")
    }

    // ===== Aides de mise en place =====

    fun cheque(id: String, solde: Double, pretAPlacer: Double, nom: String = id) =
        CompteCheque(id = id, utilisateurId = "u", nom = nom, solde = solde, pretAPlacerRaw = pretAPlacer, couleur = "#2196F3", estArchive = false, ordre = 0)
            .also { comptes[id] = it }

    fun carte(id: String, solde: Double, nom: String = id, fraisJson: com.google.gson.JsonElement? = null) =
        CompteCredit(id = id, utilisateurId = "u", nom = nom, soldeUtilise = solde, couleur = "#E91E63", estArchive = false, ordre = 0,
            limiteCredit = 7000.0, tauxInteret = 12.9, fraisMensuelsJson = fraisJson).also { comptes[id] = it }

    fun dette(id: String, solde: Double, nom: String = id, detailsJson: String? = null) =
        CompteDette(id = id, utilisateurId = "u", nom = nom, soldeDette = solde, estArchive = false, ordre = 0, montantInitial = -solde,
            detailsDetteJson = detailsJson).also { comptes[id] = it }

    fun placement(id: String, solde: Double) =
        CompteInvestissement(id = id, utilisateurId = "u", nom = id, solde = solde, couleur = "#4CAF50", estArchive = false, ordre = 0)
            .also { comptes[id] = it }

    fun categorie(id: String, nom: String) = Categorie(id = id, utilisateurId = "u", nom = nom).also { categories[id] = it }

    fun enveloppe(id: String, nom: String = id, categorieId: String = "cat") =
        Enveloppe(id = id, utilisateurId = "u", nom = nom, categorieId = categorieId, estArchive = false, ordre = 0).also { enveloppes[id] = it }

    fun allocation(enveloppeId: String, mois: Date, solde: Double, alloue: Double = solde, depense: Double = 0.0, provenance: String? = null) =
        AllocationMensuelle(id = id("a"), utilisateurId = "u", enveloppeId = enveloppeId, mois = premierJour(mois), solde = solde,
            alloue = alloue, depense = depense, compteSourceId = provenance, collectionCompteSource = provenance?.let { "comptes_cheques" })
            .also { allocations[it.id] = it }

    fun cheque(id: String) = comptes[id] as CompteCheque
    fun soldeDe(id: String) = comptes.getValue(id).solde
    fun allocationDe(enveloppeId: String, mois: Date) = allocations.values.filter { it.enveloppeId == enveloppeId && memeMois(it.mois, mois) }

    companion object {
        fun premierJour(d: Date): Date = Calendar.getInstance().apply {
            time = d; set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time

        fun memeMois(a: Date, b: Date): Boolean {
            val ca = Calendar.getInstance().apply { time = a }
            val cb = Calendar.getInstance().apply { time = b }
            return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.MONTH) == cb.get(Calendar.MONTH)
        }

        fun date(annee: Int, mois: Int, jour: Int): Date = Calendar.getInstance().apply { set(annee, mois - 1, jour, 12, 0, 0) }.time
    }

    // ===== Dépôts =====

    val depotComptes = object : CompteRepository {
        override suspend fun recupererTousLesComptes() = Result.success(comptes.values.toList())
        override suspend fun creerCompte(compte: Compte) = runCatching { verifier("creerCompte"); comptes[compte.id] = compte }
        override suspend fun mettreAJourCompte(compte: Compte) = runCatching { verifier("mettreAJourCompte"); comptes[compte.id] = compte }
        override suspend fun supprimerCompte(compteId: String, collection: String) = runCatching { comptes.remove(compteId); Unit }
        override suspend fun getCompteById(compteId: String, collection: String) = comptes[compteId]
        override suspend fun mettreAJourSolde(compteId: String, collection: String, nouveauSolde: Double) {
            verifier("mettreAJourSolde")
            comptes[compteId] = avecSolde(comptes.getValue(compteId), nouveauSolde)
        }
        override suspend fun mettreAJourSoldeAvecVariation(compteId: String, collectionCompte: String, variationSolde: Double) =
            mettreAJourSoldeAvecVariationEtPretAPlacer(compteId, collectionCompte, variationSolde, false)
        override suspend fun mettreAJourSoldeAvecVariationEtPretAPlacer(
            compteId: String, collectionCompte: String, variationSolde: Double, mettreAJourPretAPlacer: Boolean
        ) = runCatching {
            verifier("mettreAJourSoldeAvecVariation")
            val c = comptes[compteId] ?: throw Exception("Compte non trouvé")
            comptes[compteId] = if (c is CompteCheque) {
                c.copy(
                    solde = MoneyFormatter.roundAmount(c.solde + variationSolde),
                    pretAPlacerRaw = if (mettreAJourPretAPlacer) MoneyFormatter.roundAmount(c.pretAPlacer + variationSolde) else c.pretAPlacerRaw
                )
            } else avecSolde(c, MoneyFormatter.roundAmount(c.solde + variationSolde))
        }
        override suspend fun mettreAJourPretAPlacerSeulement(compteId: String, variationPretAPlacer: Double) = runCatching {
            verifier("mettreAJourPretAPlacer")
            val c = comptes[compteId] as CompteCheque
            comptes[compteId] = c.copy(pretAPlacerRaw = MoneyFormatter.roundAmount(c.pretAPlacer + variationPretAPlacer))
        }
        override suspend fun recupererCompteParId(compteId: String, collectionCompte: String) =
            comptes[compteId]?.let { Result.success(it) } ?: Result.failure(Exception("Compte non trouvé"))
        override suspend fun recupererCompteParIdToutesCollections(compteId: String) = recupererCompteParId(compteId, "")
    }

    private fun avecSolde(c: Compte, s: Double): Compte = when (c) {
        is CompteCheque -> c.copy(solde = s)
        is CompteCredit -> c.copy(soldeUtilise = s)
        is CompteDette -> c.copy(soldeDette = s)
        is CompteInvestissement -> c.copy(solde = s)
    }

    val depotTransactions = object : TransactionRepository {
        override suspend fun creerTransaction(transaction: Transaction) = runCatching {
            verifier("creerTransaction")
            val t = if (transaction.id.isBlank()) transaction.copy(id = id("t")) else transaction
            transactions[t.id] = t; t
        }
        override suspend fun recupererToutesLesTransactions() = Result.success(transactions.values.toList())
        override suspend fun recupererTransactionsParPeriode(debut: Date, fin: Date) =
            Result.success(transactions.values.filter { !it.date.before(debut) && !it.date.after(fin) })
        override suspend fun recupererTransactionsParPeriode(debut: Date, fin: Date, limit: Int, offset: Int) = recupererTransactionsParPeriode(debut, fin)
        override suspend fun recupererTransactionsPourCompte(compteId: String, collectionCompte: String) =
            Result.success(transactions.values.filter { it.compteId == compteId })
        override suspend fun recupererTransactionsParAllocation(allocationId: String) =
            Result.success(transactions.values.filter { it.allocationMensuelleId == allocationId })
        override suspend fun recupererTransactionParId(transactionId: String) =
            transactions[transactionId]?.let { Result.success(it) } ?: Result.failure(Exception("Transaction non trouvée"))
        override suspend fun mettreAJourTransaction(transaction: Transaction) = runCatching { verifier("mettreAJourTransaction"); transactions[transaction.id] = transaction }
        override suspend fun supprimerTransaction(transactionId: String) = runCatching { verifier("supprimerTransaction"); transactions.remove(transactionId); Unit }
    }

    val depotAllocations = object : AllocationMensuelleRepository {
        override suspend fun getAllocationById(id: String) = allocations[id]
        override suspend fun mettreAJourAllocation(id: String, nouveauSolde: Double, nouvelleDepense: Double) {
            allocations[id] = allocations.getValue(id).copy(solde = nouveauSolde, depense = nouvelleDepense)
        }
        /** Comme Room : les allocations du même mois sont fusionnées en une seule. */
        override suspend fun recupererOuCreerAllocation(enveloppeId: String, mois: Date): AllocationMensuelle {
            val existantes = allocationDe(enveloppeId, mois)
            if (existantes.isEmpty()) return allocation(enveloppeId, mois, solde = 0.0, alloue = 0.0)
            if (existantes.size == 1) return existantes.first()
            existantes.drop(1).forEach { allocations.remove(it.id) }
            val f = existantes.first().copy(
                solde = existantes.sumOf { it.solde }, alloue = existantes.sumOf { it.alloue }, depense = existantes.sumOf { it.depense },
                compteSourceId = existantes.lastOrNull { it.compteSourceId != null }?.compteSourceId
            )
            allocations[f.id] = f
            return f
        }
        override suspend fun mettreAJourAllocation(allocation: AllocationMensuelle) {
            verifier("mettreAJourAllocation")
            allocations[allocation.id] = allocation
        }
        override suspend fun mettreAJourCompteSource(id: String, compteSourceId: String, collectionCompteSource: String) {
            allocations[id] = allocations.getValue(id).copy(compteSourceId = compteSourceId, collectionCompteSource = collectionCompteSource)
        }
        override suspend fun creerNouvelleAllocation(allocation: AllocationMensuelle) =
            run { verifier("creerNouvelleAllocation"); allocation.copy(id = allocation.id.ifBlank { id("a") }).also { allocations[it.id] = it } }
        override suspend fun recupererAllocationsEnveloppe(enveloppeId: String) = allocations.values.filter { it.enveloppeId == enveloppeId }
    }

    val depotEnveloppes = object : EnveloppeRepository {
        override suspend fun recupererToutesLesEnveloppes() = Result.success(enveloppes.values.toList())
        override suspend fun recupererAllocationsPourMois(mois: Date) = runCatching { verifier("recupererAllocationsPourMois"); allocations.values.filter { memeMois(it.mois, mois) } }
        override suspend fun mettreAJourAllocation(allocation: AllocationMensuelle) = runCatching { allocations[allocation.id] = allocation }
        override suspend fun creerEnveloppe(enveloppe: Enveloppe) = runCatching { verifier("creerEnveloppe"); enveloppes[enveloppe.id] = enveloppe; enveloppe }
        override suspend fun mettreAJourEnveloppe(enveloppe: Enveloppe) = runCatching { enveloppes[enveloppe.id] = enveloppe }
        override suspend fun supprimerEnveloppe(id: String) = runCatching { verifier("supprimerEnveloppe"); enveloppes.remove(id); Unit }
        override suspend fun ajouterDepenseAllocation(allocationMensuelleId: String, montantDepense: Double) = runCatching {
            val a = allocations.getValue(allocationMensuelleId)
            allocations[a.id] = a.copy(depense = a.depense + montantDepense, solde = a.solde - montantDepense)
        }
        override suspend fun annulerDepenseAllocation(allocationMensuelleId: String, montantDepense: Double) = runCatching {
            val a = allocations.getValue(allocationMensuelleId)
            allocations[a.id] = a.copy(depense = a.depense - montantDepense, solde = a.solde + montantDepense)
        }
        override suspend fun recupererAllocationMensuelle(enveloppeId: String, mois: Date) =
            Result.success(allocationDe(enveloppeId, mois).firstOrNull())
        override suspend fun creerAllocationMensuelle(allocation: AllocationMensuelle) =
            Result.success(depotAllocations.creerNouvelleAllocation(allocation))
        override suspend fun recupererAllocationsEnveloppe(enveloppeId: String) =
            Result.success(allocations.values.filter { it.enveloppeId == enveloppeId })
        override suspend fun recupererAllocationParId(id: String) =
            allocations[id]?.let { Result.success(it) } ?: Result.failure(Exception("Allocation introuvable"))
    }

    val depotCategories = object : CategorieRepository {
        override suspend fun recupererToutesLesCategories() = Result.success(categories.values.toList())
        override suspend fun creerCategorie(categorie: Categorie) = runCatching { verifier("creerCategorie"); categories[categorie.id] = categorie; categorie }
        override suspend fun mettreAJourCategorie(categorie: Categorie) = runCatching { categories[categorie.id] = categorie; categorie }
        override suspend fun supprimerCategorie(id: String) = runCatching { categories.remove(id); Unit }
    }

    val historiqueService = object : HistoriqueAllocationService {
        override suspend fun enregistrerCreationAllocation(allocation: AllocationMensuelle, compte: CompteCheque, enveloppe: Enveloppe, montant: Double, soldeAvant: Double, soldeApres: Double, pretAPlacerAvant: Double, pretAPlacerApres: Double) { historique += "CREATION" }
        override suspend fun enregistrerModificationAllocation(allocationAvant: AllocationMensuelle, allocationApres: AllocationMensuelle, compte: CompteCheque, enveloppe: Enveloppe, montantModification: Double, soldeAvant: Double, soldeApres: Double, pretAPlacerAvant: Double, pretAPlacerApres: Double) { historique += "MODIFICATION" }
        override suspend fun enregistrerSuppressionAllocation(allocation: AllocationMensuelle, compte: CompteCheque, enveloppe: Enveloppe, soldeAvant: Double, soldeApres: Double, pretAPlacerAvant: Double, pretAPlacerApres: Double) { historique += "SUPPRESSION" }
        override suspend fun enregistrerTransactionDirecte(compte: CompteCheque, enveloppe: Enveloppe?, typeTransaction: String, montant: Double, soldeAvant: Double, soldeApres: Double, pretAPlacerAvant: Double, pretAPlacerApres: Double, note: String?) {
            verifier("enregistrerTransactionDirecte")
            historique += typeTransaction
        }
        override suspend fun enregistrerAjustementCompte(compte: Compte, typeAction: String, description: String, montant: Double, soldeAvant: Double, soldeApres: Double, pretAPlacerAvant: Double, pretAPlacerApres: Double) { historique += typeAction }
    }
}

/** Tiers, prêts personnels et préférences en mémoire (pour les tests des écrans). */
class AutresDepotsEnMemoire {
    val tiers = linkedMapOf<String, com.xburnsx.toutiebudget.data.modeles.Tiers>()
    val prets = linkedMapOf<String, com.xburnsx.toutiebudget.data.modeles.PretPersonnel>()
    private var n = 1
    /** Date du dernier rollover (null = jamais fait). */
    var dernierRollover: java.util.Date? = java.util.Date()

    fun instantane(): () -> Unit {
        val t = LinkedHashMap(tiers); val p = LinkedHashMap(prets)
        return { tiers.clear(); tiers.putAll(t); prets.clear(); prets.putAll(p) }
    }

    val depotTiers = object : com.xburnsx.toutiebudget.data.repositories.TiersRepository {
        override suspend fun recupererTousLesTiers() = Result.success(tiers.values.toList())
        override suspend fun creerTiers(tiers: com.xburnsx.toutiebudget.data.modeles.Tiers) = runCatching {
            val t = tiers.copy(id = tiers.id.ifBlank { "tiers${n++}" }); this@AutresDepotsEnMemoire.tiers[t.id] = t; t
        }
        override suspend fun rechercherTiersParNom(recherche: String) =
            Result.success(tiers.values.filter { it.nom.contains(recherche, ignoreCase = true) })
        override suspend fun mettreAJourTiers(tiers: com.xburnsx.toutiebudget.data.modeles.Tiers) = runCatching { this@AutresDepotsEnMemoire.tiers[tiers.id] = tiers }
        override suspend fun supprimerTiers(tiersId: String) = runCatching { tiers.remove(tiersId); Unit }
    }

    val depotPrets = object : com.xburnsx.toutiebudget.data.repositories.PretPersonnelRepository {
        override suspend fun lister() = Result.success(prets.values.toList())
        override suspend fun creer(pret: com.xburnsx.toutiebudget.data.modeles.PretPersonnel) = runCatching {
            val p = pret.copy(id = pret.id.ifBlank { "pret${n++}" }, created = pret.created ?: "2026-01-${(n).toString().padStart(2, '0')}")
            prets[p.id] = p; p
        }
        override suspend fun mettreAJour(pret: com.xburnsx.toutiebudget.data.modeles.PretPersonnel) = runCatching { prets[pret.id] = pret }
        override suspend fun supprimer(id: String) = runCatching { prets.remove(id); Unit }
    }

    val preferences = object : com.xburnsx.toutiebudget.data.repositories.PreferenceRepository {
        private var categories = emptyMap<String, Boolean>()
        private var favorites = emptySet<String>()
        private var enAttente = false
        private var rollover: java.util.Date?
            get() = dernierRollover
            set(v) { dernierRollover = v }
        override suspend fun sauvegarderDernierRollover(date: java.util.Date) { rollover = date }
        override suspend fun recupererDernierRollover() = rollover
        override suspend fun setFigerPretAPlacer(enabled: Boolean) {}
        override suspend fun getFigerPretAPlacer() = false
        override suspend fun sauvegarderCategoriesOuvertes(categoriesOuvertes: Map<String, Boolean>) { categories = categoriesOuvertes; enAttente = true }
        override suspend fun recupererCategoriesOuvertes() = categories
        override suspend fun sauvegarderEnveloppesFavorites(ids: Set<String>) { favorites = ids; enAttente = true }
        override suspend fun recupererEnveloppesFavorites() = favorites
        override suspend fun preferencesBudgetEnAttenteEnvoi() = enAttente
        override suspend fun marquerPreferencesBudgetEnvoyees() { enAttente = false }
        override suspend fun appliquerPreferencesBudgetServeur(categoriesOuvertes: Map<String, Boolean>?, favorites: Set<String>?) {}
    }
}
