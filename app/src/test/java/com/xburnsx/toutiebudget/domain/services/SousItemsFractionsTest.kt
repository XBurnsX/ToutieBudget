package com.xburnsx.toutiebudget.domain.services

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** AT9 : un seul schéma de fractions, lecture des anciennes données. */
class SousItemsFractionsTest {

    @Test
    fun `ecrire puis relire`() {
        val fractions = listOf(
            SousItemsFractions.Fraction("env1", 35.50, "alloc1", "Épicerie", "lait"),
            SousItemsFractions.Fraction("env2", 24.50, "alloc2", "Essence", null)
        )
        assertEquals(fractions, SousItemsFractions.lire(SousItemsFractions.ecrire(fractions)))
    }

    @Test
    fun `ancienne cle enveloppe_id`() {
        val ancien = """[{"enveloppe_id":"env9","montant":12.0,"allocation_mensuelle_id":"a9"}]"""
        val lu = SousItemsFractions.lire(ancien).single()
        assertEquals("env9", lu.enveloppeId)
        assertEquals(12.0, lu.montant)
        assertEquals("a9", lu.allocationMensuelleId)
    }

    @Test
    fun `un lien d'operation de paiement n'est pas une liste de fractions`() {
        assertTrue(SousItemsFractions.lire("""{"operation_paiement_id":"x"}""").isEmpty())
        assertTrue(SousItemsFractions.lire(null).isEmpty())
        assertTrue(SousItemsFractions.lire("pas du json").isEmpty())
    }
}
