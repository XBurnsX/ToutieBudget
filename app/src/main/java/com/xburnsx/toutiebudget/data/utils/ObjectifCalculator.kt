package com.xburnsx.toutiebudget.data.utils

import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * 📊 MONTANT SUGGÉRÉ POUR LE MOIS CONSULTÉ
 *
 * Annuel et à échéance : ce qui reste à mettre, réparti également sur les mois restants
 * (mois consulté inclus). Un mois sauté augmente un peu chacun des mois suivants au lieu
 * de tout demander d'un coup; un gros versement réduit les suivants.
 *   120 $ sur 12 mois → 10 $/mois. Janvier sauté → 120 $ ÷ 11 = 10,91 $.
 *   50 $ mis d'un coup en mars → mars : 0 $, puis (120 $ − 70 $) ÷ 9 mois.
 *
 * Aux deux semaines : chaque échéance qui tombe dans le mois coûte le montant de l'objectif
 * (2 ou 3 échéances selon le mois), moins l'argent déjà disponible pour ce mois.
 *
 * Annuel et à échéance comptent l'argent PRÉSENT dans l'enveloppe, jamais l'argent dépensé :
 * le but est d'avoir le montant à la date. Le report de fin de mois (montantReporte) donne ce
 * qui était présent au début du mois; un report n'est jamais pris pour un nouveau versement.
 */
class ObjectifCalculator(private val zone: ZoneId = ZoneId.systemDefault()) {

    private companion object {
        /** GN3 : le montant reporté est noté depuis le report de fin octobre 2026 (vers novembre). */
        val PREMIER_REPORT_NOTE: YearMonth = YearMonth.of(2026, 10)
    }

    private fun arrondirSuggestion(suggestion: Double): Double {
        if (!suggestion.isFinite()) return 0.0
        val cents = (suggestion * 100.0).roundToLong()
        return if (cents < 1) 0.0 else cents / 100.0
    }

    private fun Date.moisAnnee(): YearMonth = YearMonth.from(toInstant().atZone(zone))
    private fun Date.jour(): LocalDate = toInstant().atZone(zone).toLocalDate()
    private fun Double.fini(): Double = if (isFinite()) this else 0.0

    /**
     * Calcule le montant suggéré pour le mois consulté.
     *
     * @param soldeActuel Progrès du mois (utilisé seulement par l'objectif mensuel)
     * @param moisSelectionne Un jour quelconque du mois consulté
     * @param allocationsMensuelles Toutes les allocations de CETTE enveloppe, tous mois confondus
     */
    fun calculerVersementRecommande(
        enveloppe: Enveloppe,
        soldeActuel: Double,
        moisSelectionne: Date,
        allocationsMensuelles: List<AllocationMensuelle> = emptyList()
    ): Double {
        val objectif = enveloppe.objectifMontant
        if (!objectif.isFinite() || objectif <= 0.0) return 0.0
        val mois = moisSelectionne.moisAnnee()

        return when (enveloppe.typeObjectif) {
            TypeObjectif.Mensuel -> arrondirSuggestion(max(0.0, objectif - soldeActuel))
            TypeObjectif.Annuel -> {
                val limite = enveloppe.dateObjectif?.jour()
                    ?: enveloppe.dateDebutObjectif?.jour()?.plusYears(1)
                    ?: LocalDate.of(mois.year + 1, 1, 1)
                repartirSurMoisRestants(
                    objectif, enveloppe.dateDebutObjectif?.moisAnnee(), limite, mois, allocationsMensuelles
                )
            }
            TypeObjectif.Echeance -> {
                val echeance = enveloppe.dateObjectif?.jour() ?: return 0.0
                repartirSurMoisRestants(
                    objectif, enveloppe.dateDebutObjectif?.moisAnnee(), echeance, mois, allocationsMensuelles
                )
            }
            TypeObjectif.Bihebdomadaire -> besoinBihebdomadaire(enveloppe, objectif, mois, allocationsMensuelles)
            else -> 0.0
        }
    }

    // ---------- Annuel / échéance : option B ----------

    private fun repartirSurMoisRestants(
        objectif: Double,
        debut: YearMonth?,
        echeance: LocalDate,
        mois: YearMonth,
        lignes: List<AllocationMensuelle>
    ): Double {
        // L'objectif n'a pas encore commencé ce mois-là
        if (debut != null && mois.isBefore(debut)) return 0.0

        // Une échéance le 1er se prépare jusqu'à la fin du mois précédent
        val dernierMois = YearMonth.from(echeance).let { if (echeance.dayOfMonth == 1) it.minusMonths(1) else it }
        val moisRestants = max(1L, ChronoUnit.MONTHS.between(mois, dernierMois) + 1)

        // Le but est d'AVOIR le montant à la date : seul l'argent présent dans l'enveloppe compte.
        // Ce qui a été dépensé n'avance pas l'objectif (s'il faut 400 $, il faut 400 $ dedans).
        val duMois = lignes.filter { it.mois.moisAnnee() == mois }
        // Solde de l'enveloppe : actuel, ou fin de mois pour un mois déjà reporté
        val solde = duMois.sumOf { it.solde.fini() + it.montantReporte.fini() }
        val depenseCeMois = duMois.sumOf { it.depense.fini() }

        val precedent = mois.minusMonths(1)
        val (presentAuDepart, versementsCeMois) = if (!precedent.isBefore(PREMIER_REPORT_NOTE)) {
            // Présent au début du mois = ce que le mois précédent a reporté
            val report = lignes.filter { it.mois.moisAnnee() == precedent }.sumOf { it.montantReporte.fini() }
            // Mis ce mois-ci = solde − report + dépenses du mois
            (report - depenseCeMois) to (solde - report + depenseCeMois)
        } else {
            // Report d'avant GN3 : non noté, on ne peut pas séparer l'argent arrivé ce mois-ci.
            // On répartit simplement ce qui manque au solde actuel.
            solde to 0.0
        }

        val partCeMois = max(0.0, objectif - presentAuDepart) / moisRestants
        return arrondirSuggestion(max(0.0, partCeMois - max(0.0, versementsCeMois)))
    }

    // ---------- Aux deux semaines ----------

    private fun besoinBihebdomadaire(
        enveloppe: Enveloppe,
        montantParCycle: Double,
        mois: YearMonth,
        lignes: List<AllocationMensuelle>
    ): Double {
        // L'échéance connue sert de repère : les autres tombent tous les 14 jours avant et après
        val repere = enveloppe.dateObjectif?.jour()
            ?: enveloppe.dateDebutObjectif?.jour()?.plusDays(14)
            ?: return 0.0
        val premierJour = mois.atDay(1)
        val dernierJour = mois.atEndOfMonth()
        val ecart = ChronoUnit.DAYS.between(repere, premierJour)
        var echeance = repere.plusDays(Math.floorDiv(ecart, 14L) * 14L)
        if (echeance.isBefore(premierJour)) echeance = echeance.plusDays(14)
        var echeances = 0
        while (!echeance.isAfter(dernierJour)) {
            echeances++
            echeance = echeance.plusDays(14)
        }
        if (echeances == 0) return 0.0

        // Disponible pour ce mois : report reçu + versements du mois. Un mois déjà reporté
        // ne garde que ses dépenses dans « alloué » : on y rajoute ce qui est parti au suivant.
        val disponible = lignes.filter { it.mois.moisAnnee() == mois }
            .sumOf { it.alloue.fini() + it.montantReporte.fini() }
        return arrondirSuggestion(max(0.0, echeances * montantParCycle - disponible))
    }
}
