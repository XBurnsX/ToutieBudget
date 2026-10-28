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
}
