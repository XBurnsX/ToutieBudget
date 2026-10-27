package com.xburnsx.toutiebudget.ui

import com.xburnsx.toutiebudget.data.modeles.PretPersonnel
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.fakes.AppliEnMemoire
import com.xburnsx.toutiebudget.fakes.BanqueEnMemoire
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.FractionTransaction
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.budget.StatutObjectif
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Modifier, Supprimer (historique compte et enveloppe), Catégories/enveloppes et rollover,
 * par les VRAIS ViewModels. Les règles viennent des cas de vérification MT, HC, HE, CE et GN3 de la roadmap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EcransModifierSupprimerTest {

    private val main = StandardTestDispatcher()
    private lateinit var app: AppliEnMemoire
    private val b get() = app.b
    private val maintenant = Date()

    @Before fun avant() { Dispatchers.setMain(main); app = AppliEnMemoire() }
    @After fun apres() { attendreSansScope(); app.fermer(); Dispatchers.resetMain() }

    private fun test(bloc: suspend TestScope.() -> Unit) = runTest(main) { bloc() }

    /** Le code de prod passe par Dispatchers.IO : on laisse les allers-retours finir avant de vérifier. */
    private fun TestScope.attendre() = attendreSansScope()
    private fun attendreSansScope() = repeat(25) { main.scheduler.advanceUntilIdle(); Thread.sleep(20) }

    private fun enveloppeUi(id: String, solde: Double = 0.0) = EnveloppeUi(
        id = id, nom = id, solde = solde, depense = 0.0, alloue = 0.0, alloueCumulatif = 0.0, objectif = 0.0,
        couleurProvenance = null, statutObjectif = StatutObjectif.GRIS
    )

    private fun moisPrecedent(): Date = Calendar.getInstance().apply { add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 15) }.time
    private fun Date.enLocalDate(): LocalDate = toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    private fun soldeEnveloppe(id: String, mois: Date = maintenant) = b.allocationDe(id, mois).sumOf { it.solde }

    /** Chèque 1000 $ et une enveloppe Dépanneur de 100 $ venant de ce compte. */
    private fun guide() {
        b.cheque("chq", 1000.0, 0.0)
        b.enveloppe("depanneur")
        b.allocation("depanneur", maintenant, solde = 100.0, provenance = "chq")
    }

    /** Ajoute une dépense par l'écran Ajouter transaction et rend son ID. */
    private suspend fun TestScope.depense(cents: String, enveloppe: String = "depanneur", date: LocalDate? = null): String {
        val avant = b.transactions.keys.toSet()
        val vm = app.ajoutTransaction().also { attendre() }
        vm.onCompteChanged(b.comptes["chq"]); vm.onTiersChanged("Irving"); vm.onMontantChanged(cents)
        vm.onEnveloppeChanged(enveloppeUi(enveloppe, soldeEnveloppe(enveloppe)))
        date?.let { vm.onDateChanged(it) }
        vm.sauvegarderTransaction(); attendre()
        return (b.transactions.keys - avant).single()
    }

    private suspend fun TestScope.revenu(cents: String): String {
        val avant = b.transactions.keys.toSet()
        val vm = app.ajoutTransaction().also { attendre() }
        vm.onTypeTransactionChanged(TypeTransaction.Revenu)
        vm.onCompteChanged(b.comptes["chq"]); vm.onTiersChanged("Paie"); vm.onMontantChanged(cents)
        vm.sauvegarderTransaction(); attendre()
        return (b.transactions.keys - avant).single()
    }

    private suspend fun TestScope.ouvrirModifier(id: String) = app.modifier().also { it.chargerTransaction(id); attendre() }

    // ===== Modifier transaction =====

    @Test
    fun `modifier Irving 5 a 6 dollars - moins 1 net, meme transaction`() = test {
        guide()
        val id = depense("500")
        val vm = ouvrirModifier(id)
        vm.onMontantChanged("600"); vm.modifierTransaction(); attendre()

        assertNull(vm.uiState.value.messageErreur)
        assertEquals(994.0, b.soldeDe("chq")); assertEquals(94.0, soldeEnveloppe("depanneur"))
        assertEquals(setOf(id), b.transactions.keys) // MT1 : même ID, aucune copie
        assertEquals(6.0, b.transactions.getValue(id).montant)
    }

    @Test
    fun `modifier seulement la note - aucun dollar ne bouge`() = test {
        guide()
        val id = depense("500")
        val vm = ouvrirModifier(id)
        vm.onNoteChanged("café"); vm.modifierTransaction(); attendre()

        assertEquals(995.0, b.soldeDe("chq")); assertEquals(95.0, soldeEnveloppe("depanneur"))
        assertEquals("café", b.transactions.getValue(id).note)
        assertEquals(1, b.transactions.size)
    }

    @Test
    fun `revenu 500 a 400 - solde et pret a placer baissent chacun de 100`() = test {
        b.cheque("chq", 1000.0, 0.0)
        val id = revenu("50000")
        assertEquals(1500.0, b.soldeDe("chq")); assertEquals(500.0, b.cheque("chq").pretAPlacer)
        val vm = ouvrirModifier(id)
        vm.onMontantChanged("40000"); vm.modifierTransaction(); attendre()

        assertEquals(1400.0, b.soldeDe("chq")); assertEquals(400.0, b.cheque("chq").pretAPlacer)
    }

    @Test
    fun `19,99 dollars enregistre sans changement - toujours 19,99 au cent`() = test {
        guide()
        val id = depense("1999")
        assertEquals(980.01, b.soldeDe("chq"))
        val vm = ouvrirModifier(id)
        assertEquals("1999", vm.uiState.value.montant) // GN5 : pas de troncature à 1998
        vm.modifierTransaction(); attendre()

        assertEquals(980.01, b.soldeDe("chq")); assertEquals(80.01, soldeEnveloppe("depanneur"))
        assertEquals(19.99, b.transactions.values.single().montant)
    }

    @Test
    fun `echec pendant la modification - l'original et ses effets restent`() = test {
        guide()
        val id = depense("500")
        val vm = ouvrirModifier(id)
        b.echouerSur = "creerTransaction#2" // le 1er appel a créé l'original
        vm.onMontantChanged("600"); vm.modifierTransaction(); attendre()

        assertNotNull(vm.uiState.value.messageErreur)
        assertEquals(995.0, b.soldeDe("chq")); assertEquals(95.0, soldeEnveloppe("depanneur"))
        assertEquals(5.0, b.transactions.getValue(id).montant)
        assertEquals(1, b.transactions.size)
    }

    @Test
    fun `double appui sur Modifier - une seule nouvelle version`() = test {
        guide()
        val id = depense("500")
        val vm = ouvrirModifier(id)
        vm.onMontantChanged("600"); vm.modifierTransaction(); vm.modifierTransaction(); attendre()

        assertEquals(994.0, b.soldeDe("chq")); assertEquals(94.0, soldeEnveloppe("depanneur"))
        assertEquals(1, b.transactions.size)
    }

    @Test
    fun `depense du mois passe deplacee a ce mois - ancien mois rendu, nouveau debite`() = test {
        guide()
        b.allocation("depanneur", moisPrecedent(), solde = 100.0, provenance = "chq")
        val id = depense("500", date = moisPrecedent().enLocalDate())
        assertEquals(95.0, soldeEnveloppe("depanneur", moisPrecedent()))
        val vm = ouvrirModifier(id)
        vm.onDateChanged(maintenant.enLocalDate()); vm.modifierTransaction(); attendre()

        assertEquals(100.0, soldeEnveloppe("depanneur", moisPrecedent()))
        assertEquals(95.0, soldeEnveloppe("depanneur"))
        assertEquals(995.0, b.soldeDe("chq"))
    }

    @Test
    fun `un paiement de carte ne se transforme pas en depense - modification refusee`() = test {
        b.cheque("chq", 1000.0, 0.0); b.carte("visa", -400.0, nom = "Visa")
        val ajout = app.ajoutTransaction().also { attendre() }
        ajout.onModeOperationChanged("Paiement")
        ajout.onCompteChanged(b.comptes["chq"]); ajout.onComptePaiementChanged(b.comptes["visa"]); ajout.onMontantChanged("10000")
        ajout.sauvegarderTransaction(); attendre()
        val moitieChq = b.transactions.values.single { it.compteId == "chq" }

        val vm = ouvrirModifier(moitieChq.id)
        vm.onMontantChanged("5000"); vm.modifierTransaction(); attendre()

        assertNotNull(vm.uiState.value.messageErreur)
        assertEquals(900.0, b.soldeDe("chq")); assertEquals(-300.0, b.soldeDe("visa"))
        assertEquals(2, b.transactions.size)
    }

    // ===== Supprimer depuis l'historique =====

    @Test
    fun `supprimer Irving depuis l'historique du compte - compte et enveloppe rendus`() = test {
        guide()
        val id = depense("500")
        val vm = app.historiqueCompte("chq").also { attendre() }
        vm.supprimerTransaction(id); attendre()

        assertEquals(1000.0, b.soldeDe("chq")); assertEquals(100.0, soldeEnveloppe("depanneur"))
        assertTrue(b.transactions.isEmpty())
    }

    @Test
    fun `supprimer un revenu - solde et pret a placer reviennent`() = test {
        b.cheque("chq", 1000.0, 0.0)
        val id = revenu("10000")
        app.historiqueCompte("chq").also { attendre() }.supprimerTransaction(id); attendre()

        assertEquals(1000.0, b.soldeDe("chq")); assertEquals(0.0, b.cheque("chq").pretAPlacer)
    }

    @Test
    fun `supprimer un paiement de carte depuis la carte - les deux cotes annules`() = test {
        b.cheque("chq", 1000.0, 0.0); b.carte("visa", -400.0, nom = "Visa")
        val ajout = app.ajoutTransaction().also { attendre() }
        ajout.onModeOperationChanged("Paiement")
        ajout.onCompteChanged(b.comptes["chq"]); ajout.onComptePaiementChanged(b.comptes["visa"]); ajout.onMontantChanged("10000")
        ajout.sauvegarderTransaction(); attendre()
        val moitieVisa = b.transactions.values.single { it.compteId == "visa" }

        app.historiqueCompte("visa", "comptes_credits").also { attendre() }.supprimerTransaction(moitieVisa.id); attendre()

        assertEquals(1000.0, b.soldeDe("chq")); assertEquals(-400.0, b.soldeDe("visa"))
        assertTrue(b.transactions.isEmpty())
    }

    @Test
    fun `supprimer un achat fractionne depuis une enveloppe - toute la transaction`() = test {
        b.cheque("chq", 1000.0, 0.0)
        b.enveloppe("epicerie"); b.enveloppe("essence")
        b.allocation("epicerie", maintenant, solde = 100.0, provenance = "chq")
        b.allocation("essence", maintenant, solde = 100.0, provenance = "chq")
        val ajout = app.ajoutTransaction().also { attendre() }
        ajout.onCompteChanged(b.comptes["chq"]); ajout.onTiersChanged("Costco"); ajout.onMontantChanged("6000")
        ajout.confirmerFractionnement(listOf(FractionTransaction("f1", 3500.0, "epicerie"), FractionTransaction("f2", 2500.0, "essence")))
        ajout.sauvegarderTransaction(); attendre()
        assertEquals(65.0, soldeEnveloppe("epicerie")); assertEquals(75.0, soldeEnveloppe("essence"))

        val id = b.transactions.keys.single()
        app.historiqueEnveloppe("essence").also { attendre() }.supprimerTransaction(id); attendre()

        assertEquals(1000.0, b.soldeDe("chq"))
        assertEquals(100.0, soldeEnveloppe("epicerie")); assertEquals(100.0, soldeEnveloppe("essence"))
        assertTrue(b.transactions.isEmpty())
    }

    @Test
    fun `double appui sur Supprimer - rembourse une seule fois`() = test {
        guide()
        val id = depense("500")
        val vm = app.historiqueCompte("chq").also { attendre() }
        vm.supprimerTransaction(id); vm.supprimerTransaction(id); attendre()

        assertEquals(1000.0, b.soldeDe("chq")); assertEquals(100.0, soldeEnveloppe("depanneur"))
    }

    @Test
    fun `echec de suppression - rien ne bouge`() = test {
        guide()
        val id = depense("500")
        val vm = app.historiqueCompte("chq").also { attendre() }
        b.echouerSur = "supprimerTransaction"
        vm.supprimerTransaction(id); attendre()

        assertNotNull(vm.uiState.value.erreur)
        assertEquals(995.0, b.soldeDe("chq")); assertEquals(95.0, soldeEnveloppe("depanneur"))
        assertEquals(1, b.transactions.size)
    }

    @Test
    fun `echec en annulant l'enveloppe d'une fraction - rien ne bouge`() = test {
        b.cheque("chq", 1000.0, 0.0)
        b.enveloppe("epicerie"); b.enveloppe("essence")
        b.allocation("epicerie", maintenant, solde = 100.0, provenance = "chq")
        b.allocation("essence", maintenant, solde = 100.0, provenance = "chq")
        val ajout = app.ajoutTransaction().also { attendre() }
        ajout.onCompteChanged(b.comptes["chq"]); ajout.onTiersChanged("Costco"); ajout.onMontantChanged("6000")
        ajout.confirmerFractionnement(listOf(FractionTransaction("f1", 3500.0, "epicerie"), FractionTransaction("f2", 2500.0, "essence")))
        ajout.sauvegarderTransaction(); attendre()
        val id = b.transactions.keys.single()

        val vm = app.historiqueCompte("chq").also { attendre() }
        b.echouerSur = "mettreAJourAllocation" // le remboursement de la 1re fraction échoue
        vm.supprimerTransaction(id); attendre()

        assertNotNull(vm.uiState.value.erreur)
        assertEquals(940.0, b.soldeDe("chq"))
        assertEquals(65.0, soldeEnveloppe("epicerie")); assertEquals(75.0, soldeEnveloppe("essence"))
        assertEquals(1, b.transactions.size)
    }

    @Test
    fun `supprimer un pret accorde - le compte et le dossier du tiers reviennent`() = test {
        b.cheque("chq", 1000.0, 1000.0)
        val ajout = app.ajoutTransaction().also { attendre() }
        ajout.onModeOperationChanged("Prêt"); ajout.onTypePretChanged("Prêt accordé")
        ajout.onCompteChanged(b.comptes["chq"]); ajout.onTiersChanged("Ana"); ajout.onMontantChanged("20000")
        ajout.sauvegarderTransaction(); attendre()
        assertEquals(800.0, b.soldeDe("chq")); assertEquals(800.0, b.cheque("chq").pretAPlacer)

        app.historiqueCompte("chq").also { attendre() }.supprimerTransaction(b.transactions.keys.single()); attendre()

        assertEquals(1000.0, b.soldeDe("chq")); assertEquals(1000.0, b.cheque("chq").pretAPlacer)
        assertTrue(app.autres.prets.values.none { !it.estArchive && abs(it.solde) > 0.005 }, "Ana ne doit plus rien")
    }

    @Test
    fun `supprimer un remboursement recu - la creance revient`() = test {
        b.cheque("chq", 1000.0, 0.0)
        app.autres.prets["p1"] = PretPersonnel(id = "p1", nomTiers = "Ana", montantInitial = 100.0, solde = 100.0, type = TypePretPersonnel.PRET, created = "2026-01-01")
        val ajout = app.ajoutTransaction().also { attendre() }
        ajout.onModeOperationChanged("Prêt"); ajout.onTypePretChanged("Remboursement reçu")
        ajout.onCompteChanged(b.comptes["chq"]); ajout.onTiersChanged("Ana"); ajout.onMontantChanged("3000")
        ajout.sauvegarderTransaction(); attendre()
        assertEquals(70.0, app.autres.prets.getValue("p1").solde)

        app.historiqueCompte("chq").also { attendre() }.supprimerTransaction(b.transactions.keys.single()); attendre()

        assertEquals(1000.0, b.soldeDe("chq")); assertEquals(0.0, b.cheque("chq").pretAPlacer)
        assertEquals(100.0, app.autres.prets.getValue("p1").solde); assertFalse(app.autres.prets.getValue("p1").estArchive)
    }

    @Test
    fun `supprimer un pret deja en partie rembourse - refuse, rien ne bouge`() = test {
        b.cheque("chq", 1000.0, 1000.0)
        val pret = app.ajoutTransaction().also { attendre() }
        pret.onModeOperationChanged("Prêt"); pret.onTypePretChanged("Prêt accordé")
        pret.onCompteChanged(b.comptes["chq"]); pret.onTiersChanged("Ana"); pret.onMontantChanged("10000")
        pret.sauvegarderTransaction(); attendre()
        val idPret = b.transactions.keys.single()
        val remb = app.ajoutTransaction().also { attendre() }
        remb.onModeOperationChanged("Prêt"); remb.onTypePretChanged("Remboursement reçu")
        remb.onCompteChanged(b.comptes["chq"]); remb.onTiersChanged("Ana"); remb.onMontantChanged("3000")
        remb.sauvegarderTransaction(); attendre()
        assertEquals(930.0, b.soldeDe("chq"))

        val vm = app.historiqueCompte("chq").also { attendre() }
        vm.supprimerTransaction(idPret); attendre()

        assertNotNull(vm.uiState.value.erreur)
        assertEquals(930.0, b.soldeDe("chq")); assertEquals(70.0, app.autres.prets.values.single().solde)
        assertEquals(2, b.transactions.size)
    }

    // ===== Catégories et enveloppes =====

    private suspend fun TestScope.categories() = app.categories().also { attendre() }

    @Test
    fun `creer une categorie et trois enveloppes - bons liens et ordre stable`() = test {
        val vm = categories()
        vm.onOuvrirAjoutCategorieDialog(); vm.onNomCategorieChange("Maison"); vm.onAjouterCategorie(); attendre()
        listOf("Loyer", "Hydro", "Internet").forEach {
            vm.onOuvrirAjoutEnveloppeDialog("Maison"); vm.onNomEnveloppeChange(it); vm.onAjouterEnveloppe(); attendre()
        }

        val maison = b.categories.values.single { it.nom == "Maison" }
        val env = b.enveloppes.values.filter { it.categorieId == maison.id }
        assertEquals(setOf("Loyer", "Hydro", "Internet"), env.map { it.nom }.toSet())
        assertEquals(3, env.map { it.ordre }.toSet().size, "CE10 : chaque enveloppe a son propre ordre")
        assertEquals(listOf("Loyer", "Hydro", "Internet"), env.sortedBy { it.ordre }.map { it.nom })
    }

    @Test
    fun `double appui sur Ajouter categorie - une seule categorie`() = test {
        val vm = categories()
        vm.onNomCategorieChange("Maison"); vm.onAjouterCategorie(); vm.onAjouterCategorie(); attendre()
        assertEquals(1, b.categories.values.count { it.nom == "Maison" })
    }

    @Test
    fun `categorie au nom deja pris - refusee`() = test {
        b.categorie("c1", "Maison")
        val vm = categories()
        vm.onNomCategorieChange("maison"); vm.onAjouterCategorie(); attendre()
        assertEquals(1, b.categories.size); assertNotNull(vm.uiState.value.erreur)
    }

    @Test
    fun `enveloppe en double dans la meme categorie - refusee`() = test {
        b.categorie("c1", "Maison"); b.enveloppe("loyer", "Loyer", categorieId = "c1")
        val vm = categories()
        vm.onOuvrirAjoutEnveloppeDialog("Maison"); vm.onNomEnveloppeChange("loyer"); vm.onAjouterEnveloppe(); attendre()
        assertEquals(1, b.enveloppes.size); assertNotNull(vm.uiState.value.erreur)
    }

    @Test
    fun `echec de creation de categorie - erreur visible et saisie gardee`() = test {
        val vm = categories()
        b.echouerSur = "creerCategorie"
        vm.onOuvrirAjoutCategorieDialog(); vm.onNomCategorieChange("Maison"); vm.onAjouterCategorie(); attendre()

        assertTrue(b.categories.isEmpty())
        assertNotNull(vm.uiState.value.erreur)
        assertEquals("Maison", vm.uiState.value.nomNouvelleCategorie)
    }

    @Test
    fun `supprimer une enveloppe qui contient 55 dollars - refuse, l'argent reste`() = test {
        b.cheque("chq", 1000.0, 0.0); b.categorie("c1", "Maison")
        val loyer = b.enveloppe("loyer", "Loyer", categorieId = "c1")
        b.allocation("loyer", maintenant, solde = 55.0, provenance = "chq")
        val vm = categories()
        vm.onOuvrirConfirmationSuppressionEnveloppe(loyer); vm.onConfirmerSuppressionEnveloppe(); attendre()

        assertNotNull(vm.uiState.value.erreur)
        assertFalse(b.enveloppes.getValue("loyer").estArchive)
        assertEquals(55.0, soldeEnveloppe("loyer"))
        assertTrue(vm.uiState.value.enveloppesGroupees.getValue("Maison").any { it.id == "loyer" })
    }

    @Test
    fun `supprimer une enveloppe deja utilisee mais vide - archivee, pas effacee`() = test {
        b.categorie("c1", "Maison")
        val loyer = b.enveloppe("loyer", "Loyer", categorieId = "c1")
        b.allocation("loyer", moisPrecedent(), solde = 0.0, alloue = 20.0, depense = 20.0)
        val vm = categories()
        vm.onOuvrirConfirmationSuppressionEnveloppe(loyer); vm.onConfirmerSuppressionEnveloppe(); attendre()

        assertTrue(b.enveloppes.getValue("loyer").estArchive, "CE1 : archivée, son historique reste")
        assertEquals(1, b.allocations.size)
    }

    @Test
    fun `supprimer une enveloppe vide - elle disparait de la liste (archivee par prudence, CE1)`() = test {
        b.categorie("c1", "Maison")
        val loyer = b.enveloppe("loyer", "Loyer", categorieId = "c1")
        val vm = categories()
        vm.onOuvrirConfirmationSuppressionEnveloppe(loyer); vm.onConfirmerSuppressionEnveloppe(); attendre()
        assertTrue(b.enveloppes["loyer"]?.estArchive != false, "effacée ou archivée")
        assertTrue(vm.uiState.value.enveloppesGroupees.values.flatten().none { it.id == "loyer" })
    }

    @Test
    fun `supprimer une categorie qui garde une enveloppe archivee - refuse`() = test {
        b.categorie("c1", "Maison")
        b.enveloppes["vieux"] = b.enveloppe("vieux", "Vieux", categorieId = "c1").copy(estArchive = true)
        val vm = categories()
        vm.onOuvrirConfirmationSuppressionCategorie("Maison"); vm.onConfirmerSuppressionCategorie(); attendre()

        assertTrue(b.categories.containsKey("c1")); assertNotNull(vm.uiState.value.erreur)
    }

    @Test
    fun `supprimer une categorie vide - supprimee`() = test {
        b.categorie("c1", "Maison")
        val vm = categories()
        vm.onOuvrirConfirmationSuppressionCategorie("Maison"); vm.onConfirmerSuppressionCategorie(); attendre()
        assertFalse(b.categories.containsKey("c1"))
    }

    @Test
    fun `objectif mensuel - 0 dollar refuse, 200 enregistre puis retire`() = test {
        b.categorie("c1", "Maison"); b.enveloppe("loyer", "Loyer", categorieId = "c1")
        val vm = categories()
        vm.onOuvrirObjectifDialog(b.enveloppes.getValue("loyer"))
        vm.onObjectifTypeChange(TypeObjectif.Mensuel); vm.onObjectifJourChange(1); vm.onObjectifMontantChange("0")
        vm.onSauvegarderObjectif(); attendre()
        assertNotNull(vm.uiState.value.erreur, "CE14 : montant positif exigé")
        assertEquals(TypeObjectif.Aucun, b.enveloppes.getValue("loyer").typeObjectif)

        vm.onEffacerErreur()
        vm.onObjectifMontantChange("200"); vm.onSauvegarderObjectif(); attendre()
        assertEquals(TypeObjectif.Mensuel, b.enveloppes.getValue("loyer").typeObjectif)
        assertEquals(200.0, b.enveloppes.getValue("loyer").objectifMontant)

        vm.onSupprimerObjectifEnveloppe(b.enveloppes.getValue("loyer")); attendre()
        assertEquals(TypeObjectif.Aucun, b.enveloppes.getValue("loyer").typeObjectif)
        assertEquals(0.0, b.enveloppes.getValue("loyer").objectifMontant)
    }

    // ===== Rollover (nouveau mois) =====

    private fun rolloverFaitLeMoisPasse() { app.autres.dernierRollover = moisPrecedent() }

    @Test
    fun `surplus de 30 reporte au nouveau mois`() = test {
        b.enveloppe("loyer")
        b.allocation("loyer", moisPrecedent(), solde = 30.0, alloue = 50.0, depense = 20.0)
        rolloverFaitLeMoisPasse()
        assertTrue(app.rollover().isSuccess)

        assertEquals(0.0, soldeEnveloppe("loyer", moisPrecedent())); assertEquals(30.0, soldeEnveloppe("loyer"))
    }

    @Test
    fun `deficit de 20 reporte au nouveau mois`() = test {
        b.enveloppe("loyer")
        b.allocation("loyer", moisPrecedent(), solde = -20.0, alloue = 0.0, depense = 20.0)
        rolloverFaitLeMoisPasse()
        app.rollover()
        assertEquals(-20.0, soldeEnveloppe("loyer"))
    }

    @Test
    fun `rollover lance deux fois le meme mois - un seul report`() = test {
        b.enveloppe("loyer")
        b.allocation("loyer", moisPrecedent(), solde = 30.0)
        rolloverFaitLeMoisPasse()
        app.rollover(); app.rollover()
        assertEquals(30.0, soldeEnveloppe("loyer"))
    }

    @Test
    fun `nouveau mois deja alloue - le report s'additionne`() = test {
        b.enveloppe("loyer")
        b.allocation("loyer", moisPrecedent(), solde = 30.0)
        b.allocation("loyer", maintenant, solde = 10.0)
        rolloverFaitLeMoisPasse()
        app.rollover()
        assertEquals(40.0, soldeEnveloppe("loyer"))
    }

    @Test
    fun `echec en plein report - aucun argent ne disparait et le report est refait`() = test {
        b.enveloppe("loyer"); b.enveloppe("hydro")
        b.allocation("loyer", moisPrecedent(), solde = 30.0)
        b.allocation("hydro", moisPrecedent(), solde = 40.0)
        rolloverFaitLeMoisPasse()
        b.echouerSur = "creerNouvelleAllocation#2"
        assertTrue(app.rollover().isFailure)

        val total = { id: String -> soldeEnveloppe(id, moisPrecedent()) + soldeEnveloppe(id) }
        assertEquals(30.0, total("loyer")); assertEquals(40.0, total("hydro"))
        assertTrue(BanqueEnMemoire.memeMois(app.autres.dernierRollover!!, moisPrecedent()), "le rollover sera refait")

        b.echouerSur = null
        assertTrue(app.rollover().isSuccess)
        assertEquals(30.0, soldeEnveloppe("loyer")); assertEquals(40.0, soldeEnveloppe("hydro"))
        assertEquals(0.0, soldeEnveloppe("loyer", moisPrecedent())); assertEquals(0.0, soldeEnveloppe("hydro", moisPrecedent()))
    }
}
