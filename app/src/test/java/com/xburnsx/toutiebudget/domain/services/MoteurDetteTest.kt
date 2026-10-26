package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.modeles.AjustementDette
import com.xburnsx.toutiebudget.data.modeles.ChangementTaux
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.data.modeles.DetailsDette
import com.xburnsx.toutiebudget.data.modeles.ModeDette
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeAjustementDette
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import org.junit.Test
import java.util.Calendar
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Section 11 : l'exemple du TXT (1 200 $ à 0 % sur 12 mois) et les règles sans valeur inventée. */
class MoteurDetteTest {

    private fun dette(
        solde: Double,
        initial: Double = 1200.0,
        taux: Double? = 0.0,
        duree: Int? = 12,
        versement: Double? = null,
        details: DetailsDette = DetailsDette(mode = ModeDette.FINANCE)
    ) = CompteDette(
        id = "d1", nom = "Auto", soldeDette = solde, estArchive = false, ordre = 0, montantInitial = initial,
        tauxInteret = taux, paiementMinimum = versement, dureeMoisPret = duree, detailsDetteJson = details.versJson()
    )

    private fun paiement(montant: Double, joursAvant: Int = 0) = Transaction(
        type = TypeTransaction.Paiement, montant = montant, compteId = "d1", collectionCompte = "comptes_dettes",
        date = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -joursAvant) }.time
    )

    @Test
    fun `exemple du TXT - payer 50 dollars laisse 1150`() {
        val d = dette(solde = -1150.0)
        assertEquals(1150.0, MoteurDette.restant(d))
        assertEquals(100.0, MoteurDette.versementPrevu(d))
        // L'échéancier part du solde RÉEL : 1 150 $ à 100 $/mois = 12 versements (le dernier de 50 $)
        val e = assertNotNull(MoteurDette.echeancier(d))
        assertEquals(12, e.nombrePeriodes)
        assertEquals(50.0, e.periodes.last().versement)
        assertEquals(0.0, e.totalInterets)
    }

    @Test
    fun `changer le taux ou la duree ne touche pas le solde reel`() {
        val avant = dette(solde = -1150.0)
        val apres = avant.copy(tauxInteret = 5.0, dureeMoisPret = 24)
        assertEquals(MoteurDette.restant(avant), MoteurDette.restant(apres))
    }

    @Test
    fun `mensualite d'amortissement`() {
        // 10 000 $ à 6 % sur 60 mois = 193,33 $
        assertEquals(193.33, MoteurDette.mensualiteContractuelle(10000.0, 6.0, 60))
        assertEquals(100.0, MoteurDette.mensualiteContractuelle(1200.0, 0.0, 12))
    }

    @Test
    fun `aucun taux ni duree inventes`() {
        assertNull(MoteurDette.mensualiteContractuelle(1200.0, null, 12))
        assertNull(MoteurDette.mensualiteContractuelle(1200.0, 5.0, null))
        assertNull(MoteurDette.mensualiteContractuelle(1200.0, -1.0, 12))
        val simple = dette(solde = -500.0, taux = null, duree = null, details = DetailsDette(mode = ModeDette.SIMPLE))
        assertNull(MoteurDette.versementPrevu(simple))
        assertNull(MoteurDette.echeancier(simple))
    }

    @Test
    fun `une dette soldee ou creditrice ne doit rien`() {
        assertEquals(0.0, MoteurDette.restant(dette(solde = 0.0)))
        assertEquals(0.0, MoteurDette.restant(dette(solde = 25.0)))
    }

    @Test
    fun `echeances couvertes selon les montants et non les clics`() {
        val d = dette(solde = -1100.0)
        assertEquals(1, MoteurDette.echeancesCouvertes(d, listOf(paiement(50.0), paiement(50.0))))
        assertEquals(0, MoteurDette.echeancesCouvertes(d, listOf(paiement(50.0))))
        assertEquals(100.0, MoteurDette.totalVerse(listOf(paiement(50.0), paiement(50.0))))
    }

    @Test
    fun `versement supplementaire raccourcit et evite des interets`() {
        val d = dette(solde = -10000.0, initial = 10000.0, taux = 6.0, duree = 60)
        val normal = assertNotNull(MoteurDette.echeancier(d))
        val accelere = assertNotNull(MoteurDette.echeancier(d, versementSupplementaire = 100.0))
        assertTrue(accelere.nombrePeriodes!! < normal.nombrePeriodes!!)
        assertTrue(accelere.totalInterets < normal.totalInterets)
    }

    @Test
    fun `taux variable applique a partir de sa date`() {
        val details = DetailsDette(
            mode = ModeDette.FINANCE,
            tauxVariable = true,
            changementsTaux = listOf(ChangementTaux(date = "2020-01-01", taux = 9.0))
        )
        val d = dette(solde = -1000.0, taux = 3.0, details = details)
        val avant = Calendar.getInstance().apply { set(2019, Calendar.JUNE, 1) }.time
        assertEquals(3.0, MoteurDette.tauxA(d, avant))
        assertEquals(9.0, MoteurDette.tauxA(d, Date()))
    }

    @Test
    fun `interets ajoutes confirmes`() {
        val details = DetailsDette(
            ajustements = listOf(
                AjustementDette(date = "2026-10-01", type = TypeAjustementDette.INTERETS, soldeAvant = -1000.0, soldeApres = -1012.5),
                AjustementDette(date = "2026-10-02", type = TypeAjustementDette.SOLDE_OFFICIEL, soldeAvant = -1012.5, soldeApres = -990.0)
            )
        )
        assertEquals(12.5, MoteurDette.interetsAjoutes(dette(solde = -990.0, details = details)))
    }

    @Test
    fun `chaque periode tombe juste au cent`() {
        listOf(0.0, 3.5, 12.0, 24.99).forEach { taux ->
            val e = assertNotNull(MoteurDette.echeancier(dette(solde = -4321.09, initial = 5000.0, taux = taux, duree = 36)))
            e.periodes.forEach { p ->
                assertEquals(MoteurDette.arrondi(p.soldeInitial + p.interets - p.versement), p.soldeFinal)
                assertEquals(MoteurDette.arrondi(p.versement - p.interets), p.capital)
            }
            assertEquals(0.0, e.periodes.last().soldeFinal)
        }
    }
}
