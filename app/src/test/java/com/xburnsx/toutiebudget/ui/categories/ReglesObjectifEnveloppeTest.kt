package com.xburnsx.toutiebudget.ui.categories

import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReglesObjectifEnveloppeTest {
    private val zone = ZoneId.of("America/Toronto")
    private fun date(s: String) =
        Date.from(LocalDate.parse(s).atStartOfDay(zone).toInstant())
    private fun jour(date: Date?) = date!!.toInstant().atZone(zone).toLocalDate()
    private val enveloppe = Enveloppe(
        id = "env", utilisateurId = "u", nom = "Essence",
        categorieId = "transport", estArchive = false, ordre = 2
    )

    @Test fun finDeMois29_30_31EtFevrierBissextile() {
        assertEquals(LocalDate.parse("2026-02-28"),
            ReglesObjectifEnveloppe.dateMensuelle(YearMonth.of(2026, 2), 31))
        assertEquals(LocalDate.parse("2028-02-29"),
            ReglesObjectifEnveloppe.dateMensuelle(YearMonth.of(2028, 2), 31))
        assertEquals(LocalDate.parse("2028-02-29"),
            ReglesObjectifEnveloppe.dateMensuelle(YearMonth.of(2028, 2), 30))
        assertEquals(LocalDate.parse("2028-02-29"),
            ReglesObjectifEnveloppe.dateMensuelle(YearMonth.of(2028, 2), 29))
    }

    @Test fun prochainMoisUtiliseJour31SansSauterFevrier() {
        val result = ReglesObjectifEnveloppe.appliquer(
            enveloppe, ObjectifFormState(
                type = TypeObjectif.Mensuel, montant = "100.00", jour = 31
            ), aujourdHui = LocalDate.of(2027, 1, 31), zone = zone
        )
        assertEquals(LocalDate.of(2027, 1, 31), jour(result.dateDebutObjectif))
        val suivant = ReglesObjectifEnveloppe.dateMensuelle(
            YearMonth.from(jour(result.dateObjectif)).plusMonths(1), 31
        )
        assertEquals(LocalDate.of(2027, 2, 28), suivant)
    }

    @Test fun editingMontantConserveDatesEtReset() {
        val old = enveloppe.copy(
            typeObjectif = TypeObjectif.Echeance,
            objectifMontant = 100.0,
            dateDebutObjectif = date("2025-04-04"),
            dateObjectif = date("2027-12-31"),
            resetApresEcheance = true
        )
        val modifie = ReglesObjectifEnveloppe.appliquer(
            old,
            ObjectifFormState(
                type = TypeObjectif.Echeance, montant = "120.00",
                dateDebut = old.dateDebutObjectif,
                dateFin = old.dateObjectif,
                resetApresEcheance = true
            ),
            aujourdHui = LocalDate.of(2026, 10, 28), zone = zone
        )
        assertEquals(120.0, modifie.objectifMontant, 0.0001)
        assertEquals(old.dateDebutObjectif, modifie.dateDebutObjectif)
        assertEquals(old.dateObjectif, modifie.dateObjectif)
        assertEquals(true, modifie.resetApresEcheance)
    }

    @Test fun deuxSemainesJourEt14Jours() {
        val result = ReglesObjectifEnveloppe.appliquer(
            enveloppe, ObjectifFormState(
                type = TypeObjectif.Bihebdomadaire,
                montant = "50", date = date("2026-10-26"), jour = 1
            ),
            zone = zone
        )
        assertEquals(LocalDate.parse("2026-11-09"), jour(result.dateObjectif))
        assertThrows(IllegalArgumentException::class.java) {
            ReglesObjectifEnveloppe.appliquer(
                enveloppe, ObjectifFormState(
                    type = TypeObjectif.Bihebdomadaire,
                    montant = "50", date = date("2026-10-26"), jour = 3
                ), zone = zone
            )
        }
    }

    @Test fun echeanceInverseEtMontantsInvalidesRefuses() {
        assertThrows(IllegalArgumentException::class.java) {
            ReglesObjectifEnveloppe.appliquer(
                enveloppe, ObjectifFormState(
                    type = TypeObjectif.Echeance, montant = "40",
                    dateDebut = date("2027-01-01"), dateFin = date("2026-12-31")
                ), zone = zone
            )
        }
        for (montant in listOf("0", "-3", "NaN", "1.001", "")) {
            assertThrows(IllegalArgumentException::class.java) {
                ReglesObjectifEnveloppe.appliquer(
                    enveloppe, ObjectifFormState(
                        type = TypeObjectif.Mensuel, montant = montant, jour = 15
                    ), zone = zone
                )
            }
        }
    }
}
