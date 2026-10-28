package com.xburnsx.toutiebudget.ui.historique

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date

/**
 * EA1 : filtre temporel local, sans changer la date ni les montants enregistrés.
 * Une année et un mois sont sélectionnables, ou l'historique complet.
 */
enum class VuePeriodeHistorique { TOUTES, MOIS, ANNEE }

data class PeriodeHistorique(
    val vue: VuePeriodeHistorique = VuePeriodeHistorique.TOUTES,
    val annee: Int = LocalDate.now().year,
    val mois: Int = LocalDate.now().monthValue
) {
    init {
        require(annee in 1..9999) { "Année invalide" }
        require(mois in 1..12) { "Mois invalide" }
    }

    fun deplacer(pas: Int): PeriodeHistorique = runCatching {
        when (vue) {
            VuePeriodeHistorique.TOUTES -> this
            VuePeriodeHistorique.MOIS -> {
                val prochain = YearMonth.of(annee, mois).plusMonths(pas.toLong())
                copy(annee = prochain.year, mois = prochain.monthValue)
            }
            VuePeriodeHistorique.ANNEE -> copy(annee = Math.addExact(annee, pas))
        }
    }.getOrElse { this } // Bornes calendaires 1..9999, sans crash au clic.

    fun contient(date: Date, fuseau: ZoneId = ZoneId.systemDefault()): Boolean {
        if (vue == VuePeriodeHistorique.TOUTES) return true
        val locale = Instant.ofEpochMilli(date.time).atZone(fuseau).toLocalDate()
        return locale.year == annee &&
            (vue == VuePeriodeHistorique.ANNEE || locale.monthValue == mois)
    }

    fun filtrer(lignes: List<TransactionUi>, fuseau: ZoneId = ZoneId.systemDefault()): List<TransactionUi> =
        if (vue == VuePeriodeHistorique.TOUTES) lignes
        else lignes.filter { contient(it.date, fuseau) }
}
