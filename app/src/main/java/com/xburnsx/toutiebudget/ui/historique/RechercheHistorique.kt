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
    /**
     * HC17 : la ponctuation d'un nom de commerce ne doit pas casser sa
     * recherche (IRV-0421, IRV 0421, Irving #0421).
     * L'alias IRV → IRVING est explicite et ne touche pas aux montants.
     */
    fun normaliser(texte: String): String =
        Normalizer.normalize(texte, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT).trim()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\birv\\b"), "irving")

    private fun montantRechercheCentimes(requete: String): Long? {
        // Espaces habituels, insécables et insécables étroits des devises FR-CA.
        val nettoye = requete.trim().replace(Regex("[\\s\\p{Z}]+"), "")
            .replace("$", "").replace(",", ".")
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
            if (transaction.estFractionnee) {
                // HE11 : une note de portion existe même si le tiers et
                // la note générale ne la mentionnent pas. Ne jamais masquer
                // la ligne si une métadonnée optionnelle est corrompue.
                val nombre = runCatching {
                    PortionsHistorique.lire(transaction.sousItems).size
                }.getOrDefault(0)
                MetadonneesFractionsHistorique.lire(transaction.sousItems, nombre)
                    .forEach { infos ->
                        add(infos.description)
                        add(infos.note)
                    }
            }
        }
        // Requête entière d'abord (« Tim Hortons », « IRV-0421 », « 45,10 $ »)
        if (correspondTexte(transaction, requete, contenus)) return true
        // HA6 : sinon chaque terme doit correspondre (texte, montant ou date)
        val termes = requete.trim().split(Regex("[\\s\\p{Z}]+")).filter { it.isNotBlank() }
        val critereSpecial = termes.any {
            critereMontant(it, 0.0) != null || critereDate(it, java.util.Date()) != null
        }
        if (termes.size < 2 && !critereSpecial) return false
        return termes.all { terme ->
            critereMontant(terme, transaction.montant)
                ?: critereDate(terme, transaction.date)
                ?: correspondTexte(transaction, terme, contenus)
        }
    }

    private fun correspondTexte(transaction: TransactionUi, requete: String, contenus: List<String>): Boolean {
        val cle = normaliser(requete)
        if (cle.isEmpty()) return true
        if (contenus.any { normaliser(it).contains(cle) }) return true
        val cents = montantRechercheCentimes(requete)
        return cents != null && transaction.montant.isFinite() &&
            (transaction.montant * 100.0).roundToLong() == cents
    }

    /**
     * HA6 : montant sans signe — « >50 », « <100 », « 20-80 ».
     * null si le terme n'est pas un critère de montant.
     */
    private fun critereMontant(terme: String, montant: Double): Boolean? {
        val t = terme.replace("$", "").replace(",", ".")
        val absolu = kotlin.math.abs(montant)
        Regex("^([<>])=?(\\d+(?:\\.\\d{1,2})?)$").find(t)?.let { m ->
            val v = m.groupValues[2].toDouble()
            return if (m.groupValues[1] == ">") absolu >= v else absolu <= v
        }
        Regex("^(\\d+(?:\\.\\d{1,2})?)-(\\d+(?:\\.\\d{1,2})?)$").find(t)?.let { m ->
            val a = m.groupValues[1].toDouble()
            val b = m.groupValues[2].toDouble()
            return absolu in minOf(a, b)..maxOf(a, b)
        }
        return null
    }

    /**
     * HA6 : date — « 12/03 », « 12/03/2026 », « 03/2026 », « 2026-03-12 ».
     * null si le terme n'est pas un critère de date.
     */
    private fun critereDate(terme: String, date: java.util.Date): Boolean? {
        val cal = java.util.Calendar.getInstance().apply { time = date }
        val j = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val m = cal.get(java.util.Calendar.MONTH) + 1
        val a = cal.get(java.util.Calendar.YEAR)
        Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").find(terme)?.let { r ->
            return r.groupValues[1].toInt() == a && r.groupValues[2].toInt() == m &&
                r.groupValues[3].toInt() == j
        }
        Regex("^(\\d{1,2})/(\\d{4})$").find(terme)?.let { r ->
            return r.groupValues[1].toInt() == m && r.groupValues[2].toInt() == a
        }
        Regex("^(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?$").find(terme)?.let { r ->
            val annee = r.groupValues[3].toIntOrNull()
            return r.groupValues[1].toInt() == j && r.groupValues[2].toInt() == m &&
                (annee == null || annee == a)
        }
        return null
    }

    fun filtrer(transactions: List<TransactionUi>, requete: String): List<TransactionUi> =
        if (requete.isBlank()) transactions else transactions.filter { correspond(it, requete) }
}
