package com.xburnsx.toutiebudget.ui

import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.services.HistoriquePatrimoine
import com.xburnsx.toutiebudget.fakes.AppliEnMemoire
import com.xburnsx.toutiebudget.ui.statistiques.ReglesStatistiques
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
import java.util.Calendar
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Section 14 — les cas de vérification de la roadmap, par le vrai ViewModel des statistiques. */
@OptIn(ExperimentalCoroutinesApi::class)
class EcransStatistiquesTest {

    private val main = StandardTestDispatcher()
    private lateinit var app: AppliEnMemoire
    private val b get() = app.b
    private val maintenant = Date()
    private var n = 1

    @Before fun avant() { Dispatchers.setMain(main); app = AppliEnMemoire() }
    @After fun apres() { attendreSansScope(); app.fermer(); Dispatchers.resetMain() }

    private fun test(bloc: suspend TestScope.() -> Unit) = runTest(main) { bloc() }
    private fun TestScope.attendre() = attendreSansScope()
    private fun attendreSansScope() = repeat(25) { main.scheduler.advanceUntilIdle(); Thread.sleep(20) }

    private fun tx(
        type: TypeTransaction, montant: Double, compte: String, collection: String = "comptes_cheques",
        tiers: String? = null, allocation: String? = null, date: Date = maintenant, sousItems: String? = null
    ) = Transaction(
        id = "t${n++}", type = type, montant = montant, date = date, compteId = compte, collectionCompte = collection,
        tiersUtiliser = tiers, allocationMensuelleId = allocation, estFractionnee = sousItems != null, sousItems = sousItems
    ).also { b.transactions[it.id] = it }

    private suspend fun TestScope.stats(historique: HistoriquePatrimoine? = null) = app.statistiques(historique).also { attendre() }.uiState.value

    @Test
    fun `achat Visa de 100 puis paiement Visa de 100 - une seule depense de 100`() = test {
        b.cheque("chq", 900.0, 0.0); b.carte("visa", 0.0)
        b.categorie("cat", "Auto"); b.enveloppe("essence")
        val a = b.allocation("essence", maintenant, solde = 100.0, alloue = 100.0, provenance = "chq")
        tx(TypeTransaction.Depense, 100.0, "visa", "comptes_credits", "Irving", a.id)
        tx(TypeTransaction.PaiementEffectue, 100.0, "chq", tiers = "Visa")
        tx(TypeTransaction.Paiement, 100.0, "visa", "comptes_credits", tiers = "Visa")
        val s = stats()

        assertEquals(100.0, s.totalDepenses); assertEquals(0.0, s.totalRevenus)
        assertEquals(listOf("essence" to 100.0), s.top5Enveloppes.map { it.id to it.montant })
    }

    @Test
    fun `emprunt de 500 - aucun revenu et patrimoine inchange`() = test {
        b.cheque("chq", 1000.0, 0.0)
        val avant = stats().valeurNette
        b.comptes["chq"] = b.cheque("chq").copy(solde = 1500.0)
        app.autres.prets["p1"] = com.xburnsx.toutiebudget.data.modeles.PretPersonnel(
            id = "p1", nomTiers = "Bob", montantInitial = 500.0, solde = -500.0, type = com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel.DETTE
        )
        tx(TypeTransaction.Emprunt, 500.0, "chq", tiers = "Bob")
        val s = stats()

        assertEquals(0.0, s.totalRevenus); assertEquals(0.0, s.totalDepenses)
        assertEquals(avant, s.valeurNette)
    }

    @Test
    fun `pret de 80 puis remboursement - ni fausse depense ni faux salaire`() = test {
        b.cheque("chq", 1000.0, 0.0)
        tx(TypeTransaction.Pret, 80.0, "chq", tiers = "Ana")
        tx(TypeTransaction.RemboursementRecu, 80.0, "chq", tiers = "Ana")
        val s = stats()
        assertEquals(0.0, s.totalDepenses); assertEquals(0.0, s.totalRevenus)
    }

    @Test
    fun `achat fractionne de 60 - total, enveloppes et categorie coherents`() = test {
        b.cheque("chq", 1000.0, 0.0)
        b.categorie("maison", "Maison"); b.enveloppe("epicerie", categorieId = "maison"); b.enveloppe("essence", categorieId = "maison")
        val a1 = b.allocation("epicerie", maintenant, solde = 0.0); val a2 = b.allocation("essence", maintenant, solde = 0.0)
        tx(TypeTransaction.Depense, 60.0, "chq", tiers = "Costco", sousItems =
            """[{"enveloppeId":"epicerie","montant":35.0,"allocation_mensuelle_id":"${a1.id}"},{"enveloppe_id":"essence","montant":25.0,"allocation_mensuelle_id":"${a2.id}"}]""")
        val s = stats()

        assertEquals(60.0, s.totalDepenses)
        assertEquals(mapOf("epicerie" to 35.0, "essence" to 25.0), s.top5Enveloppes.associate { it.id to it.montant })
        assertEquals(60.0, s.depensesParCategorie.single().montant)
        assertEquals(2, s.enveloppesParCategorie.getValue("maison").size)
        assertEquals(60.0, s.top5Tiers.single().montant)
    }

    @Test
    fun `ST18 - Irving et irving sont un tiers, Irving Port-Cartier un autre, sommes au cent`() = test {
        b.cheque("chq", 1000.0, 0.0); b.enveloppe("dep")
        val a = b.allocation("dep", maintenant, solde = 100.0, provenance = "chq")
        tx(TypeTransaction.Depense, 0.1, "chq", "comptes_cheques", "Irving", a.id)
        tx(TypeTransaction.Depense, 0.2, "chq", "comptes_cheques", "  irving ", a.id)
        tx(TypeTransaction.Depense, 5.0, "chq", "comptes_cheques", "Irving Port-Cartier", a.id)
        val s = stats()

        val irving = s.top5Tiers.single { it.label == "Irving" }
        assertEquals(0.3, irving.montant) // pas 0.30000000000000004
        assertEquals(2, s.top5Tiers.size)
        assertEquals(5.3, s.top5Enveloppes.single().montant)
    }

    @Test
    fun `SA7 - prevu vs reel, un achat par carte n'est pas compte deux fois`() = test {
        b.cheque("chq", 1000.0, 0.0); b.carte("visa", 0.0); b.enveloppe("dep")
        // 100 $ assignés ; l'achat carte de 5 $ a ajouté 5 $ à l'assigné (règle CC)
        val a = b.allocation("dep", maintenant, solde = 90.0, alloue = 105.0, depense = 15.0, provenance = "chq")
        tx(TypeTransaction.Depense, 5.0, "visa", "comptes_credits", "Irving", a.id)
        tx(TypeTransaction.Depense, 10.0, "chq", "comptes_cheques", "Metro", a.id)
        val ligne = stats().prevuReel.single().second

        assertEquals(100.0, ligne.prevu); assertEquals(15.0, ligne.reel); assertEquals(85.0, ligne.ecart)
    }

    @Test
    fun `SA1 - filtrer un compte ne garde que ses transactions`() = test {
        b.cheque("bleu", 1000.0, 0.0, nom = "Bleu"); b.cheque("rouge", 1000.0, 0.0, nom = "Rouge"); b.enveloppe("dep")
        val a = b.allocation("dep", maintenant, solde = 100.0, provenance = "bleu")
        tx(TypeTransaction.Depense, 10.0, "bleu", tiers = "A", allocation = a.id)
        tx(TypeTransaction.Depense, 30.0, "rouge", tiers = "B", allocation = a.id)
        val vm = app.statistiques().also { attendre() }
        assertEquals(40.0, vm.uiState.value.totalDepenses)
        assertEquals(listOf("bleu" to "Bleu", "rouge" to "Rouge"), vm.uiState.value.comptesDisponibles)

        vm.filtrerComptes(setOf("rouge")); attendre()
        assertEquals(30.0, vm.uiState.value.totalDepenses)
        assertEquals(setOf("rouge"), vm.uiState.value.comptesFiltres)
    }

    @Test
    fun `SA3 - comparaison avec le mois precedent a jours egaux`() = test {
        b.cheque("chq", 1000.0, 0.0); b.enveloppe("dep")
        val debutMoisPasse = Calendar.getInstance().apply { add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 12) }.time
        val aPasse = b.allocation("dep", debutMoisPasse, solde = 0.0)
        val a = b.allocation("dep", maintenant, solde = 0.0)
        tx(TypeTransaction.Depense, 50.0, "chq", tiers = "A", allocation = aPasse.id, date = debutMoisPasse)
        tx(TypeTransaction.Depense, 100.0, "chq", tiers = "A", allocation = a.id)
        val c = assertNotNull(stats().comparaisonDepensesPrecedente)

        assertEquals(50.0, c.reference); assertEquals(50.0, c.ecart); assertEquals(100.0, c.pourcentage)
    }

    @Test
    fun `SA6 - la valeur nette du jour est notee, rien n'est reconstitue`() = test {
        b.cheque("chq", 1234.0, 0.0)
        val memoire = object : HistoriquePatrimoine {
            val points = mutableMapOf<LocalDate, Double>()
            override suspend fun enregistrer(jour: LocalDate, valeurNette: Double) { points[jour] = valeurNette }
            override suspend fun lire() = points.toList().sortedBy { it.first }
        }
        val s = stats(memoire)
        assertEquals(listOf(LocalDate.now().toString() to 1234.0), s.historiquePatrimoine)
    }

    @Test
    fun `SA8 - CSV echappe les points-virgules et guillemets, montants au cent`() {
        val csv = ReglesStatistiques.exporterCsv(
            listOf(Transaction(id = "1", type = TypeTransaction.Depense, montant = 12.5, date = maintenant,
                compteId = "c", collectionCompte = "comptes_cheques", tiersUtiliser = "Dépanneur \"Chez Jo\"; Inc")),
            nomEnveloppe = { "Épicerie" }, nomCompte = { "Bleu" }
        )
        assertTrue(csv.startsWith("﻿Date;Type;Tiers;Compte;Enveloppe;Montant ($);Note"))
        assertTrue(csv.contains(";\"Dépanneur \"\"Chez Jo\"\"; Inc\";Bleu;Épicerie;-12,50;"), csv)
    }

    @Test
    fun `SA3 - base nulle ou absente, jamais un pourcentage infini`() {
        assertNull(ReglesStatistiques.comparer(100.0, 0.0).pourcentage)
        assertEquals(100.0, ReglesStatistiques.comparer(100.0, 0.0).ecart)
        assertNull(ReglesStatistiques.comparer(100.0, null).ecart)
    }
}
