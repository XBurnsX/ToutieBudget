package com.xburnsx.toutiebudget.ui.statistiques

import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.util.Calendar
import java.util.Date
import java.math.BigDecimal
import java.math.RoundingMode

/** Définitions uniques utilisées par tous les indicateurs et graphiques (ST1-ST3). */
object ReglesStatistiques {
    fun estCorrectionTechnique(tx: Transaction): Boolean =
        tx.tiersUtiliser?.startsWith("Mise à jour : ") == true

    fun estDepenseBudget(tx: Transaction): Boolean =
        tx.type == TypeTransaction.Depense && !estCorrectionTechnique(tx)

    fun estRevenuBudget(tx: Transaction): Boolean =
        tx.type == TypeTransaction.Revenu && !estCorrectionTechnique(tx)

    fun estSortieTresorerie(tx: Transaction): Boolean = when (tx.type) {
        TypeTransaction.Depense,
        TypeTransaction.Pret,
        TypeTransaction.RemboursementDonne,
        TypeTransaction.PaiementEffectue,
        TypeTransaction.TransfertSortant -> !estCorrectionTechnique(tx)
        else -> false
    }

    fun estEntreeTresorerie(tx: Transaction): Boolean = when (tx.type) {
        TypeTransaction.Revenu,
        TypeTransaction.Emprunt,
        TypeTransaction.RemboursementRecu,
        TypeTransaction.Paiement,
        TypeTransaction.TransfertEntrant -> !estCorrectionTechnique(tx)
        else -> false
    }

    fun totalAuCent(transactions: Iterable<Transaction>, predicate: (Transaction) -> Boolean): Double {
        val cents = transactions.asSequence().filter(predicate)
            .sumOf {
                BigDecimal.valueOf(it.montant).setScale(2, RoundingMode.HALF_UP)
                    .movePointRight(2).longValueExact()
            }
        return cents / 100.0
    }

    fun pourcentage(montant: Double, totalAdmissible: Double): Double =
        if (totalAdmissible <= 0.0) 0.0 else (montant / totalAdmissible).coerceIn(0.0, 1.0)

    /** 7 jours civils complets, borne de fin exclusive et jamais au-delà d'aujourd'hui. */
    fun moyenneDepenses7Jours(transactions: List<Transaction>, dateFin: Date, maintenant: Date = Date()): List<Pair<String, Double>> {
        val finDemandeeExclusive = Calendar.getInstance().apply {
            time = dateFin
            add(Calendar.MILLISECOND, 1)
        }.time
        val finAujourdhuiExclusive = Calendar.getInstance().apply {
            time = maintenant
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }.time
        val finExclusive = minOf(finDemandeeExclusive, finAujourdhuiExclusive)
        val premierJour = Calendar.getInstance().apply {
            time = finExclusive
            add(Calendar.DAY_OF_MONTH, -30)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return List(30) {
            val debutJour = premierJour.time
            val finJourExclusive = Calendar.getInstance().apply { time = debutJour; add(Calendar.DAY_OF_MONTH, 1) }.time
            val debutFenetre = Calendar.getInstance().apply { time = finJourExclusive; add(Calendar.DAY_OF_MONTH, -7) }.time
            val depenses = totalAuCent(transactions.filter { it.date >= debutFenetre && it.date < finJourExclusive }, ::estDepenseBudget)
            val label = java.text.SimpleDateFormat("dd", java.util.Locale.FRENCH).format(debutJour)
            premierJour.add(Calendar.DAY_OF_MONTH, 1)
            label to MoneyFormatter.roundAmount(depenses / 7.0)
        }
    }
}
