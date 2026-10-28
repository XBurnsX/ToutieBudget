package com.xburnsx.toutiebudget.ui.categories

import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date

/** CE14–CE18 : calendrier des objectifs; jamais de mutation financière. */
object ReglesObjectifEnveloppe {
    private fun Date.jour(zone: ZoneId): LocalDate = toInstant().atZone(zone).toLocalDate()
    private fun LocalDate.date(zone: ZoneId): Date = Date.from(atStartOfDay(zone).toInstant())

    /** Les 29, 30 et 31 tombent sur le dernier jour si le mois est plus court. */
    fun dateMensuelle(mois: YearMonth, jour: Int): LocalDate {
        require(jour in 1..31) { "Le jour mensuel doit être entre 1 et 31." }
        return mois.atDay(minOf(jour, mois.lengthOfMonth()))
    }

    fun appliquer(
        existante: Enveloppe,
        formulaire: ObjectifFormState,
        aujourdHui: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Enveloppe {
        val type = formulaire.type
        val montant = if (type == TypeObjectif.Aucun) 0.0 else {
            val decimal = runCatching {
                BigDecimal(formulaire.montant.trim().replace(',', '.'))
            }.getOrNull() ?: throw IllegalArgumentException("Indique un montant d'objectif valide.")
            require(decimal.signum() > 0 && decimal.scale() <= 2) {
                "Le montant de l'objectif doit être positif et précis au centime."
            }
            require(decimal.toDouble().isFinite()) { "Montant d'objectif trop élevé." }
            decimal.toDouble()
        }
        val memeType = existante.typeObjectif == type
        val debut: LocalDate?
        val echeance: LocalDate?
        val jour: Int?
        when (type) {
            TypeObjectif.Aucun -> { debut = null; echeance = null; jour = null }
            TypeObjectif.Mensuel -> {
                jour = formulaire.jour ?: if (memeType) existante.objectifJour else null
                    ?: throw IllegalArgumentException("Choisis un jour du mois (1 à 31).")
                require(jour in 1..31) { "Jour du mois invalide." }
                val conserver = memeType && jour == existante.objectifJour &&
                    existante.dateDebutObjectif != null && existante.dateObjectif != null
                val premiere = if (conserver) existante.dateDebutObjectif!!.jour(zone) else {
                    val ceMois = dateMensuelle(YearMonth.from(aujourdHui), jour)
                    if (ceMois.isBefore(aujourdHui)) {
                        dateMensuelle(YearMonth.from(aujourdHui).plusMonths(1), jour)
                    } else ceMois
                }
                debut = premiere
                echeance = if (conserver) existante.dateObjectif!!.jour(zone) else premiere
            }
            TypeObjectif.Bihebdomadaire -> {
                jour = formulaire.jour ?: if (memeType) existante.objectifJour else null
                    ?: throw IllegalArgumentException("Choisis un jour de semaine.")
                require(jour in 1..7) { "Jour de semaine invalide." }
                debut = (formulaire.date ?: if (memeType) existante.dateDebutObjectif else null)
                    ?.jour(zone) ?: throw IllegalArgumentException("Choisis une date de départ.")
                require(debut.dayOfWeek.value == jour) {
                    "Le jour de semaine doit correspondre à la date de départ."
                }
                val conserver = memeType && debut == existante.dateDebutObjectif?.jour(zone) &&
                    jour == existante.objectifJour && existante.dateObjectif != null
                echeance = if (conserver) existante.dateObjectif!!.jour(zone) else debut.plusDays(14)
            }
            TypeObjectif.Annuel -> {
                jour = null
                debut = (formulaire.date ?: if (memeType) existante.dateDebutObjectif else null)
                    ?.jour(zone) ?: aujourdHui
                val conserver = memeType && debut == existante.dateDebutObjectif?.jour(zone) &&
                    existante.dateObjectif != null
                echeance = if (conserver) existante.dateObjectif!!.jour(zone) else debut.plusYears(1)
            }
            TypeObjectif.Echeance -> {
                jour = null
                debut = (formulaire.dateDebut ?: if (memeType) existante.dateDebutObjectif else null)
                    ?.jour(zone) ?: throw IllegalArgumentException("Choisis la date de début.")
                echeance = (formulaire.dateFin ?: if (memeType) existante.dateObjectif else null)
                    ?.jour(zone) ?: throw IllegalArgumentException("Choisis la date d'échéance.")
                require(!echeance.isBefore(debut)) {
                    "La date d'échéance doit être égale ou postérieure au début."
                }
            }
        }
        return existante.copy(
            typeObjectif = type,
            objectifMontant = montant,
            dateDebutObjectif = debut?.date(zone),
            dateObjectif = echeance?.date(zone),
            objectifJour = jour,
            resetApresEcheance = if (type == TypeObjectif.Echeance)
                formulaire.resetApresEcheance else false
        )
    }
}
