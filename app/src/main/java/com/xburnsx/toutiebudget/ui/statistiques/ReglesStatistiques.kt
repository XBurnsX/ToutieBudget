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
    data class FractionDepense(val enveloppeId: String, val montant: Double)

    /**
     * ST1/ST5/AT9 : toutes les stats utilisent les mêmes fractions que la
     * transaction réelle. Données manquantes = calcul incomplet, jamais 0 $.
     */
    fun lireFractions(tx: Transaction): List<FractionDepense> {
        require(tx.estFractionnee && !tx.sousItems.isNullOrBlank()) {
            "Transaction fractionnée sans fractions."
        }
        val elements = com.google.gson.JsonParser.parseString(tx.sousItems).asJsonArray
        require(elements.size() > 0) { "Transaction fractionnée sans sous-items." }
        val fractions = elements.map { element ->
            val objet = element.asJsonObject
            val enveloppe = (objet.get("enveloppe_id") ?: objet.get("enveloppeId"))
                ?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            require(enveloppe.isNotBlank()) { "Fraction sans enveloppe." }
            val montant = objet.get("montant")?.takeIf { !it.isJsonNull }?.asDouble
                ?: throw IllegalArgumentException("Fraction sans montant.")
            require(montant.isFinite() && montant > 0.0) { "Montant de fraction invalide." }
            FractionDepense(enveloppe, montant)
        }
        val totalEnCents = fractions.sumOf { fraction ->
            BigDecimal.valueOf(fraction.montant).movePointRight(2).longValueExact()
        }
        val attendu = BigDecimal.valueOf(tx.montant).movePointRight(2).longValueExact()
        require(totalEnCents == attendu) { "Total des fractions différent de la transaction." }
        return fractions
    }

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

    /**
     * SA5 : moyenne quotidienne de consommation sur les N jours civils precedant
     * (et incluant) la fin retenue. Aucun jour futur n'entre dans le calcul.
     * Null veut dire « periode future ou invalide », non zero de depense.
     */
    fun moyenneDepensesQuotidienne(
        transactions: List<Transaction>,
        dateDebut: Date,
        dateFin: Date,
        jours: Int,
        maintenant: Date = Date()
    ): Double? {
        require(jours > 0) { "La fenêtre doit comporter au moins un jour." }
        if (dateFin.before(dateDebut) || dateDebut.after(maintenant)) return null
        val finEffective = minOf(dateFin, maintenant)
        val zone = java.time.ZoneId.systemDefault()
        val premierJourPeriode = dateDebut.toInstant().atZone(zone).toLocalDate()
        val dernierJour = finEffective.toInstant().atZone(zone).toLocalDate()
        val debutFenetre = maxOf(premierJourPeriode, dernierJour.minusDays((jours - 1).toLong()))
        val nbJours = java.time.temporal.ChronoUnit.DAYS.between(debutFenetre, dernierJour) + 1
        if (nbJours <= 0L) return null
        val dateDebutFenetre = Date.from(debutFenetre.atStartOfDay(zone).toInstant())
        val montant = totalAuCent(transactions.filter {
            it.date >= dateDebutFenetre && it.date <= finEffective
        }, ::estDepenseBudget)
        return MoneyFormatter.roundAmount(montant / nbJours.toDouble())
    }

    /** 7 jours civils complets, borne de fin exclusive et jamais au-delà d'aujourd'hui. */
    fun moyenneDepenses7Jours(
        transactions: List<Transaction>,
        dateFin: Date,
        maintenant: Date = Date(),
        dateDebutPeriode: Date? = null
    ): List<Pair<String, Double>> {
        // ST11 : un mois intégralement futur ne doit jamais récupérer
        // artificiellement la moyenne des 30 jours du mois courant.
        if (dateDebutPeriode != null && dateDebutPeriode.after(maintenant)) return emptyList()
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
