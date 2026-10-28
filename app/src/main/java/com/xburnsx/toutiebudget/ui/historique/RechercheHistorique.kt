package com.xburnsx.toutiebudget.ui.historique

import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToLong

/**
 * HC17 : recherche d'historique accent-insensible et monétaire, sans toucher
 * aux données comptables. Cherche dans les champs réellement affichés.
 */
object RechercheHistorique {
    fun normaliser(texte: String): String =
        Normalizer.normalize(texte, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT).trim()
            .replace(Regex("\\s+"), " ")

    private fun montantRechercheCentimes(requete: String): Long? {
        val nettoye = requete.trim().replace("\u00A0", "")
            .replace(" ", "").replace("$", "").replace(",", ".")
        if (!nettoye.matches(Regex("[+-]?\\d+(\\.\\d{1,2})?"))) return null
        return runCatching {
            BigDecimal(nettoye).movePointRight(2).longValueExact()
        }.getOrNull()
    }

    fun correspond(transaction: TransactionUi, requete: String): Boolean {
        val cle = normaliser(requete)
        if (cle.isEmpty()) return true
        val contenus = buildList {
            add(transaction.tiersUtiliser)
            add(transaction.note.orEmpty())
            add(transaction.nomEnveloppe.orEmpty())
            add(transaction.type.name)
            add(transaction.type.libelle)
            addAll(transaction.nomsEnveloppesFractions)
        }
        if (contenus.any { normaliser(it).contains(cle) }) return true
        val cents = montantRechercheCentimes(requete)
        return cents != null && transaction.montant.isFinite() &&
            (transaction.montant * 100.0).roundToLong() == cents
    }

    fun filtrer(transactions: List<TransactionUi>, requete: String): List<TransactionUi> =
        if (requete.isBlank()) transactions else transactions.filter { correspond(it, requete) }
}
