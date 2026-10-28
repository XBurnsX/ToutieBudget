package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.modeles.DetailsInvestissement
import com.xburnsx.toutiebudget.data.services.CoursYahoo
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortefeuilleTest {

    private val celi = DetailsInvestissement(valeurDepart = 500.0, dateDepart = "2026-10-01")

    @Test
    fun `achat avec l'argent non place - la valeur du compte ne change pas`() {
        val d = Portefeuille.acheter(celi, 500.0, "xeqt", 10.0, 30.0, 0.0, "2026-10-10")
        val r = Portefeuille.resume(d, 500.0)
        assertEquals(200.0, r.argentNonPlace)
        assertEquals("XEQT", r.lignes.single().position.symbole)
        assertEquals(300.0, r.lignes.single().position.coutTotal)
        assertEquals(500.0, r.valeurTotale)
        assertEquals(0.0, r.gain)
    }

    @Test
    fun `deuxieme achat - parts additionnees, cout moyen, gain en dollars et en pourcentage`() {
        var d = Portefeuille.acheter(celi, 500.0, "XEQT", 10.0, 30.0, 0.0, "2026-10-10")
        d = Portefeuille.acheter(d, 500.0, "XEQT", 2.0, 33.0, 0.0, "2026-10-14")
        val r = Portefeuille.resume(d, 500.0)
        val l = r.lignes.single()
        assertEquals(12.0, l.position.quantite); assertEquals(366.0, l.position.coutTotal)
        assertEquals(30.5, l.coutMoyen)
        assertEquals(33.0, l.position.cours, "le prix le plus récent")
        assertEquals(396.0, l.valeur); assertEquals(30.0, l.gain)
        assertEquals(134.0, r.argentNonPlace); assertEquals(530.0, r.valeurTotale)
        assertEquals(30.0 / 366.0 * 100.0, r.pourcentage!!, 0.0001)
    }

    @Test
    fun `achat plus gros que l'argent non place - refuse avec une explication`() {
        val e = assertFailsWith<IllegalArgumentException> {
            Portefeuille.acheter(celi, 500.0, "XEQT", 30.2, 34.5, 0.0, "2026-10-10")
        }
        assertTrue(e.message!!.contains("non placé"))
    }

    @Test
    fun `titre deja detenu avant le suivi - s'ajoute a la valeur et au capital, pas a l'argent`() {
        val d = Portefeuille.acheter(celi, 500.0, "XEQT", 30.2, 34.5, 0.0, "2026-10-10", payeAvecArgentNonPlace = false)
        val r = Portefeuille.resume(d, 500.0)
        assertEquals(500.0, r.argentNonPlace)
        assertEquals(1541.9, d.valeurDepart)
        assertEquals(1541.9, r.valeurTotale)
    }

    @Test
    fun `vente - gain realise au cout moyen, argent rendu au compte`() {
        var d = Portefeuille.acheter(celi, 500.0, "XEQT", 10.0, 30.0, 0.0, "2026-10-10")
        d = Portefeuille.acheter(d, 500.0, "XEQT", 2.0, 33.0, 0.0, "2026-10-14")
        d = Portefeuille.vendre(d, 530.0, "XEQT", 5.0, 35.0, 0.0, "2026-10-20")
        val vente = d.mouvements.last()
        assertEquals(175.0, vente.montant); assertEquals(22.5, vente.gainRealise)
        val p = d.positions.single()
        assertEquals(7.0, p.quantite); assertEquals(213.5, p.coutTotal)
        assertEquals(309.0, d.encaisse)
        assertFailsWith<IllegalArgumentException> { Portefeuille.vendre(d, 0.0, "XEQT", 8.0, 35.0, 0.0, "2026-10-21") }
    }

    @Test
    fun `titre en dollars US - total seulement avec un taux connu`() {
        val sansTaux = assertFailsWith<IllegalStateException> {
            Portefeuille.acheter(celi, 500.0, "AAPL", 1.0, 200.0, 0.0, "2026-10-10", devise = "USD")
        }
        assertTrue(sansTaux.message!!.contains("Actualiser"))

        val avecTaux = celi.copy(tauxVersCad = mapOf("USD" to 1.4))
        val d = Portefeuille.acheter(avecTaux, 500.0, "AAPL", 1.0, 200.0, 0.0, "2026-10-10", devise = "USD")
        val r = Portefeuille.resume(d, 500.0)
        assertEquals(220.0, r.argentNonPlace) // 500 - 200 USD x 1,4
        assertEquals(500.0, r.valeurTotale)
        assertNull(Portefeuille.resume(d.copy(tauxVersCad = null), 500.0).valeurTotale, "sans taux, aucun total inventé")
    }

    @Test
    fun `lecture d'une reponse de cours Yahoo`() {
        val json = """{"chart":{"result":[{"meta":{"currency":"CAD","symbol":"XEQT.TO","regularMarketPrice":34.87}}],"error":null}}"""
        val c = CoursYahoo.analyser("XEQT.TO", json)
        assertEquals("XEQT.TO", c.symboleCotation); assertEquals(34.87, c.prix); assertEquals("CAD", c.devise)
        assertFailsWith<IllegalStateException> {
            CoursYahoo.analyser("ZZZZ", """{"chart":{"result":null,"error":{"code":"Not Found"}}}""")
        }
    }
}
