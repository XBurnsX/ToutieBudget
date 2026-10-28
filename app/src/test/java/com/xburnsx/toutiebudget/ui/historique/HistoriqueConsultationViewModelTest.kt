package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.fakes.AppliEnMemoire
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests de comportement des VRAIS ViewModels ; aucune mutation financière. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoriqueConsultationViewModelTest {
    private val main = StandardTestDispatcher()
    private lateinit var app: AppliEnMemoire
    private val b get() = app.b

    @Before fun avant() {
        Dispatchers.setMain(main)
        app = AppliEnMemoire()
    }

    @After fun apres() {
        attendre()
        app.fermer()
        Dispatchers.resetMain()
    }

    private fun attendre() = repeat(25) {
        main.scheduler.advanceUntilIdle()
        Thread.sleep(20)
    }

    private fun test(bloc: suspend TestScope.() -> Unit) = runTest(main) { bloc() }

    @Test fun rechercheCompteParTiersNoteMontantEtRetourALaListe() = test {
        b.cheque("chq", 1000.0, 0.0)
        b.transactions["t1"] = Transaction(
            id = "t1", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 12.50, date = Date(), compteId = "chq",
            collectionCompte = "comptes_cheques",
            tiersUtiliser = "Épicerie du Marché", note = "Café"
        )
        b.transactions["t2"] = Transaction(
            id = "t2", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 4.00, date = Date(), compteId = "chq",
            collectionCompte = "comptes_cheques", tiersUtiliser = "Station-service"
        )
        val vm = app.historiqueCompte("chq")
        attendre()
        assertNull(vm.uiState.value.erreur)
        assertEquals(2, vm.uiState.value.transactions.size)

        for (query in listOf("EPICERIE", "marche", "CAFÉ", "12,50 $")) {
            vm.mettreAJourRecherche(query)
            assertEquals(query, listOf("t1"), vm.uiState.value.transactionsGroupees
                .values.flatten().map { it.id })
        }
        vm.mettreAJourRecherche("12,51")
        assertTrue(vm.uiState.value.transactionsGroupees.isEmpty())
        vm.mettreAJourRecherche("")
        assertEquals(2, vm.uiState.value.transactionsGroupees.values.flatten().size)
    }

    @Test fun compteNeCachePasUnAchatSiSesFractionsSontIncompletes() = test {
        b.cheque("chq", 1000.0, 0.0)
        b.transactions["achat"] = Transaction(
            id = "achat", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 7.45, date = Date(), compteId = "chq",
            collectionCompte = "comptes_cheques", tiersUtiliser = "Garage",
            estFractionnee = true,
            sousItems = """[{"enveloppeId":"essence"}]"""
        )
        val vm = app.historiqueCompte("chq")
        attendre()
        assertNull(vm.uiState.value.erreur)
        assertEquals(listOf("achat"), vm.uiState.value.transactions.map { it.id })
        assertEquals(7.45, vm.uiState.value.transactions.single().montant, 0.0000001)
        assertTrue(vm.uiState.value.avertissement.orEmpty().contains("fractions"))
    }

    @Test fun rechercheResteApresActualisationEtLeScrollEstMemorise() = test {
        b.cheque("chq", 1000.0, 0.0)
        val vm = app.historiqueCompte("chq")
        attendre()
        vm.mettreAJourRecherche("Irving")
        vm.sauvegarderPositionScroll(6, 19)
        b.transactions["t1"] = Transaction(
            id = "t1", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 5.00, date = Date(), compteId = "chq",
            collectionCompte = "comptes_cheques", tiersUtiliser = "Irving"
        )
        b.transactions["t2"] = Transaction(
            id = "t2", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 3.00, date = Date(), compteId = "chq",
            collectionCompte = "comptes_cheques", tiersUtiliser = "Marché"
        )
        vm.rechargerTransactions()
        attendre()
        assertNull(vm.uiState.value.erreur)
        assertEquals("Irving", vm.uiState.value.searchQuery)
        assertEquals(listOf("t1"), vm.uiState.value.transactionsGroupees.values.flatten().map { it.id })
        assertEquals(6, vm.uiState.value.scrollPosition)
        assertEquals(19, vm.uiState.value.scrollOffset)
    }

    @Test fun enveloppeSignaleFractionsCorrompuesSansCacherLesAutres() = test {
        b.enveloppe("essence")
        b.transactions["valide"] = Transaction(
            id = "valide", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 3.25, date = Date(), compteId = "chq",
            collectionCompte = "comptes_cheques",
            tiersUtiliser = "Garage", estFractionnee = true,
            sousItems = """[{"enveloppeId":"essence","montant":3.25}]"""
        )
        b.transactions["illisible"] = Transaction(
            id = "illisible", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 1.00, date = Date(), compteId = "chq",
            collectionCompte = "comptes_cheques",
            tiersUtiliser = "Impossible", estFractionnee = true, sousItems = "pas du JSON"
        )
        val vm = app.historiqueEnveloppe("essence")
        attendre()
        assertNull(vm.uiState.value.erreur)
        assertEquals(listOf("valide"), vm.uiState.value.transactions.map { it.id })
        assertTrue(vm.uiState.value.avertissement.orEmpty().contains("illisible"))
    }

    @Test fun allocationOrphelineEstSignaleeSansEffacerLesTransactionsSures() = test {
        b.enveloppe("essence")
        b.transactions["valide"] = Transaction(
            id = "valide", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 5.25, date = Date(), compteId = "chq",
            collectionCompte = "comptes_cheques",
            tiersUtiliser = "Irving", estFractionnee = true,
            sousItems = """[{"enveloppeId":"essence","montant":5.25}]"""
        )
        b.transactions["orphan"] = Transaction(
            id = "orphan", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 2.00, date = Date(), compteId = "chq",
            collectionCompte = "comptes_cheques",
            tiersUtiliser = "Sans allocation", allocationMensuelleId = "allocation-supprimee"
        )
        val vm = app.historiqueEnveloppe("essence")
        attendre()
        assertNull(vm.uiState.value.erreur)
        assertEquals(listOf("valide"), vm.uiState.value.transactions.map { it.id })
        assertTrue(vm.uiState.value.avertissement.orEmpty().contains("allocation"))
    }

    @Test fun enveloppeAfficheSeulementSaPortionDuneDepenseFractionnee() = test {
        b.enveloppe("essence")
        b.enveloppe("epicerie")
        b.transactions["fraction"] = Transaction(
            id = "fraction", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 12.50, date = Date(), compteId = "chq",
            collectionCompte = "comptes_cheques",
            tiersUtiliser = "Épicerie", note = "Visite au dépanneur",
            estFractionnee = true,
            sousItems = """[{"enveloppeId":"essence","montant":5.25},{"enveloppe_id":"epicerie","montant":7.25}]"""
        )
        val vm = app.historiqueEnveloppe("essence")
        attendre()
        assertNull(vm.uiState.value.erreur)
        assertEquals(1, vm.uiState.value.transactions.size)
        assertEquals(5.25, vm.uiState.value.transactions.single().montant, 0.00001)
        vm.mettreAJourRecherche("5,25")
        assertEquals(1, vm.uiState.value.transactionsGroupees.values.flatten().size)
        vm.mettreAJourRecherche("7,25")
        assertTrue(vm.uiState.value.transactionsGroupees.isEmpty())
        vm.mettreAJourRecherche("DEPANNEUR")
        assertEquals(1, vm.uiState.value.transactionsGroupees.values.flatten().size)
    }
}
