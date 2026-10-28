package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeFluxHistoriqueCompteTest {
    private fun ligne(
        id: String, type: TypeTransaction, montant: Double,
        collection: String = "comptes_cheques"
    ) = TransactionUi(
        id = id, type = type, montant = montant,
        date = Date(1000L), tiersUtiliser = "Commerçant",
        nomEnveloppe = null, note = null, collectionCompte = collection
    )

    @Test fun compteUneFoisAchatFractionneEtTousLesCentimes() {
        val achat = ligne("achat", TypeTransaction.Depense, 60.01)
        val resultat = ResumeFluxHistoriqueCompte.calculer(
            listOf(achat, achat, ligne("salaire", TypeTransaction.Revenu, 80.00))
        )
        assertEquals(2, resultat.mouvements)
        assertEquals(6001L, resultat.sortiesCentimes)
        assertEquals(8000L, resultat.entreesCentimes)
        assertEquals(1999L, resultat.netCentimes)
    }

    @Test fun paiementCarteEstEntreeMaisCompteChequeEstSortie() {
        val resume = ResumeFluxHistoriqueCompte.calculer(
            listOf(
                ligne("paiement-carte", TypeTransaction.Paiement, 75.25, "comptes_credits"),
                ligne("paiement-cheque", TypeTransaction.PaiementEffectue, 75.25)
            )
        )
        assertEquals(7525L, resume.entreesCentimes)
        assertEquals(7525L, resume.sortiesCentimes)
        assertEquals(0L, resume.netCentimes)
    }

    @Test fun montantInvalideExcluSansFauxZeroEtAutresFluxConserves() {
        val resume = ResumeFluxHistoriqueCompte.calculer(
            listOf(
                ligne("invalide", TypeTransaction.Depense, Double.NaN),
                ligne("correct", TypeTransaction.Revenu, 12.34),
                ligne("fraction-de-cent", TypeTransaction.Depense, 1.001)
            )
        )
        assertEquals(3, resume.mouvements)
        assertEquals(2, resume.montantsExclus)
        assertEquals(1234L, resume.entreesCentimes)
        assertEquals(0L, resume.sortiesCentimes)
    }
}
