package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.DetailsCarte
import com.xburnsx.toutiebudget.data.modeles.EmetteurCarte
import com.xburnsx.toutiebudget.data.modeles.ReleveCarte
import com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit.Strategie
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Section 10 : exemples du TXT (Visa Classique Desjardins) et invariants du moteur. */
class MoteurCarteCreditTest {

    private fun carte(
        solde: Double,
        limite: Double = 7000.0,
        taux: Double? = 12.90,
        details: DetailsCarte = DetailsCarte(emetteur = EmetteurCarte.DESJARDINS),
        minimumSaisi: Double? = null
    ) = CompteCredit(
        id = "c1", nom = "Visa", soldeUtilise = solde, couleur = "#2196F3", estArchive = false, ordre = 0,
        limiteCredit = limite, tauxInteret = taux, paiementMinimum = minimumSaisi, detailsCarteJson = details.versJson()
    )

    @Test
    fun `dette et disponible du releve Desjardins`() {
        val c = carte(-4182.87)
        assertEquals(4182.87, MoteurCarteCredit.dette(c))
        assertEquals(2817.13, MoteurCarteCredit.creditDisponible(c))
    }

    @Test
    fun `un solde crediteur n'est jamais une dette`() {
        val c = carte(50.0)
        assertEquals(0.0, MoteurCarteCredit.dette(c))
        assertEquals(7050.0, MoteurCarteCredit.creditDisponible(c))
        assertEquals(0.0, MoteurCarteCredit.tauxUtilisation(c))
    }

    @Test
    fun `depassement de limite visible et non plafonne`() {
        val c = carte(-7500.0)
        assertTrue(MoteurCarteCredit.limiteDepassee(c))
        assertEquals(-500.0, MoteurCarteCredit.creditDisponible(c))
        assertTrue(MoteurCarteCredit.tauxUtilisation(c) > 1.0)
    }

    @Test
    fun `minimum estime 5 pourcent du solde du releve`() {
        // 5 % de 3 672,42 $ = 183,621 $ -> arrondi au cent supérieur
        assertEquals(183.63, MoteurCarteCredit.minimumEstime(carte(-4182.87), 3672.42))
    }

    @Test
    fun `plancher et petit solde`() {
        val c = carte(-5.0, details = DetailsCarte(emetteur = EmetteurCarte.DESJARDINS, plancherMinimum = 10.0))
        assertEquals(5.0, MoteurCarteCredit.minimumEstime(c, 5.0)) // jamais plus que le solde
        assertEquals(10.0, MoteurCarteCredit.minimumEstime(c, 100.0)) // plancher
    }

    @Test
    fun `le minimum officiel du releve passe avant l'estimation`() {
        val releve = ReleveCarte(dateEmission = "2026-09-25", dateEcheance = "2026-10-21", soldeOfficiel = 3672.42, minimumOfficiel = 184.0, interetsFactures = 51.50)
        val c = carte(-4182.87, details = DetailsCarte(emetteur = EmetteurCarte.DESJARDINS, releves = listOf(releve)))
        val m = assertNotNull(MoteurCarteCredit.minimumAPayer(c))
        assertEquals(184.0, m.montant)
        assertTrue(m.officiel)
    }

    @Test
    fun `sans releve ni minimum saisi le minimum est marque estime`() {
        val m = assertNotNull(MoteurCarteCredit.minimumAPayer(carte(-1000.0)))
        assertEquals(50.0, m.montant)
        assertEquals(false, m.officiel)
    }

    @Test
    fun `interets quotidiens`() {
        // 1 000 $ à 12 % pendant 30 jours = 1000 * 0.12 / 365 * 30 = 9,86 $
        assertEquals(9.86, MoteurCarteCredit.arrondi(MoteurCarteCredit.interetsEstimes(1000.0, 12.0, 30)))
        assertEquals(0.0, MoteurCarteCredit.interetsEstimes(-50.0, 12.0, 30))
    }

    @Test
    fun `remboursement a taux zero`() {
        val r = MoteurCarteCredit.simuler(carte(-1000.0, taux = 0.0), Strategie.FIXE, 100.0)
        assertEquals(10, r.nombreMois)
        assertEquals(0.0, r.totalInterets)
        assertEquals(1000.0, r.totalPaye)
    }

    @Test
    fun `versement qui ne couvre pas les interets`() {
        val r = MoteurCarteCredit.simuler(carte(-10000.0, limite = 20000.0, taux = 20.0), Strategie.FIXE, 50.0)
        assertNull(r.nombreMois)
        assertNotNull(r.avertissement)
    }

    @Test
    fun `strategie minimum degressif finit par rembourser`() {
        val r = MoteurCarteCredit.simuler(carte(-2000.0), Strategie.MINIMUM)
        assertNotNull(r.nombreMois)
        assertEquals(0.0, r.lignes.last().soldeFinal)
    }

    @Test
    fun `date cible`() {
        assertEquals(100.0, MoteurCarteCredit.paiementPourDateCible(carte(-1000.0, taux = 0.0), 10))
        val p = assertNotNull(MoteurCarteCredit.paiementPourDateCible(carte(-3000.0), 24))
        assertTrue(MoteurCarteCredit.simuler(carte(-3000.0), Strategie.FIXE, p).nombreMois!! <= 24)
    }

    @Test
    fun `chaque ligne du tableau tombe juste au cent`() {
        val rnd = Random(42)
        repeat(300) {
            val dette = (rnd.nextInt(10_000, 2_000_000)) / 100.0
            val taux = rnd.nextInt(0, 3000) / 100.0
            val c = carte(-dette, limite = 30000.0, taux = taux)
            val strategie = Strategie.entries[rnd.nextInt(Strategie.entries.size)]
            val valeur = if (strategie == Strategie.POURCENTAGE) rnd.nextInt(3, 30).toDouble() else dette / rnd.nextInt(3, 60) + 20
            val r = MoteurCarteCredit.simuler(c, strategie, valeur)
            var precedent = dette
            r.lignes.forEach { l ->
                assertEquals(precedent, l.soldeInitial, "solde initial = solde final précédent")
                assertEquals(MoteurCarteCredit.arrondi(l.soldeInitial + l.interets + l.frais - l.paiement), l.soldeFinal)
                assertTrue(l.soldeFinal >= 0.0)
                precedent = l.soldeFinal
            }
            assertEquals(MoteurCarteCredit.arrondi(r.lignes.sumOf { it.paiement }), r.totalPaye)
        }
    }
}
