package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HC15 : gros historique simulé, dates ordonnées et anti-doublons de lecture.
 * Aucun ajout, suppression ou recalcul de solde bancaire.
 */
class RegroupementHistoriqueTest {
    private val fuseau = ZoneId.of("America/Toronto")

    private fun ligne(id: String, date: String, revision: Long? = null, montant: Double = 5.0) =
        TransactionUi(
            id = id,
            type = TypeTransaction.Depense,
            montant = montant,
            date = Date.from(LocalDate.parse(date).atStartOfDay(fuseau).toInstant()),
            tiersUtiliser = "Marché",
            nomEnveloppe = null,
            note = null,
            revisionTransaction = revision?.let { Date(it) }
        )

    @Test fun cinqMilleTransactionsOntToutesUnIdUniqueEtUnOrdreDeDateStable() {
        val transactions = (0 until 5_000).map { i ->
            ligne("tx_$i", LocalDate.of(2026, 1, 1).plusDays((i % 365).toLong()).toString())
        }.reversed()
        val resultat = RegroupementHistorique.grouper(transactions, fuseau)
        assertEquals(5_000, resultat.values.sumOf { it.size })
        assertEquals(5_000, resultat.values.flatten().map { it.id }.distinct().size)
        val dates = resultat.values.flatten().map { it.date.time }
        assertEquals(dates.sortedDescending(), dates)
        assertEquals("31 décembre 2026", resultat.keys.first())
        assertEquals("1 janvier 2026", resultat.keys.last())
    }

    @Test fun doublonDeSyncNeFaitPasApparaitreDeuxMouvements() {
        val original = ligne("achat", "2026-10-28", revision = 100L, montant = 10.0)
        val plusRecent = ligne("achat", "2026-10-28", revision = 200L, montant = 12.0)
        val lignes = listOf(original, ligne("autre", "2026-10-28"), plusRecent)
        assertEquals(1, RegroupementHistorique.nombreDoublons(lignes))
        val groupes = RegroupementHistorique.grouper(lignes, fuseau)
        assertEquals(2, groupes.values.flatten().size)
        assertEquals(12.0, groupes.values.flatten().first { it.id == "achat" }.montant, 0.000001)
    }

    @Test fun ordreChronologiqueNeDependPasDuTriAlphabetiqueFrancais() {
        val resultats = RegroupementHistorique.grouper(
            listOf(ligne("janvier", "2026-01-02"), ligne("decembre", "2025-12-31")),
            fuseau
        )
        assertTrue(resultats.values.first().single().id == "janvier")
        assertEquals(2, resultats.size)
    }

    @Test fun identifiantsIdentiquesAMemeDateOntUnOrdreDeterministe() {
        val regroupes = RegroupementHistorique.grouper(
            listOf(ligne("b", "2026-10-28"), ligne("a", "2026-10-28")),
            fuseau
        )
        assertEquals(listOf("a", "b"), regroupes.values.single().map { it.id })
    }
}
