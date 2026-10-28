package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodeHistoriqueTest {
    private val zone = ZoneId.of("America/Toronto")
    private fun transaction(id: String, date: String): TransactionUi =
        TransactionUi(
            id = id, type = TypeTransaction.Depense,
            montant = 10.0,
            date = Date.from(LocalDate.parse(date).atStartOfDay(zone).toInstant()),
            tiersUtiliser = "Marché", nomEnveloppe = "Épicerie", note = null
        )

    @Test fun toutesLesPeriodesNePerdentAucuneTransaction() {
        val transactions = listOf(transaction("2024", "2024-01-01"), transaction("2026", "2026-12-31"))
        assertEquals(transactions, PeriodeHistorique().filtrer(transactions, zone))
    }

    @Test fun moisChoisiInclusPremierEtDernierJourSeulement() {
        val historique = listOf(
            transaction("avant", "2026-09-30"),
            transaction("premier", "2026-10-01"),
            transaction("dernier", "2026-10-31"),
            transaction("apres", "2026-11-01")
        )
        val selection = PeriodeHistorique(VuePeriodeHistorique.MOIS, 2026, 10)
        assertEquals(listOf("premier", "dernier"), selection.filtrer(historique, zone).map { it.id })
    }

    @Test fun anneeInclutDouzeMoisSansConfondreLesAnnees() {
        val historique = listOf(
            transaction("avant", "2025-12-31"),
            transaction("janvier", "2026-01-01"),
            transaction("decembre", "2026-12-31"),
            transaction("apres", "2027-01-01")
        )
        assertEquals(
            listOf("janvier", "decembre"),
            PeriodeHistorique(VuePeriodeHistorique.ANNEE, 2026, 7).filtrer(historique, zone).map { it.id }
        )
    }

    @Test fun naviguerDecembreJanvierChangeBienAnnee() {
        val decembre = PeriodeHistorique(VuePeriodeHistorique.MOIS, 2026, 12)
        assertEquals(PeriodeHistorique(VuePeriodeHistorique.MOIS, 2027, 1), decembre.deplacer(1))
        assertEquals(PeriodeHistorique(VuePeriodeHistorique.MOIS, 2026, 12), decembre.deplacer(1).deplacer(-1))
    }

    @Test fun suivantEtPrecedentAnnee() {
        val selection = PeriodeHistorique(VuePeriodeHistorique.ANNEE, 2026, 2)
        assertEquals(2025, selection.deplacer(-1).annee)
        assertEquals(2027, selection.deplacer(1).annee)
        assertTrue(selection.contient(transaction("x", "2026-05-10").date, zone))
        assertFalse(selection.contient(transaction("x", "2025-05-10").date, zone))
    }

    @Test fun selectionTotaleNestPasModifieeParLesFleches() {
        val selection = PeriodeHistorique()
        assertEquals(selection, selection.deplacer(1))
    }
}
