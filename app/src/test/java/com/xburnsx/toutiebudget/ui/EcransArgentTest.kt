package com.xburnsx.toutiebudget.ui

import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.data.modeles.CompteInvestissement
import com.xburnsx.toutiebudget.data.modeles.PretPersonnel
import com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.modeles.TypeValorisation
import com.xburnsx.toutiebudget.fakes.AppliEnMemoire
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.FractionTransaction
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.budget.StatutObjectif
import com.xburnsx.toutiebudget.ui.virement.ItemVirement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Les VRAIS ViewModels, appelés comme par un appui sur les boutons (scénarios du guide Codex).
 * Le répartiteur « Main » est contrôlé : un double appui arrive AVANT que la première opération tourne,
 * exactement comme deux appuis à 123 ms d'intervalle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EcransArgentTest {

    private val main = StandardTestDispatcher()
    private lateinit var app: AppliEnMemoire
    private val b get() = app.b

    @Before fun avant() { Dispatchers.setMain(main); app = AppliEnMemoire() }
    @After fun apres() { attendreSansScope(); app.fermer(); Dispatchers.resetMain() }

    /**
     * Le code de prod saute sur Dispatchers.IO (vrais fils) puis revient sur Main : on laisse ces
     * allers-retours finir avant de vérifier, sinon le test lit la base trop tôt ou fuit dans le suivant.
     */
    private fun TestScope.attendre() = attendreSansScope()
    private fun attendreSansScope() = repeat(25) { main.scheduler.advanceUntilIdle(); Thread.sleep(20) }

    private fun test(bloc: suspend TestScope.() -> Unit) = runTest(main) { bloc() }

    private fun enveloppeUi(id: String, solde: Double = 0.0) = EnveloppeUi(
        id = id, nom = id, solde = solde, depense = 0.0, alloue = 0.0, alloueCumulatif = 0.0, objectif = 0.0,
        couleurProvenance = null, statutObjectif = StatutObjectif.GRIS
    )

    // ===== Virer argent : le bug trouvé par Codex =====

    @Test
    fun `double appui sur Effectuer le virement - un seul virement`() = test {
        b.cheque("bleu", 1000.0, 790.0)
        b.enveloppe("loyer")
        b.allocation("loyer", java.util.Date(), solde = 55.0, provenance = "bleu")
        val vm = app.virer(); attendre()

        vm.onEnveloppeSelected(enveloppeUi("pret_a_placer_bleu", 790.0), isSource = true)
        vm.onEnveloppeSelected(enveloppeUi("loyer", 55.0), isSource = false)
        vm.onMontantChange("500")
        vm.onVirementExecute()
        vm.onVirementExecute() // 2e appui avant que le 1er ait tourné
        attendre()

        assertEquals(785.0, b.cheque("bleu").pretAPlacer)
        assertEquals(60.0, b.allocationDe("loyer", java.util.Date()).sumOf { it.solde })
    }

    // ===== Ajouter transaction =====

    private suspend fun TestScope.formulaire() = app.ajoutTransaction().also { attendre() }

    @Test
    fun `depense manuelle du guide - Irving 5 dollars`() = test {
        b.cheque("chq", 1000.0, 0.0)
        b.enveloppe("depanneur")
        b.allocation("depanneur", java.util.Date(), solde = 100.0, provenance = "chq")
        val vm = formulaire()
        vm.onCompteChanged(b.comptes["chq"]); vm.onTiersChanged("Irving"); vm.onMontantChanged("500")
        vm.onEnveloppeChanged(enveloppeUi("depanneur", 100.0))
        vm.sauvegarderTransaction(); attendre()

        assertEquals(995.0, b.soldeDe("chq"))
        assertEquals(95.0, b.allocationDe("depanneur", java.util.Date()).sumOf { it.solde })
        assertEquals(1, b.transactions.size)
    }

    @Test
    fun `double appui sur Enregistrer - une seule depense`() = test {
        b.cheque("chq", 1000.0, 0.0)
        b.enveloppe("depanneur")
        b.allocation("depanneur", java.util.Date(), solde = 100.0, provenance = "chq")
        val vm = formulaire()
        vm.onCompteChanged(b.comptes["chq"]); vm.onTiersChanged("Irving"); vm.onMontantChanged("500")
        vm.onEnveloppeChanged(enveloppeUi("depanneur", 100.0))
        vm.sauvegarderTransaction(); vm.sauvegarderTransaction(); attendre()
        assertEquals(1, b.transactions.size)
        assertEquals(995.0, b.soldeDe("chq"))
    }

    @Test
    fun `revenu - compte et pret a placer`() = test {
        b.cheque("chq", 1000.0, 0.0)
        val vm = formulaire()
        vm.onTypeTransactionChanged(TypeTransaction.Revenu)
        vm.onCompteChanged(b.comptes["chq"]); vm.onTiersChanged("Paie"); vm.onMontantChanged("10000")
        vm.sauvegarderTransaction(); attendre()
        assertEquals(1100.0, b.soldeDe("chq")); assertEquals(100.0, b.cheque("chq").pretAPlacer)
    }

    @Test
    fun `emprunt de 200 dollars - plus 200, jamais plus 400`() = test {
        b.cheque("chq", 1000.0, 0.0)
        val vm = formulaire()
        vm.onModeOperationChanged("Emprunt"); vm.onTypeDetteChanged("Dette contractée")
        vm.onCompteChanged(b.comptes["chq"]); vm.onTiersChanged("Bob"); vm.onMontantChanged("20000")
        vm.sauvegarderTransaction(); attendre()

        assertEquals(1200.0, b.soldeDe("chq")); assertEquals(200.0, b.cheque("chq").pretAPlacer)
        val dossier = app.autres.prets.values.single()
        assertEquals(TypePretPersonnel.DETTE, dossier.type); assertEquals(-200.0, dossier.solde)
        assertEquals(1, b.transactions.size)
    }

    @Test
    fun `remboursement donne de 50 - moins 50, jamais moins 100`() = test {
        b.cheque("chq", 1000.0, 1000.0)
        app.autres.prets["p1"] = PretPersonnel(id = "p1", nomTiers = "Bob", montantInitial = 200.0, solde = -200.0, type = TypePretPersonnel.DETTE, created = "2026-01-01")
        val vm = formulaire()
        vm.onModeOperationChanged("Emprunt"); vm.onTypeDetteChanged("Remboursement donné")
        vm.onCompteChanged(b.comptes["chq"]); vm.onTiersChanged("Bob"); vm.onMontantChanged("5000")
        vm.sauvegarderTransaction(); attendre()
        assertEquals(950.0, b.soldeDe("chq"))
        assertEquals(-150.0, app.autres.prets.getValue("p1").solde)
    }

    @Test
    fun `remboursement de 100 quand il reste 80 - refuse sans rien debiter`() = test {
        b.cheque("chq", 1000.0, 1000.0)
        app.autres.prets["p1"] = PretPersonnel(id = "p1", nomTiers = "Bob", montantInitial = 80.0, solde = -80.0, type = TypePretPersonnel.DETTE)
        val vm = formulaire()
        vm.onModeOperationChanged("Emprunt"); vm.onTypeDetteChanged("Remboursement donné")
        vm.onCompteChanged(b.comptes["chq"]); vm.onTiersChanged("Bob"); vm.onMontantChanged("10000")
        vm.sauvegarderTransaction(); attendre()
        assertEquals(1000.0, b.soldeDe("chq")); assertEquals(-80.0, app.autres.prets.getValue("p1").solde)
        assertTrue(b.transactions.isEmpty())
        assertNotNull(vm.uiState.value.messageErreur)
    }

    @Test
    fun `remboursement recu reparti sur deux pret du plus ancien au plus recent`() = test {
        b.cheque("chq", 1000.0, 0.0)
        app.autres.prets["p1"] = PretPersonnel(id = "p1", nomTiers = "Ana", montantInitial = 100.0, solde = 100.0, type = TypePretPersonnel.PRET, created = "2026-01-01")
        app.autres.prets["p2"] = PretPersonnel(id = "p2", nomTiers = "Ana", montantInitial = 50.0, solde = 50.0, type = TypePretPersonnel.PRET, created = "2026-02-01")
        val vm = formulaire()
        vm.onModeOperationChanged("Prêt"); vm.onTypePretChanged("Remboursement reçu")
        vm.onCompteChanged(b.comptes["chq"]); vm.onTiersChanged("Ana"); vm.onMontantChanged("12000")
        vm.sauvegarderTransaction(); attendre()
        assertEquals(1120.0, b.soldeDe("chq")) // un seul flux d'argent
        assertEquals(0.0, app.autres.prets.getValue("p1").solde); assertTrue(app.autres.prets.getValue("p1").estArchive)
        assertEquals(30.0, app.autres.prets.getValue("p2").solde)
        assertEquals(2, b.transactions.size)
    }

    @Test
    fun `achat fractionne 60 dollars sur deux enveloppes`() = test {
        b.cheque("chq", 1000.0, 0.0)
        b.enveloppe("epicerie"); b.enveloppe("essence")
        b.allocation("epicerie", java.util.Date(), solde = 100.0, provenance = "chq")
        b.allocation("essence", java.util.Date(), solde = 100.0, provenance = "chq")
        val vm = formulaire()
        vm.onCompteChanged(b.comptes["chq"]); vm.onTiersChanged("Costco"); vm.onMontantChanged("6000")
        vm.confirmerFractionnement(listOf(FractionTransaction("f1", 3500.0, "epicerie"), FractionTransaction("f2", 2500.0, "essence")))
        vm.sauvegarderTransaction(); attendre()
        assertEquals(940.0, b.soldeDe("chq"))
        assertEquals(65.0, b.allocationDe("epicerie", java.util.Date()).sumOf { it.solde })
        assertEquals(75.0, b.allocationDe("essence", java.util.Date()).sumOf { it.solde })
        assertEquals(1, b.transactions.size)
    }

    @Test
    fun `fractions perimees - montant change apres le fractionnement, sauvegarde interdite`() = test {
        b.cheque("chq", 1000.0, 0.0)
        b.enveloppe("epicerie"); b.enveloppe("essence")
        val vm = formulaire()
        vm.onCompteChanged(b.comptes["chq"]); vm.onTiersChanged("Costco"); vm.onMontantChanged("6000")
        vm.confirmerFractionnement(listOf(FractionTransaction("f1", 3500.0, "epicerie"), FractionTransaction("f2", 2500.0, "essence")))
        vm.onMontantChanged("5000")
        vm.sauvegarderTransaction(); attendre()
        assertTrue(b.transactions.isEmpty())
        assertEquals(1000.0, b.soldeDe("chq"))
    }

    @Test
    fun `paiement de carte a la date choisie`() = test {
        b.cheque("chq", 1000.0, 0.0)
        b.carte("visa", -400.0, nom = "Visa")
        val vm = formulaire()
        vm.onModeOperationChanged("Paiement")
        vm.onCompteChanged(b.comptes["chq"]); vm.onComptePaiementChanged(b.comptes["visa"]); vm.onMontantChanged("10000")
        vm.onDateChanged(LocalDate.of(2026, 3, 15))
        vm.sauvegarderTransaction(); attendre()
        assertEquals(900.0, b.soldeDe("chq")); assertEquals(-300.0, b.soldeDe("visa"))
        val cal = java.util.Calendar.getInstance()
        assertTrue(b.transactions.values.all { cal.time = it.date; cal.get(java.util.Calendar.MONTH) == java.util.Calendar.MARCH }, b.transactions.values.map { it.date }.toString())
    }

    @Test
    fun `un compte ne peut pas se payer lui-meme`() = test {
        b.carte("visa", -400.0)
        val vm = formulaire()
        vm.onModeOperationChanged("Paiement")
        vm.onCompteChanged(b.comptes["visa"]); vm.onComptePaiementChanged(b.comptes["visa"]); vm.onMontantChanged("1000")
        vm.sauvegarderTransaction(); attendre()
        assertEquals(-400.0, b.soldeDe("visa")); assertTrue(b.transactions.isEmpty())
    }

    // ===== Budget =====

    @Test
    fun `double appui sur Assigner - une seule assignation`() = test {
        b.cheque("bleu", 1000.0, 500.0)
        b.enveloppe("loyer")
        val vm = app.budget(); Thread.sleep(300); attendre()
        vm.assignerArgentAEnveloppe("loyer", "bleu", 5000)
        vm.assignerArgentAEnveloppe("loyer", "bleu", 5000)
        attendre(); Thread.sleep(100); attendre()
        assertEquals(450.0, b.cheque("bleu").pretAPlacer)
        assertEquals(50.0, b.allocationDe("loyer", java.util.Date()).sumOf { it.solde })
    }

    @Test
    fun `repartition vers plusieurs enveloppes depuis un compte`() = test {
        b.cheque("bleu", 1000.0, 500.0)
        b.enveloppe("loyer"); b.enveloppe("epicerie")
        val vm = app.budget(); Thread.sleep(300); attendre()
        vm.assignerAPlusieursEnveloppes("bleu", mapOf("loyer" to 30000L, "epicerie" to 15000L))
        attendre(); Thread.sleep(100); attendre()
        assertEquals(50.0, b.cheque("bleu").pretAPlacer)
        assertEquals(1000.0, b.soldeDe("bleu"))
        assertEquals(300.0, b.allocationDe("loyer", java.util.Date()).sumOf { it.solde })
        assertEquals(150.0, b.allocationDe("epicerie", java.util.Date()).sumOf { it.solde })
    }

    // ===== Comptes =====

    @Test
    fun `reconciliation 50 vers 100 - meme ecart au PAP, aucune fausse transaction`() = test {
        b.cheque("chq", 50.0, 20.0)
        val vm = app.comptes(); attendre()
        vm.onCompteLongPress(b.comptes.getValue("chq"))
        vm.onReconcilierCompte(100.0); vm.onReconcilierCompte(100.0)
        attendre()
        assertEquals(100.0, b.soldeDe("chq")); assertEquals(70.0, b.cheque("chq").pretAPlacer)
        assertTrue(b.transactions.isEmpty())
        assertEquals(1, b.historique.count { it == "RECONCILIATION" })
    }

    @Test
    fun `archiver une carte garde son taux et sa limite`() = test {
        b.carte("visa", -100.0)
        val vm = app.comptes(); attendre()
        vm.onCompteLongPress(b.comptes.getValue("visa")); vm.onArchiverCompte(); attendre()
        val c = b.comptes.getValue("visa") as CompteCredit
        assertTrue(c.estArchive); assertEquals(12.9, c.tauxInteret); assertEquals(7000.0, c.limiteCredit)
    }

    // ===== Carte, dette, placement, prêt =====

    @Test
    fun `carte - le solde signe saisi reste signe`() = test {
        b.carte("visa", -400.0)
        val vm = app.cartes(); attendre()
        vm.afficherDialogModification(b.comptes.getValue("visa") as CompteCredit)
        vm.modifierFormulaire { copy(soldeActuel = "-450") }
        vm.sauvegarderModificationsCarteCredit(); vm.sauvegarderModificationsCarteCredit(); attendre()
        assertEquals(-450.0, b.soldeDe("visa"))
        assertEquals(1, b.historique.count { it == "CORRECTION_SOLDE_CARTE" })
    }

    @Test
    fun `dette - changer le taux apres un paiement garde le vrai solde`() = test {
        b.dette("auto", -1150.0, nom = "Auto")
        val vm = app.dette(); vm.chargerDette("auto"); attendre()
        vm.modifierFormulaire { copy(taux = "5", dureeMois = "24", mode = com.xburnsx.toutiebudget.data.modeles.ModeDette.FINANCE) }
        vm.sauvegarder(); vm.sauvegarder(); attendre()
        val d = b.comptes.getValue("auto") as CompteDette
        assertEquals(-1150.0, d.soldeDette); assertEquals(5.0, d.tauxInteret); assertEquals(24, d.dureeMoisPret)
    }

    @Test
    fun `placement - revalorisation 500 vers 550 sans revenu`() = test {
        b.placement("ws", 500.0)
        val vm = app.placement(); vm.charger("ws"); attendre()
        vm.nouvelleValeur("2026-10-26", 550.0, TypeValorisation.MARCHE, null)
        vm.nouvelleValeur("2026-10-26", 550.0, TypeValorisation.MARCHE, null)
        attendre()
        val c = b.comptes.getValue("ws") as CompteInvestissement
        assertEquals(550.0, c.solde); assertEquals(1, c.details.valorisations.size)
        assertTrue(b.transactions.isEmpty())
    }

    @Test
    fun `pret personnel - archiver ne touche pas au solde`() = test {
        app.autres.prets["p1"] = PretPersonnel(id = "p1", nomTiers = "Ana", montantInitial = 100.0, solde = 60.0, type = TypePretPersonnel.PRET)
        val vm = app.prets(); vm.charger(); attendre()
        vm.chargerHistoriquePourPret("p1", "Ana"); attendre()
        vm.changerArchivage("p1", true); attendre()
        val p = app.autres.prets.getValue("p1")
        assertTrue(p.estArchive); assertEquals(60.0, p.solde)
        assertNull(vm.uiState.value.erreurAction)
    }
}
