package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.modeles.CompteInvestissement
import com.xburnsx.toutiebudget.data.modeles.DetailsInvestissement
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.modeles.TypeValorisation
import com.xburnsx.toutiebudget.data.modeles.Valorisation
import org.junit.Test
import java.util.Calendar
import java.util.Date
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Section 12 : test principal du TXT (chèque 1 000 $ -> Wealthsimple) et règles sans pourcentage inventé. */
class MoteurInvestissementTest {

    private fun jour(decalage: Int) = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, decalage) }.time

    private fun compte(valeur: Double, details: DetailsInvestissement) = CompteInvestissement(
        id = "i1", nom = "Wealthsimple", solde = valeur, couleur = "#4CAF50", estArchive = false, ordre = 0,
        detailsInvestissementJson = details.versJson()
    )

    private fun virement(type: TypeTransaction, montant: Double, date: Date = jour(-100)) =
        Transaction(type = type, montant = montant, date = date, compteId = "i1", collectionCompte = "comptes_investissement")

    @Test
    fun `depot puis revalorisation - capital et gain`() {
        val details = DetailsInvestissement(valeurDepart = 0.0, dateDepart = MoteurCarteCredit.ecrireDate(jour(-200)))
        val tx = listOf(virement(TypeTransaction.TransfertEntrant, 500.0))
        val c = compte(550.0, details)
        assertEquals(500.0, MoteurInvestissement.capitalNetVerse(c, tx))
        assertEquals(50.0, MoteurInvestissement.gainTotal(c, tx))
        // Avec un dépôt, le % simple serait trompeur : il n'est pas affiché
        assertNull(MoteurInvestissement.pourcentageSimple(c, tx))
    }

    @Test
    fun `retrait de 200 dollars`() {
        val details = DetailsInvestissement(valeurDepart = 0.0, dateDepart = "2026-01-01")
        val tx = listOf(virement(TypeTransaction.TransfertEntrant, 500.0), virement(TypeTransaction.TransfertSortant, 200.0))
        val c = compte(350.0, details)
        assertEquals(300.0, MoteurInvestissement.capitalNetVerse(c, tx))
        assertEquals(50.0, MoteurInvestissement.gainTotal(c, tx))
    }

    @Test
    fun `sans valeur de depart rien n'est fabrique`() {
        val c = compte(550.0, DetailsInvestissement())
        assertNull(MoteurInvestissement.capitalNetVerse(c, emptyList()))
        assertNull(MoteurInvestissement.gainTotal(c, emptyList()))
        assertNull(MoteurInvestissement.rendementPondereFlux(c, emptyList()))
    }

    @Test
    fun `un seul solde ne donne aucun rendement`() {
        val c = compte(550.0, DetailsInvestissement(valeurDepart = 550.0, dateDepart = MoteurCarteCredit.ecrireDate(Date())))
        assertNull(MoteurInvestissement.rendementPondereTemps(c, emptyList()))
    }

    @Test
    fun `rendements sur un an sans flux`() {
        val details = DetailsInvestissement(valeurDepart = 1000.0, dateDepart = MoteurCarteCredit.ecrireDate(jour(-365)))
        val c = compte(1100.0, details)
        val twr = assertNotNull(MoteurInvestissement.rendementPondereTemps(c, emptyList()))
        val mwr = assertNotNull(MoteurInvestissement.rendementPondereFlux(c, emptyList()))
        assertTrue(abs(twr - 10.0) < 0.01, "TWR=$twr")
        assertTrue(abs(mwr - 10.0) < 0.1, "MWR=$mwr")
        assertEquals(10.0, MoteurInvestissement.pourcentageSimple(c, emptyList())!!, 0.0001)
    }

    @Test
    fun `rendement pondere dans le temps indisponible avec un depot entre deux valeurs`() {
        // IA10 : 1 000 $ -> dépôt 1 000 $ -> valeur 2 000 $. Sans valeur juste avant le dépôt, un TWR
        // exact est impossible : « indisponible », jamais un chiffre inventé.
        val details = DetailsInvestissement(
            valeurDepart = 1000.0, dateDepart = MoteurCarteCredit.ecrireDate(jour(-60)),
            valorisations = listOf(Valorisation(date = MoteurCarteCredit.ecrireDate(jour(-10)), valeur = 2000.0, type = TypeValorisation.MARCHE))
        )
        val c = compte(2000.0, details)
        assertNull(MoteurInvestissement.rendementPondereTemps(c, listOf(virement(TypeTransaction.TransfertEntrant, 1000.0, jour(-30)))))
    }

    @Test
    fun `les revenus et depenses ne sont pas des apports`() {
        val tx = listOf(
            virement(TypeTransaction.Revenu, 999.0),
            virement(TypeTransaction.Depense, 111.0),
            virement(TypeTransaction.TransfertEntrant, 100.0)
        )
        assertEquals(100.0, MoteurInvestissement.apports(tx))
        assertEquals(0.0, MoteurInvestissement.retraits(tx))
    }
}
