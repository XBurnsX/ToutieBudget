package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
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

    @Test fun confirmationPerimeeNeSupprimeRienDansLeCompte() = test {
        b.cheque("chq", 1000.0, 0.0)
        b.transactions["achat"] = Transaction(
            id = "achat", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 12.50, date = Date(), compteId = "chq",
            collectionCompte = "comptes_cheques", tiersUtiliser = "Irving"
        )
        val vm = app.historiqueCompte("chq")
        attendre()
        val affichee = vm.uiState.value.transactions.single()
        b.transactions["achat"] = b.transactions.getValue("achat").copy(montant = 14.25)

        vm.supprimerTransactionConfirmee(affichee)
        attendre()

        assertEquals(14.25, b.transactions.getValue("achat").montant, 0.000001)
        assertTrue(vm.uiState.value.erreur.orEmpty().contains("Aucune suppression"))
    }

    @Test fun confirmationPerimeeNeSupprimeRienDansLenveloppe() = test {
        b.enveloppe("auto")
        b.transactions["achat"] = Transaction(
            id = "achat", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 60.0, date = Date(), compteId = "chq",
            collectionCompte = "comptes_cheques", tiersUtiliser = "Station",
            estFractionnee = true,
            sousItems = """[{"enveloppeId":"auto","montant":20.00},{"enveloppeId":"courses","montant":40.00}]"""
        )
        val vm = app.historiqueEnveloppe("auto")
        attendre()
        val affichee = vm.uiState.value.transactions.single()
        assertEquals(20.0, affichee.montant, 0.000001)
        assertEquals(60.0, affichee.montantTotalTransaction!!, 0.000001)
        b.transactions["achat"] = b.transactions.getValue("achat").copy(
            sousItems = """[{"enveloppeId":"auto","montant":25.00},{"enveloppeId":"courses","montant":35.00}]"""
        )
        vm.supprimerTransactionConfirmee(affichee)
        attendre()
        assertTrue(b.transactions.containsKey("achat"))
        assertTrue(vm.uiState.value.erreur.orEmpty().contains("Aucune suppression"))
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
        b.enveloppe("essence", nom = "Essence auto")
        b.enveloppe("epicerie", nom = "Épicerie familiale")
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
        assertEquals(
            listOf("Essence auto", "Épicerie familiale"),
            vm.uiState.value.transactions.single().nomsEnveloppesFractions
        )
        assertEquals("chq", vm.uiState.value.transactions.single().compteId)
        vm.mettreAJourRecherche("5,25")
        assertEquals(1, vm.uiState.value.transactionsGroupees.values.flatten().size)
        vm.mettreAJourRecherche("7,25")
        assertTrue(vm.uiState.value.transactionsGroupees.isEmpty())
        vm.mettreAJourRecherche("DEPANNEUR")
        assertEquals(1, vm.uiState.value.transactionsGroupees.values.flatten().size)
    }
    @Test fun selectionMoisEtAnneeSurVraiHistoriqueEnveloppe() = test {
        b.enveloppe("essence")
        fun date(jour: String) = Date.from(
            java.time.LocalDate.parse(jour).atStartOfDay(
                java.time.ZoneId.systemDefault()
            ).toInstant()
        )
        for ((id, jour) in listOf(
            "dec25" to "2025-12-31",
            "jan26" to "2026-01-01",
            "dec26" to "2026-12-31",
            "jan27" to "2027-01-01"
        )) {
            b.transactions[id] = Transaction(
                id = id, utilisateurId = "u",
                type = TypeTransaction.Depense, montant = 5.0,
                date = date(jour), compteId = "chq",
                collectionCompte = "comptes_cheques",
                tiersUtiliser = "Station",
                estFractionnee = true,
                sousItems = """[{"enveloppeId":"essence","montant":5.00}]"""
            )
        }
        val vm = app.historiqueEnveloppe("essence")
        attendre()
        assertEquals(4, vm.uiState.value.transactionsGroupees.values.flatten().size)
        vm.selectionnerVuePeriode(VuePeriodeHistorique.ANNEE)
        val vers2026 = 2026 - vm.uiState.value.periode.annee
        vm.deplacerPeriode(vers2026)
        assertEquals(
            setOf("jan26", "dec26"),
            vm.uiState.value.transactionsGroupees.values.flatten().map { it.id }.toSet()
        )
        vm.selectionnerVuePeriode(VuePeriodeHistorique.MOIS)
        vm.deplacerPeriode(1 - vm.uiState.value.periode.mois)
        assertEquals(
            listOf("jan26"),
            vm.uiState.value.transactionsGroupees.values.flatten().map { it.id }
        )
        vm.rechargerTransactions()
        attendre()
        assertEquals(VuePeriodeHistorique.MOIS, vm.uiState.value.periode.vue)
        assertEquals(
            listOf("jan26"),
            vm.uiState.value.transactionsGroupees.values.flatten().map { it.id }
        )
        vm.selectionnerVuePeriode(VuePeriodeHistorique.TOUTES)
        assertEquals(4, vm.uiState.value.transactionsGroupees.values.flatten().size)
    }

    @Test fun filtragePeriodeEtRechercheNeCreentPasDeTransactionsOuDeDoublons() = test {
        b.enveloppe("auto")
        val date = Date.from(
            java.time.LocalDate.of(2026, 10, 15)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        )
        b.transactions["tx1"] = Transaction(
            id = "tx1", utilisateurId = "u",
            type = TypeTransaction.Depense, montant = 60.0, date = date,
            compteId = "chq", collectionCompte = "comptes_cheques",
            tiersUtiliser = "Costco", estFractionnee = true,
            sousItems = """[{"enveloppeId":"auto","montant":20.00}]"""
        )
        val vm = app.historiqueEnveloppe("auto")
        attendre()
        val avant = b.transactions.keys.toSet()
        vm.selectionnerVuePeriode(VuePeriodeHistorique.ANNEE)
        vm.deplacerPeriode(2026 - vm.uiState.value.periode.annee)
        vm.mettreAJourRecherche("COSTCO")
        assertEquals(1, vm.uiState.value.transactionsGroupees.values.flatten().size)
        assertEquals(
            2000L, ResumeHistoriqueEnveloppe.calculer(
                vm.uiState.value.transactionsGroupees.values.flatten()
            ).depensesCentimes
        )
        vm.mettreAJourRecherche("introuvable")
        assertTrue(vm.uiState.value.transactionsGroupees.isEmpty())
        assertEquals(avant, b.transactions.keys.toSet())
        assertEquals(60.0, b.transactions.getValue("tx1").montant, 0.000001)
    }

    @Test fun compteRefuseSuppressionDeTransactionDansUnAutreCompte() = test {
        b.cheque("cheque", 1000.0, 0.0)
        b.cheque("autre", 1000.0, 0.0)
        b.transactions["secret"] = Transaction(
            id = "secret", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 15.00, date = Date(), compteId = "autre",
            collectionCompte = "comptes_cheques", tiersUtiliser = "Confidentiel"
        )
        val vm = app.historiqueCompte("cheque")
        attendre()
        assertTrue(vm.uiState.value.transactions.isEmpty())
        vm.supprimerTransaction("secret")
        attendre()
        assertTrue(b.transactions.containsKey("secret"))
        assertTrue(vm.uiState.value.erreur.orEmpty().contains("n'appartient pas au compte"))
    }

    @Test fun enveloppeRefuseSuppressionDeTransactionDuneAutreEnveloppe() = test {
        b.enveloppe("essence")
        b.enveloppe("loyer")
        b.transactions["loyer_tx"] = Transaction(
            id = "loyer_tx", utilisateurId = "u",
            type = TypeTransaction.Depense, montant = 100.0,
            date = Date(), compteId = "chq", collectionCompte = "comptes_cheques",
            tiersUtiliser = "Loyer", estFractionnee = true,
            sousItems = """[{"enveloppeId":"loyer","montant":100.00}]"""
        )
        val vm = app.historiqueEnveloppe("essence")
        attendre()
        vm.supprimerTransaction("loyer_tx")
        attendre()
        assertTrue(b.transactions.containsKey("loyer_tx"))
        assertTrue(vm.uiState.value.erreur.orEmpty().contains("ne figure plus"))
    }

    @Test fun libelleEnveloppeArchiveeEtAllocationIntrouvableCompte() = test {
        b.cheque("chq", 1000.0, 0.0)
        b.enveloppe("essence", nom = "Essence auto")
        b.enveloppes["essence"] = b.enveloppes.getValue("essence").copy(estArchive = true)
        val allocation = b.allocation("essence", Date(), solde = 0.0)
        b.transactions["archive"] = Transaction(
            id = "archive", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 10.0, date = Date(), compteId = "chq",
            collectionCompte = "comptes_cheques", tiersUtiliser = "Station",
            allocationMensuelleId = allocation.id
        )
        b.transactions["orpheline"] = Transaction(
            id = "orpheline", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 5.0, date = Date(), compteId = "chq",
            collectionCompte = "comptes_cheques", tiersUtiliser = "Marché",
            allocationMensuelleId = "alloc-supprimee"
        )
        val vm = app.historiqueCompte("chq")
        attendre()
        assertNull(vm.uiState.value.erreur)
        val mouvements = vm.uiState.value.transactions.associateBy { it.id }
        assertEquals("Essence auto (archivée)", mouvements.getValue("archive").nomEnveloppe)
        assertEquals(
            "Allocation historique introuvable (ID : alloc-supprimee)",
            mouvements.getValue("orpheline").nomEnveloppe
        )
        assertTrue(vm.uiState.value.avertissement.orEmpty().contains("allocations"))
    }

    @Test fun ancienneFractionGardeSaReferenceMemeSiEnveloppeSupprimee() = test {
        b.cheque("chq", 1000.0, 0.0)
        b.transactions["fraction-inconnue"] = Transaction(
            id = "fraction-inconnue", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 3.15, date = Date(), compteId = "chq",
            collectionCompte = "comptes_cheques", tiersUtiliser = "Station",
            estFractionnee = true,
            sousItems = """[{"enveloppe_id":"env-absente","montant":3.15}]"""
        )
        val vm = app.historiqueCompte("chq")
        attendre()
        assertNull(vm.uiState.value.erreur)
        assertEquals(3.15, vm.uiState.value.transactions.single().montant, 0.00001)
        val libelle = vm.uiState.value.transactions.single().nomsEnveloppesFractions.single()
        assertTrue(libelle.contains("introuvable"))
        assertTrue(libelle.contains("env-absente"))
    }

    @Test fun changementDeProfilPendantChargementRejetteLesAnciennesTransactions() = test {
        b.cheque("chq", 1000.0, 0.0)
        b.transactions["achat"] = Transaction(
            id = "achat", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 23.0, date = Date(), compteId = "chq",
            collectionCompte = "comptes_cheques", tiersUtiliser = "Ancien profil"
        )
        var session = "profil-A"
        val depot = object : TransactionRepository by b.depotTransactions {
            override fun identifiantSessionHistorique(): String? = session

            override suspend fun recupererTransactionsPourCompte(
                compteId: String, collectionCompte: String
            ): Result<List<Transaction>> {
                val resultat = b.depotTransactions.recupererTransactionsPourCompte(compteId, collectionCompte)
                session = "profil-B" // la session bascule pendant la lecture
                return resultat
            }
        }
        val vm = HistoriqueCompteViewModel(
            depot, b.depotEnveloppes, app.autres.depotTiers, app.supprimer,
            SavedStateHandle(mapOf("compteId" to "chq", "collectionCompte" to "comptes_cheques"))
        )
        val store = ViewModelStore().apply { put("historique-change", vm) }
        try {
            attendre()
            assertTrue(vm.uiState.value.transactions.isEmpty())
            assertTrue(vm.uiState.value.transactionsGroupees.isEmpty())
            assertTrue(vm.uiState.value.erreur.orEmpty().contains("session a changé"))
        } finally {
            store.clear()
        }
    }

    @Test fun deconnexionPendantRechargementMasqueLesTransactionsPrecedentes() = test {
        b.cheque("chq", 1000.0, 0.0)
        b.transactions["achat"] = Transaction(
            id = "achat", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 23.0, date = Date(), compteId = "chq",
            collectionCompte = "comptes_cheques", tiersUtiliser = "Ancien profil"
        )
        var deconnecte = false
        val depot = object : TransactionRepository by b.depotTransactions {
            override suspend fun recupererTransactionsPourCompte(
                compteId: String, collectionCompte: String
            ): Result<List<Transaction>> =
                if (deconnecte) Result.failure(SecurityException("Connexion requise."))
                else b.depotTransactions.recupererTransactionsPourCompte(compteId, collectionCompte)
        }
        val vm = HistoriqueCompteViewModel(
            depot, b.depotEnveloppes, app.autres.depotTiers, app.supprimer,
            SavedStateHandle(mapOf("compteId" to "chq", "collectionCompte" to "comptes_cheques"))
        )
        val store = ViewModelStore().apply { put("historique-deconnexion", vm) }
        try {
            attendre()
            assertEquals(1, vm.uiState.value.transactions.size)
            deconnecte = true
            vm.rechargerTransactions()
            attendre()
            assertTrue(vm.uiState.value.transactions.isEmpty())
            assertTrue(vm.uiState.value.transactionsGroupees.isEmpty())
            assertTrue(vm.uiState.value.erreur.orEmpty().contains("Connexion requise"))
            assertTrue(b.transactions.containsKey("achat")) // lecture seule : rien supprimé
        } finally {
            store.clear()
        }
    }

    @Test fun filtreCompteMoisTypeRechercheSurvitAuRechargement() = test {
        b.cheque("chq", 1000.0, 0.0)
        fun date(jour: String) = Date.from(
            java.time.LocalDate.parse(jour)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        )
        b.transactions["oct"] = Transaction(
            id = "oct", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 20.00, date = date("2026-10-28"),
            compteId = "chq", collectionCompte = "comptes_cheques", tiersUtiliser = "Irving #0421"
        )
        b.transactions["nov"] = Transaction(
            id = "nov", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 50.00, date = date("2026-11-12"),
            compteId = "chq", collectionCompte = "comptes_cheques", tiersUtiliser = "Irving #0421"
        )
        b.transactions["revenu"] = Transaction(
            id = "revenu", utilisateurId = "u", type = TypeTransaction.Revenu,
            montant = 300.00, date = date("2026-10-28"),
            compteId = "chq", collectionCompte = "comptes_cheques", tiersUtiliser = "Salaire"
        )
        val vm = app.historiqueCompte("chq")
        attendre()
        assertEquals(3, vm.uiState.value.transactions.size)
        vm.selectionnerVuePeriode(VuePeriodeHistorique.MOIS)
        val p = vm.uiState.value.periode
        vm.deplacerPeriode((2026 - p.annee) * 12 + (10 - p.mois))
        vm.selectionnerTypeFiltre(TypeTransaction.Depense)
        vm.mettreAJourRecherche("IRV 0421")
        assertEquals(listOf("oct"), vm.uiState.value.transactionsGroupees.values.flatten().map { it.id })
        val montant = ResumeFluxHistoriqueCompte.calculer(
            vm.uiState.value.transactionsGroupees.values.flatten()
        )
        assertEquals(2000L, montant.sortiesCentimes)
        vm.rechargerTransactions()
        attendre()
        assertEquals(VuePeriodeHistorique.MOIS, vm.uiState.value.periode.vue)
        assertEquals(TypeTransaction.Depense, vm.uiState.value.typeFiltre)
        assertEquals("IRV 0421", vm.uiState.value.searchQuery)
        assertEquals(listOf("oct"), vm.uiState.value.transactionsGroupees.values.flatten().map { it.id })
        vm.selectionnerVuePeriode(VuePeriodeHistorique.TOUTES)
        vm.selectionnerTypeFiltre(null)
        vm.mettreAJourRecherche("")
        assertEquals(3, vm.uiState.value.transactionsGroupees.values.flatten().size)
    }

    @Test fun enveloppeArchiveeEtReferenceDeFractionPerdueConserventLeursVraisLibelles() = test {
        b.cheque("chq", 1000.0, 0.0)
        b.enveloppe("auto", nom = "Essence")
        b.enveloppes["auto"] = b.enveloppes.getValue("auto").copy(estArchive = true)
        b.transactions["achat"] = Transaction(
            id = "achat", utilisateurId = "u", type = TypeTransaction.Depense,
            montant = 60.0, date = Date(), compteId = "chq",
            collectionCompte = "comptes_cheques", tiersUtiliser = "Station",
            estFractionnee = true,
            sousItems = """[{"enveloppeId":"auto","montant":20.00},{"enveloppeId":"ancienne-supprimee","montant":40.00}]"""
        )
        val vm = app.historiqueEnveloppe("auto")
        attendre()
        assertNull(vm.uiState.value.erreur)
        val achat = vm.uiState.value.transactions.single()
        assertEquals(20.0, achat.montant, 0.0000001)
        assertEquals(60.0, achat.montantTotalTransaction ?: 0.0, 0.0000001)
        assertEquals("Essence (archivée)", achat.nomsEnveloppesFractions.first())
        assertTrue(achat.nomsEnveloppesFractions[1].contains("ancienne-supprimee"))
    }

}
