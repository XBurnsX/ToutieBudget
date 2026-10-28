package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltrageHistoriqueCompteTest {
    private val zone = ZoneId.systemDefault()
    private fun tx(id: String, date: String, type: TypeTransaction, tiers: String): TransactionUi =
        TransactionUi(
            id = id, type = type, montant = 10.0,
            date = Date.from(LocalDate.parse(date).atStartOfDay(zone).toInstant()),
            tiersUtiliser = tiers, nomEnveloppe = "Essence", note = null,
            compteId = "chq", collectionCompte = "comptes_cheques"
        )

    @Test fun unTypeEtUnMoisSeCombinentAvecLaRecherche() {
        val lignes = listOf(
            tx("a", "2026-10-01", TypeTransaction.Depense, "Irving 0432"),
            tx("b", "2026-10-15", TypeTransaction.Revenu, "Salaire"),
            tx("c", "2026-11-02", TypeTransaction.Depense, "Irving 0432"),
            tx("d", "2026-10-20", TypeTransaction.Depense, "Marché")
        )
        val filtre = PeriodeHistorique(VuePeriodeHistorique.MOIS, 2026, 10)
        assertEquals(
            listOf("a"),
            FiltrageHistoriqueCompte.appliquer(
                lignes, "irv 0432", filtre, TypeTransaction.Depense
            ).map { it.id }
        )
        assertEquals(
            listOf("a", "d"),
            FiltrageHistoriqueCompte.appliquer(
                lignes, "", filtre, TypeTransaction.Depense
            ).map { it.id }
        )
    }

    @Test fun filtreToutRetrouveToutesLesOperationsEtConserveLordre() {
        val lignes = listOf(
            tx("b", "2025-10-01", TypeTransaction.Revenu, "Salaire"),
            tx("a", "2026-10-01", TypeTransaction.Depense, "Irving")
        )
        assertEquals(
            lignes, FiltrageHistoriqueCompte.appliquer(
                lignes, "", PeriodeHistorique(VuePeriodeHistorique.TOUTES), null
            )
        )
        assertTrue(
            FiltrageHistoriqueCompte.appliquer(
                lignes, "inconnu",
                PeriodeHistorique(VuePeriodeHistorique.TOUTES), null
            ).isEmpty()
        )
    }
}
