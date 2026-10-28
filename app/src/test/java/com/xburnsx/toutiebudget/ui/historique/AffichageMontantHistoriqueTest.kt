package com.xburnsx.toutiebudget.ui.historique

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AffichageMontantHistoriqueTest {
    @Test fun montantHistoriqueNonFiniNestPasRemplaceParZero() {
        for (valeur in listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)) {
            val affichage = AffichageMontantHistorique.formater(valeur)
            assertTrue(affichage.contains("invalide"))
            assertFalse(affichage.contains("0,00"))
        }
    }

    @Test fun montantReelConserveSonFormatFrancais() {
        val affichage = AffichageMontantHistorique.formater(12.50)
        assertTrue(affichage.contains("12"))
        assertTrue(affichage.contains("50"))
    }
}
