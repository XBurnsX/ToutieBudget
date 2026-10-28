package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.modeles.DetailsInvestissement
import com.xburnsx.toutiebudget.data.modeles.ModeSuiviPlacement
import com.xburnsx.toutiebudget.data.modeles.MouvementPlacement
import com.xburnsx.toutiebudget.data.modeles.PositionTitre
import com.xburnsx.toutiebudget.data.modeles.TypeMouvementPlacement
import kotlin.math.abs

/**
 * Suivi d'un compte de placement par titre (XEQT, VFV…) : achats et ventes datés, coût moyen, cours,
 * valeur, gain ou perte en $ et en %. Pur calcul : rien ici n'écrit dans la base.
 *
 * - Un achat payé avec l'argent non placé du compte le réduit d'autant (la valeur du compte ne change pas,
 *   sauf les frais) ; un titre « déjà détenu avant le suivi » s'ajoute au capital de départ.
 * - Une vente rend l'argent au compte et calcule le gain réalisé au coût moyen.
 * - Les cours sont dans la devise du titre ; les totaux sont convertis en CAD avec les taux connus.
 */
object Portefeuille {

    data class LigneTitre(
        val position: PositionTitre,
        val coutMoyen: Double,
        /** Valeur et gain dans la devise du titre ; null si le cours est inconnu. */
        val valeur: Double?,
        val gain: Double?,
        val pourcentage: Double?
    )

    data class Resume(
        val lignes: List<LigneTitre>,
        /** Argent dans le compte, pas encore placé en titres (CAD). */
        val argentNonPlace: Double,
        /** Null tant qu'un cours ou un taux de change manque (jamais un total inventé). */
        val valeurTitres: Double?,
        val valeurTotale: Double?,
        val coutTitres: Double?,
        val gain: Double?,
        val pourcentage: Double?
    )

    fun normaliser(symbole: String): String = symbole.trim().uppercase()

    private fun arrondi(v: Double) = Math.round(v * 100.0) / 100.0

    /** Argent non placé : la valeur notée si elle existe ; sinon, sans aucun titre, tout le compte est de l'argent. */
    fun argentNonPlace(d: DetailsInvestissement, soldeCompte: Double): Double =
        d.encaisse ?: if (d.positions.isEmpty()) soldeCompte else 0.0

    fun acheter(
        d: DetailsInvestissement,
        soldeCompte: Double,
        symbole: String,
        quantite: Double,
        prixUnitaire: Double,
        frais: Double,
        date: String,
        devise: String = "CAD",
        payeAvecArgentNonPlace: Boolean = true
    ): DetailsInvestissement {
        val sym = normaliser(symbole)
        require(sym.isNotBlank()) { "Symbole requis (ex. XEQT)." }
        require(quantite.isFinite() && quantite > 0.0) { "Quantité invalide." }
        require(prixUnitaire.isFinite() && prixUnitaire >= 0.0) { "Prix invalide." }
        require(frais.isFinite() && frais >= 0.0) { "Frais invalides." }
        require(MoteurCarteCredit.lireDate(date) != null) { "Date invalide (AAAA-MM-JJ)." }
        val cout = arrondi(quantite * prixUnitaire + frais)
        val argent = argentNonPlace(d, soldeCompte)
        val existante = d.positions.firstOrNull { normaliser(it.symbole) == sym }
        val deviseTitre = existante?.devise ?: devise.trim().uppercase().ifBlank { "CAD" }

        val (encaisse, valeurDepart) = if (payeAvecArgentNonPlace) {
            // Le coût doit être couvert dans la devise du compte (CAD) : un titre en devise utilise son taux
            val coutCad = if (deviseTitre == "CAD") cout else d.tauxVersCad?.get(deviseTitre)?.let { arrondi(cout * it) }
                ?: throw IllegalStateException("Taux $deviseTitre → CAD inconnu : appuie sur « Actualiser » d'abord.")
            require(coutCad <= argent + 0.005) {
                "Il reste ${com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(argent)} non placé dans ce compte. " +
                    "Ajoute un dépôt (Virer argent), ou indique que ce titre était déjà détenu avant le suivi."
            }
            arrondi(argent - coutCad) to d.valeurDepart
        } else {
            // Titre déjà détenu : il s'ajoute à la valeur ET au capital de départ, sans prendre l'argent non placé
            val coutCad = if (deviseTitre == "CAD") cout else d.tauxVersCad?.get(deviseTitre)?.let { arrondi(cout * it) }
                ?: throw IllegalStateException("Taux $deviseTitre → CAD inconnu : appuie sur « Actualiser » d'abord.")
            argent to arrondi((d.valeurDepart ?: soldeCompte) + coutCad)
        }

        // Sans cours plus récent, le prix d'achat est le dernier cours connu (en attendant « Actualiser »)
        val coursPlusRecent = existante?.cours != null && existante.dateCours != null && existante.dateCours >= date
        val position = existante?.copy(
            quantite = existante.quantite + quantite,
            coutTotal = arrondi(existante.coutTotal + cout),
            cours = if (coursPlusRecent) existante.cours else prixUnitaire,
            dateCours = if (coursPlusRecent) existante.dateCours else date
        ) ?: PositionTitre(symbole = sym, quantite = quantite, coutTotal = cout, devise = deviseTitre, cours = prixUnitaire, dateCours = date)
        val mouvement = MouvementPlacement(
            date = date, type = TypeMouvementPlacement.ACHAT, montant = cout, symbole = sym, quantite = quantite,
            note = if (payeAvecArgentNonPlace) "à ${prixUnitaire} $deviseTitre" else "Déjà détenu avant le suivi · à ${prixUnitaire} $deviseTitre"
        )
        return d.copy(
            mode = ModeSuiviPlacement.DETAILLE,
            encaisse = encaisse,
            // L'argent retirable ne peut pas dépasser l'argent qui reste après l'achat
            retirableConnu = d.retirableConnu?.let { minOf(it, encaisse) },
            valeurDepart = valeurDepart,
            dateDepart = d.dateDepart ?: date,
            positions = d.positions.filter { it.id != position.id } + position,
            mouvements = d.mouvements + mouvement
        )
    }

    fun vendre(
        d: DetailsInvestissement,
        soldeCompte: Double,
        symbole: String,
        quantite: Double,
        prixUnitaire: Double,
        frais: Double,
        date: String
    ): DetailsInvestissement {
        val sym = normaliser(symbole)
        val position = d.positions.firstOrNull { normaliser(it.symbole) == sym }
            ?: throw IllegalArgumentException("Aucune position $sym dans ce compte.")
        require(quantite.isFinite() && quantite > 0.0) { "Quantité invalide." }
        require(quantite <= position.quantite + 1e-9) { "Tu n'as que ${position.quantite} parts de $sym." }
        require(prixUnitaire.isFinite() && prixUnitaire >= 0.0 && frais.isFinite() && frais >= 0.0) { "Prix ou frais invalides." }
        require(MoteurCarteCredit.lireDate(date) != null) { "Date invalide (AAAA-MM-JJ)." }

        val coutVendu = arrondi(position.coutTotal * quantite / position.quantite)
        val produit = arrondi(quantite * prixUnitaire - frais)
        val gain = arrondi(produit - coutVendu)
        val reste = position.quantite - quantite
        val produitCad = if (position.devise == "CAD") produit else d.tauxVersCad?.get(position.devise)?.let { arrondi(produit * it) }
            ?: throw IllegalStateException("Taux ${position.devise} → CAD inconnu : appuie sur « Actualiser » d'abord.")
        val positions = d.positions.filter { it.id != position.id } +
            if (reste < 1e-9) emptyList() else listOf(position.copy(quantite = reste, coutTotal = arrondi(position.coutTotal - coutVendu)))
        return d.copy(
            encaisse = arrondi(argentNonPlace(d, soldeCompte) + produitCad),
            positions = positions,
            mouvements = d.mouvements + MouvementPlacement(
                date = date, type = TypeMouvementPlacement.VENTE, montant = produit, symbole = sym, quantite = quantite,
                gainRealise = gain, note = "à ${prixUnitaire} ${position.devise}"
            )
        )
    }

    /** Nouveau cours d'un titre (en ligne ou à la main), dans sa devise. */
    fun mettreCours(
        d: DetailsInvestissement,
        symbole: String,
        cours: Double,
        date: String,
        devise: String? = null,
        symboleCotation: String? = null
    ): DetailsInvestissement {
        require(cours.isFinite() && cours >= 0.0) { "Prix invalide." }
        val sym = normaliser(symbole)
        require(d.positions.any { normaliser(it.symbole) == sym }) { "Aucune position $sym." }
        return d.copy(positions = d.positions.map { p ->
            if (normaliser(p.symbole) != sym) p else p.copy(
                cours = cours, dateCours = date,
                devise = devise?.uppercase()?.ifBlank { null } ?: p.devise,
                symboleCotation = symboleCotation ?: p.symboleCotation
            )
        })
    }

    fun resume(d: DetailsInvestissement, soldeCompte: Double): Resume {
        val taux = d.tauxVersCad.orEmpty()
        fun versCad(devise: String): Double? = if (devise == "CAD") 1.0 else taux[devise]
        val lignes = d.positions.sortedBy { it.symbole }.map { p ->
            val valeur = p.cours?.let { arrondi(it * p.quantite) }
            val gain = valeur?.let { arrondi(it - p.coutTotal) }
            LigneTitre(
                position = p,
                coutMoyen = if (p.quantite > 0) p.coutTotal / p.quantite else 0.0,
                valeur = valeur,
                gain = gain,
                pourcentage = gain?.takeIf { p.coutTotal > 0.0 }?.let { it / p.coutTotal * 100.0 }
            )
        }
        val argent = argentNonPlace(d, soldeCompte)
        val valeurTitres = lignes.map { l -> l.valeur?.let { v -> versCad(l.position.devise)?.let { v * it } } }
            .let { if (it.any { v -> v == null }) null else arrondi(it.sumOf { v -> v!! }) }
        val coutTitres = lignes.map { l -> versCad(l.position.devise)?.let { l.position.coutTotal * it } }
            .let { if (it.any { v -> v == null }) null else arrondi(it.sumOf { v -> v!! }) }
        val gain = if (valeurTitres != null && coutTitres != null) arrondi(valeurTitres - coutTitres) else null
        return Resume(
            lignes = lignes,
            argentNonPlace = argent,
            valeurTitres = valeurTitres,
            valeurTotale = valeurTitres?.let { arrondi(it + argent) },
            coutTitres = coutTitres,
            gain = gain,
            pourcentage = gain?.takeIf { coutTitres != null && coutTitres > 0.0 }?.let { it / coutTitres!! * 100.0 }
        )
    }

    /** Valeur du compte d'après ses titres, ou null si un cours/taux manque (la valeur actuelle est alors gardée). */
    fun valeurCompte(d: DetailsInvestissement, soldeCompte: Double): Double? =
        if (d.mode != ModeSuiviPlacement.DETAILLE || d.positions.isEmpty() && d.encaisse == null) null
        else resume(d, soldeCompte).valeurTotale?.takeIf { abs(it) < 1e13 }
}
