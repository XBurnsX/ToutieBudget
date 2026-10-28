package com.xburnsx.toutiebudget.ui.historique

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadonneesFractionsHistoriqueTest {
    @Test fun notesLisiblesConserveesDansLeMemeOrdreQueLesFractions() {
        val json = """[
            {"enveloppeId":"essence","montant":20.00,"description":"Essence","note":"Pompe 3"},
            {"enveloppeId":"cafe","montant":40.00,"description":"Café","note":"Facture"}
        ]"""
        val portions = PortionsHistorique.lire(json)
        val infos = MetadonneesFractionsHistorique.lire(json, portions.size)

        assertTrue(PortionsHistorique.verifierTotal(portions, 60.0))
        assertEquals(listOf("Pompe 3", "Facture"), infos.map { it.note })
        assertEquals(listOf("Essence", "Café"), infos.map { it.description })
    }

    @Test fun noteNonTextuelleNInvalidePasLaPortionNiLesAutresNotes() {
        val json = """[
            {"enveloppeId":"essence","montant":20.00,"description":{"invalide":true},"note":[1,2]},
            {"enveloppeId":"cafe","montant":40.00,"description":"Café","note":"Document reçu"}
        ]"""
        val portions = PortionsHistorique.lire(json)
        val infos = MetadonneesFractionsHistorique.lire(json, portions.size)

        assertEquals(2, portions.size)
        assertTrue(PortionsHistorique.verifierTotal(portions, 60.0))
        assertEquals("", infos[0].description)
        assertEquals("", infos[0].note)
        assertEquals("Café", infos[1].description)
        assertEquals("Document reçu", infos[1].note)
    }

    @Test fun champOptionnelManquantNestJamaisInvente() {
        val infos = MetadonneesFractionsHistorique.lire(
            """[{"enveloppe_id":"auto","montant":9.99}]""", 1
        )
        assertEquals("", infos.single().description)
        assertEquals("", infos.single().note)
    }
}
