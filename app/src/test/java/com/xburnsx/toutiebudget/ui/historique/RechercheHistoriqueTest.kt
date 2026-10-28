package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RechercheHistoriqueTest {
    private val achat = TransactionUi(
        id = "tx-1", type = TypeTransaction.Depense, montant = 12.50,
        date = Date(1_000L),
        tiersUtiliser = "Épicerie du Marché",
        nomEnveloppe = "Alimentation",
        note = "Café et lait",
        estFractionnee = true,
        nomsEnveloppesFractions = listOf("Maison", "Épicerie")
    )

    @Test fun accentsEtMajusculesNeBloquerontPasLeMarchand() {
        assertTrue(RechercheHistorique.correspond(achat, "EPICERIE"))
        assertTrue(RechercheHistorique.correspond(achat, "marche"))
        assertTrue(RechercheHistorique.correspond(achat, "CAFE"))
    }

    @Test fun dollarsEtVirgulesAuCentPres() {
        assertTrue(RechercheHistorique.correspond(achat, "12,50"))
        assertTrue(RechercheHistorique.correspond(achat, "12.50 $"))
        assertFalse(RechercheHistorique.correspond(achat, "12,51"))
    }

    @Test fun fractionsEtCategoriesSontRecherchables() {
        assertTrue(RechercheHistorique.correspond(achat, "maison"))
        assertTrue(RechercheHistorique.correspond(achat, "alimentation"))
        assertFalse(RechercheHistorique.correspond(achat, "garage"))
    }

    @Test fun rechercheVideConserveOrdreEtTransactions() {
        assertEquals(listOf(achat), RechercheHistorique.filtrer(listOf(achat), " "))
    }

    @Test fun aucuneTransactionNeChangeApresLaRecherche() {
        RechercheHistorique.filtrer(listOf(achat), "12,50")
        assertEquals(12.50, achat.montant, 0.00000001)
        assertEquals("Épicerie du Marché", achat.tiersUtiliser)
    }
    @Test fun aliasIrvingEtIrvAvecNumeroDeStationSontEquivalents() {
        val irv = achat.copy(tiersUtiliser = "IRV-0421", montant = 51.30)
        val irving = achat.copy(tiersUtiliser = "Irving #0421", montant = 51.30)
        assertTrue(RechercheHistorique.correspond(irv, "Irving 0421"))
        assertTrue(RechercheHistorique.correspond(irving, "IRV 0421"))
        assertTrue(RechercheHistorique.correspond(irv, "irving"))
        assertFalse(RechercheHistorique.correspond(irv, "IRV 9999"))
    }

    @Test fun milliersFrancaisAvecEspacesInsecablesSontDesMontantsExacts() {
        val achatImportant = achat.copy(montant = 1234.56)
        assertTrue(RechercheHistorique.correspond(achatImportant, "1 234,56 $"))
        assertTrue(RechercheHistorique.correspond(achatImportant, "1\u00A0234,56 $"))
        assertTrue(RechercheHistorique.correspond(achatImportant, "1\u202F234,56 $"))
        assertFalse(RechercheHistorique.correspond(achatImportant, "1\u202F234,57 $"))
    }

}
