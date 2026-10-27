package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.modeles.CompteInvestissement
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeMouvementPlacement
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import java.util.Date
import kotlin.math.abs
import kotlin.math.pow

/**
 * Calculs des placements (section 12). Aucune donnée manquante n'est fabriquée :
 * un résultat impossible à établir est null (« indisponible »), jamais un pourcentage inventé (IA10).
 * Les gains ne vont jamais dans les enveloppes ni dans le « Prêt à placer » (IA3).
 */
object MoteurInvestissement {

    private const val JOUR_MS = 86_400_000.0

    fun arrondi(v: Double) = MoteurCarteCredit.arrondi(v)

    // ===== Flux d'argent réels (IF3, IA2) =====

    data class Flux(val date: Date, val montant: Double) // + apport, - retrait

    /** Seuls les virements sont des apports/retraits : un dépôt n'est ni un revenu ni un gain. */
    fun flux(transactions: List<Transaction>): List<Flux> = transactions.mapNotNull { tx ->
        when (tx.type) {
            TypeTransaction.TransfertEntrant -> Flux(tx.date, tx.montant)
            TypeTransaction.TransfertSortant -> Flux(tx.date, -tx.montant)
            else -> null
        }
    }.sortedBy { it.date }

    fun apports(transactions: List<Transaction>) = arrondi(flux(transactions).filter { it.montant > 0 }.sumOf { it.montant })
    fun retraits(transactions: List<Transaction>) = arrondi(-flux(transactions).filter { it.montant < 0 }.sumOf { it.montant })

    /** Capital net versé = valeur de départ connue + apports - retraits ; null tant que la valeur de départ est inconnue. */
    fun capitalNetVerse(compte: CompteInvestissement, transactions: List<Transaction>): Double? {
        val depart = compte.details.valeurDepart ?: return null
        val dateDepart = MoteurCarteCredit.lireDate(compte.details.dateDepart) ?: return null
        val fluxApresDepart = flux(transactions).filter { !it.date.before(dateDepart) }
        val capital = depart + fluxApresDepart.sumOf { it.montant }
        return capital.takeIf { it.isFinite() }?.let(::arrondi)
    }

    /** IA3 : variation totale (réalisée + non réalisée) par rapport au capital net versé. */
    fun gainTotal(compte: CompteInvestissement, transactions: List<Transaction>): Double? =
        capitalNetVerse(compte, transactions)?.let { arrondi(compte.solde - it) }

    /** Pourcentage simple, seulement s'il n'y a eu aucun apport ni retrait (sinon trompeur). */
    fun pourcentageSimple(compte: CompteInvestissement, transactions: List<Transaction>): Double? {
        val depart = compte.details.valeurDepart ?: return null
        if (depart <= 0 || flux(transactions).isNotEmpty()) return null
        return (compte.solde - depart) / depart * 100
    }

    // ===== Réalisé / non réalisé (IA3, IA5, IA6, IA10) =====

    fun gainRealise(compte: CompteInvestissement): Double = arrondi(
        compte.details.mouvements.sumOf { m ->
            when (m.type) {
                TypeMouvementPlacement.VENTE -> m.gainRealise ?: 0.0
                TypeMouvementPlacement.DIVIDENDE, TypeMouvementPlacement.DISTRIBUTION, TypeMouvementPlacement.INTERETS -> m.montant
                TypeMouvementPlacement.FRAIS -> -abs(m.montant)
                else -> 0.0
            }
        }
    )

    /** Plus-value latente des positions dont le cours est connu ; null si aucun cours. */
    fun gainNonRealise(compte: CompteInvestissement): Double? {
        val positions = compte.details.positions
        // Aucune conversion de change inventée pour le coût ou le cours.
        if (positions.any { !it.devise.equals("CAD", ignoreCase = true) }) return null
        if (positions.isEmpty() || positions.any {
                it.valeur == null || it.valeur?.isFinite() != true || !it.coutTotal.isFinite()
            }) return null
        val gain = positions.sumOf { it.valeur!! - it.coutTotal }
        return gain.takeIf { it.isFinite() }?.let(::arrondi)
    }

    // ===== Valeurs datées (IA4) =====

    data class PointValeur(val date: Date, val valeur: Double)

    /** Points connus seulement (départ, valorisations) + la valeur actuelle ; les périodes sans point restent vides. */
    fun serieValeurs(compte: CompteInvestissement, aujourdHui: Date = Date()): List<PointValeur> {
        val d = compte.details
        val points = mutableListOf<PointValeur>()
        val depart = MoteurCarteCredit.lireDate(d.dateDepart)
        if (depart != null && d.valeurDepart != null) points += PointValeur(depart, d.valeurDepart)
        d.valorisations.forEach { v -> MoteurCarteCredit.lireDate(v.date)?.let { points += PointValeur(it, v.valeur) } }
        points += PointValeur(aujourdHui, compte.solde)
        return points.sortedBy { it.date }.distinctBy { MoteurCarteCredit.ecrireDate(it.date) to it.valeur }
    }

    // ===== Performance avancée (IA10) =====

    /**
     * Rendement pondéré dans le temps : produit des rendements entre deux valeurs connues,
     * en retirant les apports/retraits de chaque sous-période. Null s'il y a moins de deux valeurs.
     */
    fun rendementPondereTemps(compte: CompteInvestissement, transactions: List<Transaction>): Double? {
        val points = serieValeurs(compte)
        if (points.size < 2) return null
        val flux = flux(transactions)
        var produit = 1.0
        for (i in 1 until points.size) {
            val debut = points[i - 1]
            val fin = points[i]
            if (debut.valeur <= 0) return null
            val fluxPeriode = flux.filter { it.date.after(debut.date) && !it.date.after(fin.date) }.sumOf { it.montant }
            produit *= (fin.valeur - fluxPeriode) / debut.valeur
        }
        val r = (produit - 1) * 100
        return if (r.isFinite()) r else null
    }

    /**
     * Rendement pondéré selon les flux (TRI annualisé) : valeur de départ et apports investis,
     * retraits et valeur actuelle récupérés. Null si les données ne permettent pas un résultat fiable.
     */
    fun rendementPondereFlux(compte: CompteInvestissement, transactions: List<Transaction>, aujourdHui: Date = Date()): Double? {
        val d = compte.details
        val depart = MoteurCarteCredit.lireDate(d.dateDepart) ?: return null
        val valeurDepart = d.valeurDepart ?: return null
        if ((aujourdHui.time - depart.time) / JOUR_MS < 30) return null
        val mouvements = mutableListOf(depart to -valeurDepart)
        flux(transactions).forEach { mouvements += it.date to -it.montant }
        mouvements += aujourdHui to compte.solde
        if (mouvements.none { it.second > 0 } || mouvements.none { it.second < 0 }) return null

        fun van(taux: Double) = mouvements.sumOf { (date, montant) ->
            montant / (1 + taux).pow((date.time - depart.time) / JOUR_MS / 365.0)
        }
        var bas = -0.99
        var haut = 10.0
        if (van(bas) * van(haut) > 0) return null
        repeat(200) {
            val milieu = (bas + haut) / 2
            if (van(bas) * van(milieu) <= 0) haut = milieu else bas = milieu
        }
        val r = (bas + haut) / 2 * 100
        return if (r.isFinite()) r else null
    }

    // ===== Répartition (IA9) =====

    data class Part(val libelle: String, val valeur: Double, val pourcentage: Double)

    /** Par catégorie d'actif, encaisse comprise ; seulement les positions dont la valeur est connue. */
    fun repartitionParCategorie(compte: CompteInvestissement): List<Part> {
        val d = compte.details
        if (d.positions.any { !it.devise.equals("CAD", ignoreCase = true) }) return emptyList()
        val valeurs = d.positions.filter { it.valeur != null }.groupBy { it.categorie.libelle }
            .mapValues { (_, p) -> p.sumOf { it.valeur!! } }.toMutableMap()
        d.encaisse?.takeIf { it > 0 }?.let { valeurs["Encaisse"] = it }
        return parts(valeurs)
    }

    fun repartitionParTitre(compte: CompteInvestissement): List<Part> {
        val d = compte.details
        if (d.positions.any { !it.devise.equals("CAD", ignoreCase = true) }) return emptyList()
        val valeurs = d.positions.filter { it.valeur != null && it.valeur!!.isFinite() }
            .groupBy { it.symbole }.mapValues { (_, positions) -> positions.sumOf { it.valeur!! } }
            .toMutableMap()
        d.encaisse?.takeIf { it > 0 }?.let { valeurs["Encaisse"] = it }
        return parts(valeurs)
    }

    fun repartitionParDevise(compte: CompteInvestissement): List<Part> {
        val d = compte.details
        // IA9 : un graphique global CAD/USD sans taux FX n'est pas une
        // répartition comparable. Ne pas additionner des unités différentes.
        val devises = d.positions.filter { it.valeur != null }.map { it.devise }.toSet()
        if (devises.size > 1 || (devises.isNotEmpty() && "CAD" !in devises && d.encaisse != null)) {
            return emptyList()
        }
        val valeurs = d.positions.filter { it.valeur != null && it.valeur!!.isFinite() }
            .groupBy { it.devise }.mapValues { (_, p) -> p.sumOf { it.valeur!! } }.toMutableMap()
        d.encaisse?.takeIf { it > 0 }?.let { valeurs["CAD (encaisse)"] = (valeurs["CAD (encaisse)"] ?: 0.0) + it }
        return parts(valeurs)
    }

    private fun parts(valeurs: Map<String, Double>): List<Part> {
        val total = valeurs.values.sum()
        if (!total.isFinite() || total <= 0 || valeurs.values.any { !it.isFinite() || it < 0 }) return emptyList()
        return valeurs.map { (k, v) -> Part(k, arrondi(v), v / total * 100) }.sortedByDescending { it.valeur }
    }
}
