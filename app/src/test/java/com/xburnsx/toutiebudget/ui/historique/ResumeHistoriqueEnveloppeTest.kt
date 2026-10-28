package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeHistoriqueEnveloppeTest {
    private fun ligne(
        id: String, type: TypeTransaction, montant: Double,
        totalBancaire: Double? = null
    ) = TransactionUi(
        id = id, type = type, montant = montant,
        date = Date(1000L), tiersUtiliser = "Marché",
        nomEnveloppe = "Dépanneur", note = null,
        montantTotalTransaction = totalBancaire
    )

    @Test fun achat60Fraction20NeCompteQue20() {
        val resultat = ResumeHistoriqueEnveloppe.calculer(
            listOf(ligne("achat-60", TypeTransaction.Depense, 20.0, totalBancaire = 60.0))
        )
        assertEquals(1, resultat.nombre)
        assertEquals(2000L, resultat.depensesCentimes)
        assertEquals(-2000L, resultat.netCentimes)
    }

    @Test fun plusieursPortionsMemeTransactionNeComptentPasDeuxFois() {
        val transaction = ligne("achat", TypeTransaction.Depense, 19.99, totalBancaire = 60.0)
        val resultat = ResumeHistoriqueEnveloppe.calculer(listOf(transaction, transaction))
        assertEquals(1, resultat.nombre)
        assertEquals(1999L, resultat.depensesCentimes)
    }

    @Test fun sommeExacteAuCentimeAvecRevenu() {
        val resultat = ResumeHistoriqueEnveloppe.calculer(
            listOf(
                ligne("a", TypeTransaction.Depense, 12.49),
                ligne("b", TypeTransaction.Depense, 0.01),
                ligne("c", TypeTransaction.Revenu, 2.00)
            )
        )
        assertEquals(1250L, resultat.depensesCentimes)
        assertEquals(200L, resultat.revenusCentimes)
        assertEquals(-1050L, resultat.netCentimes)
    }

    @Test fun paiementLieNestJamaisConfonduAvecUneDepenseOrdinaire() {
        val resultat = ResumeHistoriqueEnveloppe.calculer(
            listOf(ligne("v", TypeTransaction.TransfertSortant, 500.0))
        )
        assertEquals(0L, resultat.depensesCentimes)
        assertEquals(0L, resultat.revenusCentimes)
        assertEquals(1, resultat.autresOperations)
    }

    @Test(expected = ArithmeticException::class)
    fun refusesMontantAvecFractionDeCentime() {
        ResumeHistoriqueEnveloppe.calculer(
            listOf(ligne("x", TypeTransaction.Depense, 12.345))
        )
    }
}
