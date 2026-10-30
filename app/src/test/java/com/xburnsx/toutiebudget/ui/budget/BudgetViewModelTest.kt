package com.xburnsx.toutiebudget.ui.budget

import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.fakes.AppliEnMemoire
import com.xburnsx.toutiebudget.fakes.BanqueEnMemoire
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Correctifs de la page Budget, avec le VRAI BudgetViewModel sur la base en mémoire. */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModelTest {

    private val main = StandardTestDispatcher()
    private lateinit var app: AppliEnMemoire
    private val b get() = app.b

    @Before fun avant() { Dispatchers.setMain(main); app = AppliEnMemoire() }
    @After fun apres() { attendreSansScope(); app.fermer(); Dispatchers.resetMain() }

    private fun TestScope.attendre() = attendreSansScope()
    private fun attendreSansScope() = repeat(25) { main.scheduler.advanceUntilIdle(); Thread.sleep(20) }
    private fun test(bloc: suspend TestScope.() -> Unit) = runTest(main) { bloc() }

    private fun moisDecale(n: Int): Date = Calendar.getInstance().apply { add(Calendar.MONTH, n) }.time
    private fun TestScope.ouvrirBudget(): BudgetViewModel {
        val vm = app.budget(); Thread.sleep(300); attendre()
        return vm
    }
    private fun BudgetViewModel.enveloppe(id: String) =
        uiState.value.categoriesEnveloppes.flatMap { it.enveloppes }.first { it.id == id }

    private fun preparer() {
        b.categorie("cat", "Dépenses")
        b.cheque("bleu", 1000.0, 500.0)
        b.enveloppe("loyer")
    }

    // ===== Un seul mois de référence =====

    @Test
    fun `le mois affiche suit le mois demande`() = test {
        preparer()
        val vm = ouvrirBudget()
        val prochain = moisDecale(1)
        vm.chargerDonneesBudget(prochain); attendre()
        assertTrue(BanqueEnMemoire.memeMois(prochain, vm.uiState.value.moisAffiche))
    }

    @Test
    fun `rafraichir garde le mois consulte au lieu de revenir au mois courant`() = test {
        preparer()
        val vm = ouvrirBudget()
        val prochain = moisDecale(1)
        vm.chargerDonneesBudget(prochain); attendre()
        vm.rafraichirDonnees(); attendre()
        assertTrue(BanqueEnMemoire.memeMois(prochain, vm.uiState.value.moisAffiche))
    }

    // ===== Assignations refusées quand les données ne sont pas fiables =====

    @Test
    fun `assignation refusee pendant un chargement`() = test {
        preparer()
        val vm = ouvrirBudget()
        vm.chargerDonneesBudget(Date()) // le chargement démarre, pas encore fini
        vm.assignerArgentAEnveloppe("loyer", "bleu", 5000)
        assertTrue(vm.uiState.value.erreur.orEmpty().contains("chargement"), vm.uiState.value.erreur)
        attendre()
        assertEquals(500.0, b.cheque("bleu").pretAPlacer)
        assertTrue(b.allocationDe("loyer", Date()).isEmpty())
    }

    @Test
    fun `assignation refusee dans un mois termine`() = test {
        preparer()
        val vm = ouvrirBudget()
        vm.chargerDonneesBudget(moisDecale(-1)); attendre()
        vm.assignerArgentAEnveloppe("loyer", "bleu", 5000); attendre()
        assertTrue(vm.uiState.value.erreur.orEmpty().contains("terminé"), vm.uiState.value.erreur)
        assertEquals(500.0, b.cheque("bleu").pretAPlacer)
        assertTrue(b.allocationDe("loyer", moisDecale(-1)).isEmpty())
    }

    @Test
    fun `repartition refusee dans un mois termine`() = test {
        preparer()
        val vm = ouvrirBudget()
        vm.chargerDonneesBudget(moisDecale(-1)); attendre()
        var resultat: ResultatRepartition? = null
        val ecoute = launch { resultat = vm.resultatRepartition.first() }
        attendre()
        vm.assignerAPlusieursEnveloppes("bleu", mapOf("loyer" to 5000L)); attendre()
        ecoute.cancel()
        assertTrue(resultat?.erreur.orEmpty().contains("terminé"), resultat?.erreur)
        assertEquals(500.0, b.cheque("bleu").pretAPlacer)
    }

    @Test
    fun `assignation acceptee dans le mois courant`() = test {
        preparer()
        val vm = ouvrirBudget()
        vm.assignerArgentAEnveloppe("loyer", "bleu", 5000); attendre()
        assertNull(vm.uiState.value.erreur, vm.uiState.value.erreur)
        assertEquals(450.0, b.cheque("bleu").pretAPlacer)
        assertEquals(50.0, b.allocationDe("loyer", Date()).sumOf { it.solde })
    }

    // ===== Erreur du report de mois : visible et persistante =====

    @Test
    fun `un report de mois en echec reste affiche apres le chargement`() = test {
        preparer()
        b.allocation("loyer", moisDecale(-1), solde = 40.0, provenance = "bleu")
        app.autres.dernierRollover = moisDecale(-1) // le report du mois courant n'est pas fait
        b.echouerSur = "recupererAllocationsPourMois#1" // la lecture du report échoue
        val vm = ouvrirBudget()
        val alerte = vm.uiState.value.alertePersistante
        assertNotNull(alerte)
        assertTrue(alerte.contains("report"), alerte)
        assertEquals(false, vm.uiState.value.chargementEchoue)
        vm.rafraichirDonnees(); attendre()
        assertNotNull(vm.uiState.value.alertePersistante) // un rechargement ne l'efface pas
        vm.fermerAlertePersistante()
        assertNull(vm.uiState.value.alertePersistante)
    }

    // ===== Couleur de provenance =====

    @Test
    fun `la bulle prend la couleur de la ligne qui porte l'argent`() = test {
        preparer()
        b.cheque("rouge", 500.0, 0.0)
        b.comptes["rouge"] = b.cheque("rouge").copy(couleur = "#F44336")
        // Ligne active (bleu) d'abord, puis l'ancienne ligne vidée (rouge) : « la dernière » serait rouge
        b.allocation("loyer", Date(), solde = 50.0, provenance = "bleu")
        b.allocation("loyer", Date(), solde = 0.0, alloue = 30.0, depense = 30.0, provenance = "rouge")
        val vm = ouvrirBudget()
        assertEquals("#2196F3", vm.enveloppe("loyer").couleurProvenance)
        assertEquals("#2196F3", vm.enveloppe("loyer").couleurObjectif)
    }

    // ===== Argent non affiché =====

    @Test
    fun `argent d'une enveloppe archivee signale sur la page`() = test {
        preparer()
        b.enveloppe("vieux")
        b.enveloppes["vieux"] = b.enveloppes.getValue("vieux").copy(estArchive = true)
        b.allocation("vieux", Date(), solde = 25.0, provenance = "bleu")
        val vm = ouvrirBudget()
        assertEquals(25.0, vm.uiState.value.argentNonAffiche)
    }

    @Test
    fun `aucun argent cache - rien de signale`() = test {
        preparer()
        b.allocation("loyer", Date(), solde = 25.0, provenance = "bleu")
        val vm = ouvrirBudget()
        assertEquals(0.0, vm.uiState.value.argentNonAffiche)
    }

    // ===== Montant suggéré affiché sur la page =====

    @Test
    fun `objectif annuel - la page affiche ce qui reste reparti sur les mois restants`() = test {
        preparer()
        val debut = BanqueEnMemoire.premierJour(Date())
        val fin = Calendar.getInstance().apply { time = debut; add(Calendar.YEAR, 1) }.time
        b.enveloppes["loyer"] = b.enveloppes.getValue("loyer").copy(
            typeObjectif = TypeObjectif.Annuel, objectifMontant = 120.0,
            dateDebutObjectif = debut, dateObjectif = fin
        )
        val vm = ouvrirBudget()
        assertEquals(10.0, vm.enveloppe("loyer").versementRecommande)

        // 50 $ d'un coup : rien de plus à suggérer ce mois-ci
        vm.assignerArgentAEnveloppe("loyer", "bleu", 5000); attendre()
        assertEquals(0.0, vm.enveloppe("loyer").versementRecommande)
    }
}
