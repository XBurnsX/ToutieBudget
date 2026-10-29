package com.xburnsx.toutiebudget.utils.notifications

import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.util.Date

/**
 * PR11/PR13 : rappels calculés à partir des données des dépôts, pour le mois RÉEL d'aujourd'hui
 * (jamais le mois consulté à l'écran). Fonctions pures, sans Android, testables.
 */
object CalculRappels {
    data class RappelObjectif(val enveloppe: Enveloppe, val echeance: LocalDate, val reste: Double, val progres: Double)

    private fun Date.jour(zone: ZoneId): LocalDate = toInstant().atZone(zone).toLocalDate()

    /** Enveloppes en négatif au cent, pour le mois courant (somme de toutes leurs allocations du mois). */
    fun deficits(
        enveloppes: List<Enveloppe>,
        allocationsDuMois: List<AllocationMensuelle>,
        idsCategoriesExclues: Set<String>
    ): List<Pair<Enveloppe, Double>> {
        val parEnveloppe = allocationsDuMois.groupBy { it.enveloppeId }
        return enveloppes.filter { !it.estArchive && it.categorieId !in idsCategoriesExclues }.mapNotNull { env ->
            val centimes = parEnveloppe[env.id].orEmpty().sumOf { Math.round(it.solde * 100.0) }
            if (centimes < 0L) env to centimes / 100.0 else null
        }
    }

    /** Prochaine échéance réelle d'un objectif à partir d'aujourd'hui ; null si aucune ou déjà passée. */
    fun prochaineEcheance(env: Enveloppe, aujourdHui: LocalDate, zone: ZoneId): LocalDate? = when (env.typeObjectif) {
        TypeObjectif.Aucun -> null
        TypeObjectif.Mensuel -> {
            // Jour du mois (29-31 ramené au dernier jour des mois courts) ; sinon le jour de la date d'objectif
            val jour = env.objectifJour ?: env.dateObjectif?.jour(zone)?.dayOfMonth
            jour?.takeIf { it in 1..31 }?.let { j ->
                val ceMois = YearMonth.from(aujourdHui)
                val ici = ceMois.atDay(minOf(j, ceMois.lengthOfMonth()))
                if (!ici.isBefore(aujourdHui)) ici
                else ceMois.plusMonths(1).let { it.atDay(minOf(j, it.lengthOfMonth())) }
            }
        }
        else -> env.dateObjectif?.jour(zone)?.takeIf { !it.isBefore(aujourdHui) }
    }

    /**
     * Progrès vers l'objectif : le mois courant seulement pour un objectif mensuel,
     * sinon le cumul alloué depuis le début de l'objectif.
     */
    fun progres(env: Enveloppe, allocationsEnveloppe: List<AllocationMensuelle>, aujourdHui: LocalDate, zone: ZoneId): Double {
        val moisCourant = YearMonth.from(aujourdHui)
        val debut = env.dateDebutObjectif?.jour(zone)?.let { YearMonth.from(it) }
        val retenues = allocationsEnveloppe.filter { a ->
            val mois = YearMonth.from(a.mois.jour(zone))
            if (env.typeObjectif == TypeObjectif.Mensuel) mois == moisCourant
            else !mois.isAfter(moisCourant) && (debut == null || !mois.isBefore(debut))
        }
        return retenues.sumOf { Math.round(it.alloue * 100.0) } / 100.0
    }

    data class PaiementPlanifie(val enveloppe: Enveloppe, val date: LocalDate, val montant: Double, val disponible: Double) {
        val manque: Double get() = maxOf(0.0, (Math.round(montant * 100.0) - Math.round(disponible * 100.0)) / 100.0)
    }

    /**
     * « Paiement à X planifié pour demain » : enveloppes dont l'échéance de l'objectif tombe DEMAIN.
     * Le montant est celui de l'objectif ; « disponible » est le solde actuel de l'enveloppe ce mois-ci.
     */
    fun paiementsDemain(
        enveloppes: List<Enveloppe>,
        allocationsDuMois: List<AllocationMensuelle>,
        aujourdHui: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<PaiementPlanifie> {
        val demain = aujourdHui.plusDays(1)
        val soldes = allocationsDuMois.groupBy { it.enveloppeId }
            .mapValues { (_, a) -> a.sumOf { Math.round(it.solde * 100.0) } / 100.0 }
        return enveloppes.filter { !it.estArchive && it.objectifMontant > 0.0 && it.objectifMontant.isFinite() }
            .mapNotNull { env ->
                val echeance = prochaineEcheance(env, aujourdHui, zone) ?: return@mapNotNull null
                if (echeance != demain) return@mapNotNull null
                PaiementPlanifie(env, echeance, env.objectifMontant, soldes[env.id] ?: 0.0)
            }
    }

    data class PaiementOublie(val enveloppe: Enveloppe, val date: LocalDate, val montant: Double)

    /** Dernière échéance déjà passée (strictement avant aujourd'hui), ou null. */
    fun derniereEcheancePassee(env: Enveloppe, aujourdHui: LocalDate, zone: ZoneId): LocalDate? = when (env.typeObjectif) {
        TypeObjectif.Aucun -> null
        TypeObjectif.Mensuel -> {
            val jour = env.objectifJour ?: env.dateObjectif?.jour(zone)?.dayOfMonth
            jour?.takeIf { it in 1..31 }?.let { j ->
                val ceMois = YearMonth.from(aujourdHui)
                val ici = ceMois.atDay(minOf(j, ceMois.lengthOfMonth()))
                if (ici.isBefore(aujourdHui)) ici
                else ceMois.minusMonths(1).let { it.atDay(minOf(j, it.lengthOfMonth())) }
            }
        }
        // Après un renouvellement, le début du cycle actuel EST l'échéance qui vient de passer
        else -> listOfNotNull(env.dateObjectif?.jour(zone), env.dateDebutObjectif?.jour(zone))
            .filter { it.isBefore(aujourdHui) }.maxOrNull()
    }

    /**
     * « Paiement oublié » : l'échéance de l'objectif est passée depuis 1 à [joursMax] jours et aucun paiement
     * n'a été fait dans l'enveloppe pour cette échéance (aucune dépense ce mois-là ; pour un objectif aux deux
     * semaines, pas plus que ce que les échéances précédentes du mois ont déjà demandé).
     */
    fun paiementOublie(
        env: Enveloppe,
        allocationsEnveloppe: List<AllocationMensuelle>,
        aujourdHui: LocalDate,
        joursMax: Long = 3,
        zone: ZoneId = ZoneId.systemDefault()
    ): PaiementOublie? {
        if (env.estArchive || env.objectifMontant <= 0.0 || !env.objectifMontant.isFinite()) return null
        val echeance = derniereEcheancePassee(env, aujourdHui, zone) ?: return null
        if (echeance.isBefore(aujourdHui.minusDays(joursMax))) return null
        val moisEcheance = YearMonth.from(echeance)
        val depenseCentimes = allocationsEnveloppe
            .filter { YearMonth.from(it.mois.jour(zone)) == moisEcheance }
            .sumOf { Math.round(it.depense * 100.0) }
        // Échéances du même mois AVANT celle-ci (aux deux semaines : souvent 1) déjà couvertes par des dépenses
        val echeancesPrecedentes = if (env.typeObjectif == TypeObjectif.Bihebdomadaire) {
            generateSequence(echeance.minusDays(14)) { it.minusDays(14) }
                .takeWhile { YearMonth.from(it) == moisEcheance }.count()
        } else 0
        val dejaAttendu = echeancesPrecedentes * Math.round(env.objectifMontant * 100.0)
        val paye = depenseCentimes > dejaAttendu
        return if (paye) null else PaiementOublie(env, echeance, env.objectifMontant)
    }

    /** Rappel si l'échéance tombe dans [joursAvant] jours et qu'il reste au moins 1 cent à mettre. */
    fun rappelObjectif(
        env: Enveloppe,
        allocationsEnveloppe: List<AllocationMensuelle>,
        aujourdHui: LocalDate,
        joursAvant: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): RappelObjectif? {
        if (env.estArchive || env.objectifMontant <= 0.0 || !env.objectifMontant.isFinite()) return null
        val echeance = prochaineEcheance(env, aujourdHui, zone) ?: return null
        if (echeance.isAfter(aujourdHui.plusDays(joursAvant.coerceAtLeast(0).toLong()))) return null
        val progres = progres(env, allocationsEnveloppe, aujourdHui, zone)
        val resteCentimes = Math.round(env.objectifMontant * 100.0) - Math.round(progres * 100.0)
        if (resteCentimes <= 0L) return null
        return RappelObjectif(env, echeance, resteCentimes / 100.0, progres)
    }
}
