package com.xburnsx.toutiebudget.data.services

import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

/**
 * CE18 : renouveler le CALENDRIER d'un objectif sans jamais modifier
 * les allocations comptables (solde, alloue, depense).
 *
 * Une échéance reste valide le jour même; le cycle avance à partir du
 * lendemain. Les jours sont calendaires, sans décalage d'heure d'été.
 */
object CalendrierRenouvellementObjectif {
    private fun Date.jour(zone: ZoneId): LocalDate = toInstant().atZone(zone).toLocalDate()
    private fun LocalDate.date(zone: ZoneId): Date = Date.from(atStartOfDay(zone).toInstant())

    fun prochaineVersion(
        enveloppe: Enveloppe,
        aujourdHui: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Enveloppe? {
        if (enveloppe.estArchive) return null
        val type = enveloppe.typeObjectif
        if (type != TypeObjectif.Bihebdomadaire &&
            type != TypeObjectif.Annuel &&
            type != TypeObjectif.Echeance) return null
        if (type == TypeObjectif.Echeance && !enveloppe.resetApresEcheance) return null

        val derniereEcheance = enveloppe.dateObjectif?.jour(zone) ?: return null
        if (!aujourdHui.isAfter(derniereEcheance)) return null

        val nouveauDebut: LocalDate
        val nouvelleEcheance: LocalDate
        when (type) {
            TypeObjectif.Bihebdomadaire -> {
                val ecart = ChronoUnit.DAYS.between(derniereEcheance, aujourdHui)
                val cycles = (ecart - 1) / 14 + 1
                val dernierCycle = Math.multiplyExact(cycles - 1, 14L)
                val finCycle = Math.multiplyExact(cycles, 14L)
                nouveauDebut = derniereEcheance.plusDays(dernierCycle)
                nouvelleEcheance = derniereEcheance.plusDays(finCycle)
            }
            TypeObjectif.Annuel -> {
                var debut = derniereEcheance
                var fin = debut.plusYears(1)
                // Au plus un passage par année manquée. Aucun changement
                // d'allocations même après des années sans lancer l'app.
                var essais = 0
                while (aujourdHui.isAfter(fin)) {
                    require(++essais <= 1000) { "Date d'objectif trop ancienne." }
                    debut = fin
                    fin = debut.plusYears(1)
                }
                nouveauDebut = debut
                nouvelleEcheance = fin
            }
            TypeObjectif.Echeance -> {
                val origine = enveloppe.dateDebutObjectif?.jour(zone) ?: return null
                val duree = ChronoUnit.DAYS.between(origine, derniereEcheance)
                require(duree > 0) { "Une échéance récurrente exige une durée positive." }
                val ecart = ChronoUnit.DAYS.between(derniereEcheance, aujourdHui)
                val cycles = (ecart - 1) / duree + 1
                nouveauDebut = derniereEcheance.plusDays(Math.multiplyExact(cycles - 1, duree))
                nouvelleEcheance = derniereEcheance.plusDays(Math.multiplyExact(cycles, duree))
            }
            else -> return null
        }

        return enveloppe.copy(
            dateDebutObjectif = nouveauDebut.date(zone),
            dateObjectif = nouvelleEcheance.date(zone)
        )
    }
}
