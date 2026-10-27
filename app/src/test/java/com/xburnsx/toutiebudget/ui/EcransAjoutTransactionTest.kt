package com.xburnsx.toutiebudget.ui

import com.xburnsx.toutiebudget.data.modeles.PretPersonnel
import com.xburnsx.toutiebudget.data.modeles.Tiers
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel
import com.xburnsx.toutiebudget.data.room.entities.SyncJob
import com.xburnsx.toutiebudget.fakes.AppliEnMemoire
import com.xburnsx.toutiebudget.ui.ajout_transaction.StatutSynchronisationEcriture
import com.xburnsx.toutiebudget.ui.ajout_transaction.determinerStatutSynchronisation
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Section 13 — Ajouter transaction : correctifs monétaires et ajouts AA, par le vrai ViewModel. */
@OptIn(ExperimentalCoroutinesApi::class)
class EcransAjoutTransactionTest {

    private val main = StandardTestDispatcher()
    private lateinit var app: AppliEnMemoire
    private val b get() = app.b
    private val maintenant = Date()

    @Before fun avant() { Dispatchers.setMain(main); app = AppliEnMemoire() }
    @After fun apres() { attendreSansScope(); app.fermer(); Dispatchers.resetMain() }

    private fun test(bloc: suspend TestScope.() -> Unit) = runTest(main) { bloc() }
    private fun TestScope.attendre() = attendreSansScope()
    private fun attendreSansScope() = repeat(25) { main.scheduler.advanceUntilIdle(); Thread.sleep(20) }

    private fun enveloppeUi(id: String, solde: Double = 0.0) = EnveloppeUi(
        id = id, nom = id, solde = solde, depense = 0.0, alloue = 0.0, alloueCumulatif = 0.0, objectif = 0.0,
        couleurProvenance = null, statutObjectif = StatutObjectif.GRIS
    )
    private fun moisPasse(): Date = Calendar.getInstance().apply { add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 15) }.time
    private fun Date.local(): LocalDate = toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    private fun soldeEnveloppe(id: String, mois: Date = maintenant) = b.allocationDe(id, mois).sumOf { it.solde }
    private suspend fun TestScope.formulaire() = app.ajoutTransaction().also { attendre() }

    // ===== AT4 / AT15 : tout ou rien =====

    @Test
    fun `historique de transaction directe en erreur - aucun succes ni transaction restante`() = test {
        b.cheque("chq", 1000.0, 0.0)
        b.enveloppe("depanneur")
        b.allocation("depanneur", maintenant, solde = 100.0, provenance = "chq")
        b.echouerSur = "enregistrerTransactionDirecte"
        val retour = app.enregistrer.executer(
            typeTransaction = TypeTransaction.Depense,
            montant = 5.0,
            compteId = "chq",
            collectionCompte = "comptes_cheques",
            enveloppeId = "depanneur",
            tiersUtiliser = "Irving",
            date = maintenant
        )

        assertTrue(retour.isFailure, "Le journal obligatoire ne peut pas echouer silencieusement")
        assertEquals(1000.0, b.soldeDe("chq"))
        assertTrue(b.transactions.isEmpty(), "La transaction est defaite en cas d'erreur du journal")
    }


    @Test
    fun `pret accorde dont la transaction echoue - ni dossier ni debit`() = test {
        b.cheque("chq", 1000.0, 1000.0)
        b.echouerSur = "creerTransaction"
        val vm = formulaire()
        vm.onModeOperationChanged("Prêt"); vm.onTypePretChanged("Prêt accordé")
        vm.onCompteChanged(b.comptes["chq"]); vm.onTiersChanged("Ana"); vm.onMontantChanged("20000")
        vm.sauvegarderTransaction(); attendre()

        assertNotNull(vm.uiState.value.messageErreur)
        assertEquals(1000.0, b.soldeDe("chq")); assertEquals(1000.0, b.cheque("chq").pretAPlacer)
        assertTrue(app.autres.prets.isEmpty()); assertTrue(b.transactions.isEmpty())
    }

    @Test
    fun `emprunt dont la transaction echoue - ni dossier ni credit`() = test {
        b.cheque("chq", 1000.0, 0.0)
        b.echouerSur = "creerTransaction"
        val vm = formulaire()
        vm.onModeOperationChanged("Emprunt"); vm.onTypeDetteChanged("Dette contractée")
        vm.onCompteChanged(b.comptes["chq"]); vm.onTiersChanged("Bob"); vm.onMontantChanged("20000")
        vm.sauvegarderTransaction(); attendre()

        assertEquals(1000.0, b.soldeDe("chq")); assertEquals(0.0, b.cheque("chq").pretAPlacer)
        assertTrue(app.autres.prets.isEmpty())
    }

    @Test
    fun `remboursement reparti qui echoue au 2e dossier - tout revient`() = test {
        b.cheque("chq", 1000.0, 0.0)
        app.autres.prets["p1"] = PretPersonnel(id = "p1", nomTiers = "Ana", montantInitial = 100.0, solde = 100.0, type = TypePretPersonnel.PRET, created = "2026-01-01")
        app.autres.prets["p2"] = PretPersonnel(id = "p2", nomTiers = "Ana", montantInitial = 50.0, solde = 50.0, type = TypePretPersonnel.PRET, created = "2026-02-01")
        b.echouerSur = "creerTransaction#2"
        val vm = formulaire()
        vm.onModeOperationChanged("Prêt"); vm.onTypePretChanged("Remboursement reçu")
        vm.onCompteChanged(b.comptes["chq"]); vm.onTiersChanged("Ana"); vm.onMontantChanged("12000")
        vm.sauvegarderTransaction(); attendre()

        assertNotNull(vm.uiState.value.messageErreur)
        assertEquals(1000.0, b.soldeDe("chq")); assertEquals(0.0, b.cheque("chq").pretAPlacer)
        assertEquals(100.0, app.autres.prets.getValue("p1").solde); assertFalse(app.autres.prets.getValue("p1").estArchive)
        assertEquals(50.0, app.autres.prets.getValue("p2").solde)
        assertTrue(b.transactions.isEmpty())
    }

    @Test
    fun `depense dont le compte ne peut pas etre debite - aucune transaction ne reste`() = test {
        b.cheque("chq", 1000.0, 0.0); b.enveloppe("depanneur")
        b.allocation("depanneur", maintenant, solde = 100.0, provenance = "chq")
        b.echouerSur = "mettreAJourSoldeAvecVariation"
        val vm = formulaire()
        vm.onCompteChanged(b.comptes["chq"]); vm.onTiersChanged("Irving"); vm.onMontantChanged("500")
        vm.onEnveloppeChanged(enveloppeUi("depanneur", 100.0))
        vm.sauvegarderTransaction(); attendre()

        assertNotNull(vm.uiState.value.messageErreur)
        assertTrue(b.transactions.isEmpty())
        assertEquals(1000.0, b.soldeDe("chq")); assertEquals(100.0, soldeEnveloppe("depanneur"))
        assertEquals("500", vm.uiState.value.montant, "AT20 : le brouillon reste pour réessayer")
    }

    // ===== AT8 : fractions = mêmes règles qu'un achat simple, tout ou rien =====

    private suspend fun TestScope.achatFractionne(compte: String) = formulaire().also { vm ->
        vm.onCompteChanged(b.comptes[compte]); vm.onTiersChanged("Costco"); vm.onMontantChanged("6000")
        vm.confirmerFractionnement(listOf(FractionTransaction("f1", 3500.0, "epicerie"), FractionTransaction("f2", 2500.0, "essence")))
        vm.sauvegarderTransaction(); attendre()
    }

    private fun deuxEnveloppes() {
        b.enveloppe("epicerie"); b.enveloppe("essence")
        b.allocation("epicerie", maintenant, solde = 100.0, provenance = "chq")
        b.allocation("essence", maintenant, solde = 100.0, provenance = "chq")
    }

    @Test
    fun `achat fractionne sur carte - carte debitee une fois, enveloppes comme un achat carte simple`() = test {
        b.cheque("chq", 1000.0, 0.0); b.carte("visa", -400.0, nom = "Visa"); deuxEnveloppes()
        achatFractionne("visa")

        assertEquals(-460.0, b.soldeDe("visa"))
        // Achat carte : le solde de l'enveloppe ne bouge pas, dépensé et alloué montent
        assertEquals(100.0, soldeEnveloppe("epicerie")); assertEquals(35.0, b.allocationDe("epicerie", maintenant).sumOf { it.depense })
        assertEquals(100.0, soldeEnveloppe("essence")); assertEquals(25.0, b.allocationDe("essence", maintenant).sumOf { it.depense })
    }

    @Test
    fun `achat simple sur carte puis suppression - l'enveloppe ne gagne pas d'argent`() = test {
        b.cheque("chq", 1000.0, 0.0); b.carte("visa", -400.0, nom = "Visa")
        b.enveloppe("depanneur"); b.allocation("depanneur", maintenant, solde = 100.0, provenance = "chq")
        val vm = formulaire()
        vm.onCompteChanged(b.comptes["visa"]); vm.onTiersChanged("Irving"); vm.onMontantChanged("500")
        vm.onEnveloppeChanged(enveloppeUi("depanneur", 100.0))
        vm.sauvegarderTransaction(); attendre()
        assertEquals(-405.0, b.soldeDe("visa")); assertEquals(100.0, soldeEnveloppe("depanneur"))

        app.historiqueCompte("visa", "comptes_credits").also { attendre() }.supprimerTransaction(b.transactions.keys.single()); attendre()

        assertEquals(-400.0, b.soldeDe("visa"))
        assertEquals(100.0, soldeEnveloppe("depanneur"))
        assertEquals(0.0, b.allocationDe("depanneur", maintenant).sumOf { it.depense })
    }

    @Test
    fun `fraction qui echoue en route - ni transaction, ni debit, ni enveloppe touchee`() = test {
        b.cheque("chq", 1000.0, 0.0); deuxEnveloppes()
        b.echouerSur = "mettreAJourAllocation#2"
        val vm = achatFractionne("chq")

        assertNotNull(vm.uiState.value.messageErreur)
        assertTrue(b.transactions.isEmpty())
        assertEquals(1000.0, b.soldeDe("chq"))
        assertEquals(100.0, soldeEnveloppe("epicerie")); assertEquals(100.0, soldeEnveloppe("essence"))
    }

    // ===== AT6 / AT21 : le bon mois, et pas de faux 0 $ =====

    @Test
    fun `date du mois passe - les soldes d'enveloppes affiches sont ceux de ce mois`() = test {
        b.cheque("chq", 1000.0, 0.0); b.enveloppe("depanneur")
        b.allocation("depanneur", maintenant, solde = 100.0, provenance = "chq")
        b.allocation("depanneur", moisPasse(), solde = 40.0, provenance = "chq")
        val vm = formulaire()
        assertEquals(100.0, vm.uiState.value.enveloppesDisponibles.single().solde)

        vm.onDateChanged(moisPasse().local()); attendre()
        assertEquals(40.0, vm.uiState.value.enveloppesDisponibles.single().solde)
    }

    @Test
    fun `soldes du mois illisibles - indique, et une depense ne peut pas etre enregistree`() = test {
        b.cheque("chq", 1000.0, 0.0); b.enveloppe("depanneur")
        b.echouerSur = "recupererAllocationsPourMois"
        val vm = formulaire()
        vm.onCompteChanged(b.comptes["chq"]); vm.onTiersChanged("Irving"); vm.onMontantChanged("500")
        vm.onEnveloppeChanged(enveloppeUi("depanneur"))

        assertTrue(vm.uiState.value.allocationsIndisponibles)
        assertFalse(vm.uiState.value.peutSauvegarder)
    }

    // ===== AT12 : provenance =====

    @Test
    fun `argent de l'enveloppe venu d'un autre compte - avertissement, pas un refus`() = test {
        b.cheque("bleu", 1000.0, 0.0, nom = "Bleu"); b.cheque("rouge", 500.0, 0.0, nom = "Rouge")
        b.enveloppe("depanneur"); b.allocation("depanneur", maintenant, solde = 100.0, provenance = "bleu")
        val vm = formulaire()
        vm.onCompteChanged(b.comptes["rouge"]); vm.onTiersChanged("Irving"); vm.onMontantChanged("500")
        vm.onEnveloppeChanged(enveloppeUi("depanneur", 100.0))

        assertNotNull(vm.uiState.value.avertissementProvenance)
        assertTrue(vm.uiState.value.peutSauvegarder)

        vm.onCompteChanged(b.comptes["bleu"])
        assertNull(vm.uiState.value.avertissementProvenance)
    }

    // ===== AT17 / AT20 : après succès =====

    @Test
    fun `apres un pret reussi - formulaire vide, message unique, pas de 2e pret possible`() = test {
        b.cheque("chq", 1000.0, 1000.0)
        val vm = formulaire()
        vm.onModeOperationChanged("Prêt"); vm.onTypePretChanged("Prêt accordé")
        vm.onCompteChanged(b.comptes["chq"]); vm.onTiersChanged("Ana"); vm.onMontantChanged("20000")
        vm.sauvegarderTransaction(); attendre()

        assertEquals("Prêt enregistré", vm.uiState.value.messageConfirmation)
        assertFalse(vm.uiState.value.transactionReussie, "AT17 : rien ne relance la navigation après le message")
        assertEquals("", vm.uiState.value.montant)
        vm.sauvegarderTransaction(); attendre()
        assertEquals(1, app.autres.prets.size); assertEquals(800.0, b.soldeDe("chq"))
    }

    // ===== AA6 : date future =====

    @Test
    fun `date future - avertie mais permise`() = test {
        b.cheque("chq", 1000.0, 0.0)
        val vm = formulaire()
        vm.onDateChanged(LocalDate.now().plusDays(3))
        assertTrue(vm.uiState.value.dateFuture)
        vm.onDateChanged(LocalDate.now())
        assertFalse(vm.uiState.value.dateFuture)
    }

    // ===== AT10 / AT18 et AA1 à AA5 =====

    @Test
    fun `apercu annonce les soldes projetes sans enregistrer`() = test {
        b.cheque("chq", 100.0, 0.0, nom = "Compte courant")
        b.enveloppe("depanneur", nom = "Dépanneur")
        b.allocation("depanneur", maintenant, solde = 20.0, provenance = "chq")
        val vm = formulaire()
        vm.onCompteChanged(b.comptes.getValue("chq"))
        vm.onTiersChanged("Irving")
        vm.onMontantChanged("500")
        vm.onEnveloppeChanged(enveloppeUi("depanneur", 20.0).copy(nom = "Dépanneur"))

        val apercu = assertNotNull(vm.uiState.value.apercu)
        assertTrue(apercu.contains("100,00"))
        assertTrue(apercu.contains("95,00"))
        assertTrue(apercu.contains("15,00"))
        assertTrue(b.transactions.isEmpty(), "AA1 ne sauvegarde rien")
    }

    @Test
    fun `tiers connu propose ses enveloppes recentes sans les selectionner`() = test {
        b.cheque("chq", 100.0, 0.0)
        b.enveloppe("epicerie", nom = "Épicerie")
        val allocation = b.allocation("epicerie", maintenant, solde = 30.0, provenance = "chq")
        b.depotTransactions.creerTransaction(
            Transaction(
                id = "ancienne",
                utilisateurId = "u",
                type = TypeTransaction.Depense,
                montant = 4.0,
                compteId = "chq",
                collectionCompte = "comptes_cheques",
                allocationMensuelleId = allocation.id,
                tiersUtiliser = "Marché"
            )
        ).getOrThrow()
        val vm = formulaire()
        vm.onTiersChanged("  marché ")
        attendre()

        assertEquals(listOf("epicerie"), vm.uiState.value.suggestionsEnveloppes.map { it.id })
        assertNull(vm.uiState.value.enveloppeSelectionnee, "AA2 propose, mais ne choisit jamais automatiquement")
    }

    @Test
    fun `remboursement peut viser un dossier precis`() = test {
        b.cheque("chq", 100.0, 0.0)
        app.autres.prets["p1"] = PretPersonnel(id = "p1", nomTiers = "Ana", montantInitial = 50.0, solde = 50.0, type = TypePretPersonnel.PRET, created = "2026-01-01")
        app.autres.prets["p2"] = PretPersonnel(id = "p2", nomTiers = "Ana", montantInitial = 80.0, solde = 80.0, type = TypePretPersonnel.PRET, created = "2026-02-01")
        val vm = formulaire()
        vm.onModeOperationChanged("Prêt")
        vm.onTypePretChanged("Remboursement reçu")
        vm.onCompteChanged(b.comptes.getValue("chq"))
        vm.onTiersChanged("Ana")
        vm.onMontantChanged("2000")
        attendre()
        vm.onDossierChoisi("p2")
        vm.sauvegarderTransaction()
        attendre()

        assertEquals(50.0, app.autres.prets.getValue("p1").solde)
        assertEquals(60.0, app.autres.prets.getValue("p2").solde)
        assertEquals(1, b.transactions.size)
    }

    @Test
    fun `reprendre remplit le brouillon sans creer une transaction`() = test {
        b.cheque("chq", 100.0, 0.0)
        b.enveloppe("epicerie")
        val allocation = b.allocation("epicerie", maintenant, solde = 30.0, provenance = "chq")
        b.depotTransactions.creerTransaction(
            Transaction(
                id = "ancienne",
                utilisateurId = "u",
                type = TypeTransaction.Depense,
                montant = 12.34,
                compteId = "chq",
                collectionCompte = "comptes_cheques",
                allocationMensuelleId = allocation.id,
                tiersUtiliser = "Marché",
                note = "Pain"
            )
        ).getOrThrow()
        val vm = formulaire()
        vm.reprendreTransaction("ancienne")
        attendre()

        assertEquals("1234", vm.uiState.value.montant)
        assertEquals("Marché", vm.uiState.value.tiersUtiliser)
        assertEquals("Pain", vm.uiState.value.note)
        assertEquals("chq", vm.uiState.value.compteSelectionne?.id)
        assertEquals("epicerie", vm.uiState.value.enveloppeSelectionnee?.id)
        assertEquals(1, b.transactions.size, "AA4 ne sauvegarde pas la reprise")
    }

    @Test
    fun `note de chaque fraction est conservee dans le json`() = test {
        b.cheque("chq", 100.0, 0.0)
        b.enveloppe("epicerie"); b.enveloppe("maison")
        b.allocation("epicerie", maintenant, solde = 30.0, provenance = "chq")
        b.allocation("maison", maintenant, solde = 30.0, provenance = "chq")
        val vm = formulaire()
        vm.onCompteChanged(b.comptes.getValue("chq")); vm.onTiersChanged("Marché"); vm.onMontantChanged("1000")
        vm.confirmerFractionnement(
            listOf(
                FractionTransaction("f1", 400.0, "epicerie", "Pain"),
                FractionTransaction("f2", 600.0, "maison", "Ampoule")
            )
        )
        vm.sauvegarderTransaction(); attendre()

        val json = assertNotNull(b.transactions.values.single().sousItems)
        assertTrue(json.contains("Pain")); assertTrue(json.contains("Ampoule"))
    }

    @Test
    fun `creation reutilise un tiers equivalent`() = test {
        app.autres.tiers["t1"] = Tiers(id = "t1", nom = "Irving Port-Cartier", utilisateur_id = "u")
        val vm = formulaire()
        vm.onCreerNouveauTiers("  IRVING   PORT-CARTIER ")
        attendre()

        assertEquals(1, app.autres.tiers.size)
        assertEquals("Irving Port-Cartier", vm.uiState.value.tiersUtiliser)
    }

    @Test
    fun `statut de synchronisation reflete seulement la derniere ecriture`() {
        val ancien = SyncJob(id = "ancien", type = "TRANSACTION", action = "CREATE", dataJson = "{}", createdAt = 10, status = "FAILED")
        val pending = SyncJob(id = "local", type = "TRANSACTION", action = "CREATE", dataJson = "{}", createdAt = 101, status = "PENDING")
        val failed = pending.copy(id = "echec", status = "FAILED", bloque = true)

        // L'absence de tâche postérieure n'est PAS un accusé de réception PocketBase.
        assertEquals(StatutSynchronisationEcriture.ENREGISTREE_LOCALEMENT, determinerStatutSynchronisation(listOf(ancien), 100))
        assertEquals(StatutSynchronisationEcriture.ENREGISTREE_LOCALEMENT, determinerStatutSynchronisation(emptyList(), 100))
        assertEquals(StatutSynchronisationEcriture.ENREGISTREE_LOCALEMENT, determinerStatutSynchronisation(listOf(pending), 100))
        assertEquals(StatutSynchronisationEcriture.ENREGISTREE_LOCALEMENT, determinerStatutSynchronisation(listOf(pending.copy(status = "UNKNOWN")), 100))
        assertEquals(StatutSynchronisationEcriture.ECHEC, determinerStatutSynchronisation(listOf(pending, failed), 100))
        assertEquals(StatutSynchronisationEcriture.SYNCHRONISEE, determinerStatutSynchronisation(listOf(pending.copy(status = "COMPLETED")), 100))
        assertEquals(StatutSynchronisationEcriture.ENREGISTREE_LOCALEMENT,
            determinerStatutSynchronisation(listOf(pending.copy(status = "COMPLETED"), pending.copy(id = "a-attendre")), 100))
    }
}
