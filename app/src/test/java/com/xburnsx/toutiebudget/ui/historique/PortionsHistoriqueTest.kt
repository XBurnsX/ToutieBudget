package com.xburnsx.toutiebudget.ui.historique

import org.junit.Assert.assertEquals
import org.junit.Test

class PortionsHistoriqueTest {
    @Test fun dollarsConvertisEnCentimesSansTroncature() {
        val f = PortionsHistorique.lire(
            """[{"enveloppeId":"essence","montant":19.99},{"enveloppe_id":"epicerie","montant":0.01}]"""
        )
        assertEquals(1999L, f[0].montantCentimes)
        assertEquals(1L, f[1].montantCentimes)
        assertEquals(19.99, PortionsHistorique.totalPourEnveloppe(f, "essence"), 0.000001)
    }

    @Test fun cumuleDeuxPortionsDuneMemeEnveloppe() {
        val f = PortionsHistorique.lire(
            """[{"enveloppeId":"essence","montant":1.20},{"enveloppeId":"essence","montant":2.35}]"""
        )
        assertEquals(3.55, PortionsHistorique.totalPourEnveloppe(f, "essence"), 0.000001)
    }

    @Test(expected = IllegalStateException::class)
    fun montantAbsentNestJamaisAssimileAZero() {
        PortionsHistorique.lire("""[{"enveloppeId":"essence"}]""")
    }

    @Test(expected = ArithmeticException::class)
    fun refuseFractionDeCentime() {
        PortionsHistorique.lire("""[{"enveloppeId":"essence","montant":1.001}]""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun refuseFractionNegative() {
        PortionsHistorique.lire("""[{"enveloppeId":"essence","montant":-2.00}]""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun refuseUnObjetNonFractionne() {
        PortionsHistorique.lire("""{"pret_personnel_id":"xyz"}""")
    }
}
