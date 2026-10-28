package com.xburnsx.toutiebudget.domain

import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.domain.usecases.SupprimerTransactionUseCase
import com.xburnsx.toutiebudget.fakes.BanqueEnMemoire
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * HC1/HC6 : la version affichée est vérifiée une seconde fois dans la même
 * opération atomique que l'annulation bancaire. Aucun effet en cas de conflit.
 */
class HistoriqueSuppressionConflitTest {
    private fun banqueEtSuppression(): Pair<BanqueEnMemoire, SupprimerTransactionUseCase> {
        val b = BanqueEnMemoire()
        b.cheque("cheque", 990.0, 990.0)
        return b to SupprimerTransactionUseCase(
            b.depotTransactions, b.depotComptes, b.depotEnveloppes,
            b.depotAllocations, operationAtomique = b.transaction()
        )
    }

    private fun achat() = Transaction(
        id = "achat", utilisateurId = "u",
        type = TypeTransaction.Depense, montant = 10.0,
        date = Date(1_790_000_000_000L),
        compteId = "cheque", collectionCompte = "comptes_cheques",
        tiersUtiliser = "Dépanneur", note = "Pain"
    )

    @Test fun refuseModificationApresConfirmationEtNeToucheAuxSoldes() = runTest {
        val (b, supprimer) = banqueEtSuppression()
        val confirme = achat()
        b.transactions[confirme.id] = confirme.copy(note = "Lait")
        val avant = b.soldeDe("cheque")
        val pap = b.cheque("cheque").pretAPlacer

        val resultat = supprimer.executer("achat", transactionAttendue = confirme)

        assertTrue(resultat.isFailure)
        assertTrue(resultat.exceptionOrNull()?.message.orEmpty().contains("modifiée"))
        assertEquals(avant, b.soldeDe("cheque"), 0.000001)
        assertEquals(pap, b.cheque("cheque").pretAPlacer, 0.000001)
        assertEquals("Lait", b.transactions.getValue("achat").note)
    }

    @Test fun refuseChangementDeMontantPendantLaConfirmation() = runTest {
        val (b, supprimer) = banqueEtSuppression()
        val confirme = achat()
        b.transactions[confirme.id] = confirme.copy(montant = 25.0)
        val resultat = supprimer.executer("achat", transactionAttendue = confirme)
        assertTrue(resultat.isFailure)
        assertEquals(990.0, b.soldeDe("cheque"), 0.000001)
        assertEquals(25.0, b.transactions.getValue("achat").montant, 0.000001)
    }

    @Test fun versionIdentiquePeutEtreSupprimeeUneSeuleFois() = runTest {
        val (b, supprimer) = banqueEtSuppression()
        val confirme = achat()
        b.transactions[confirme.id] = confirme

        assertTrue(supprimer.executer("achat", transactionAttendue = confirme).isSuccess)
        val soldeApres = b.soldeDe("cheque")
        assertFalse(b.transactions.containsKey("achat"))
        assertTrue(supprimer.executer("achat", transactionAttendue = confirme).isFailure)
        assertEquals(soldeApres, b.soldeDe("cheque"), 0.000001)
    }
    @Test fun profilDifferentAuMomentDuCommitRefuseSuppression() = runTest {
        val (b, _) = banqueEtSuppression()
        val confirme = achat()
        b.transactions[confirme.id] = confirme
        val depotSansProfil = object : TransactionRepository by b.depotTransactions {
            override suspend fun recupererTransactionDeLaSession(
                transactionId: String
            ): Result<Transaction> =
                Result.failure(SecurityException("Transaction hors session"))
        }
        val supprimer = SupprimerTransactionUseCase(
            depotSansProfil, b.depotComptes, b.depotEnveloppes,
            b.depotAllocations, operationAtomique = b.transaction()
        )

        val resultat = supprimer.executer("achat", transactionAttendue = confirme)

        assertTrue(resultat.isFailure)
        assertEquals(990.0, b.soldeDe("cheque"), 0.000001)
        assertTrue(b.transactions.containsKey("achat"))
    }

}
