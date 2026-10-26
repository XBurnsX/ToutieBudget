package com.xburnsx.toutiebudget.domain

import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.data.modeles.DetailsDette
import com.xburnsx.toutiebudget.data.modeles.FraisMensuel
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.domain.services.Impl.ArgentServiceImpl
import com.xburnsx.toutiebudget.domain.services.PlanResetService
import com.xburnsx.toutiebudget.domain.services.ValidationProvenanceService
import com.xburnsx.toutiebudget.domain.services.VirementBudgetService
import com.xburnsx.toutiebudget.domain.usecases.SupprimerTransactionUseCase
import com.xburnsx.toutiebudget.domain.usecases.VirementUseCase
import com.xburnsx.toutiebudget.fakes.BanqueEnMemoire
import com.xburnsx.toutiebudget.fakes.BanqueEnMemoire.Companion.date
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opérations d'argent réelles (services de l'app) contre une base en mémoire.
 * Chaque test vérifie TOUS les soldes touchés : banque, Prêt à placer, enveloppes, cartes/dettes, transactions.
 */
class OperationsArgentTest {

    private val b = BanqueEnMemoire()
    private val argent = ArgentServiceImpl(
        b.depotComptes, b.depotTransactions, b.depotAllocations,
        VirementUseCase(b.depotComptes, b.depotAllocations, b.depotTransactions, b.depotEnveloppes,
            ValidationProvenanceService(b.depotEnveloppes, b.depotComptes), b.historiqueService),
        b.depotEnveloppes, b.depotCategories, b.historiqueService
    )
    private val virementsBudget = VirementBudgetService(b.depotComptes, b.depotEnveloppes, b.depotAllocations, b.historiqueService)
    private val suppression = SupprimerTransactionUseCase(b.depotTransactions, b.depotComptes, b.depotEnveloppes, b.depotAllocations)
    private val maintenant = Date()

    // ===== Exemple de provenance du guide (BLEU / ROUGE) =====

    @Test
    fun `provenance - les 5 etapes du guide`() = runTest {
        b.cheque("bleu", 1000.0, 1000.0)
        b.cheque("rouge", 600.0, 600.0)
        b.enveloppe("epicerie")
        val mois = maintenant

        // 1) 150 $ du PAP bleu : oui, la banque ne bouge pas
        assertTrue(virementsBudget.pretAPlacerVersEnveloppe("bleu", "epicerie", 150.0, mois).isSuccess)
        assertEquals(1000.0, b.soldeDe("bleu")); assertEquals(850.0, b.cheque("bleu").pretAPlacer)
        // 2) +50 $ du PAP bleu : épicerie 200 $
        assertTrue(virementsBudget.pretAPlacerVersEnveloppe("bleu", "epicerie", 50.0, mois).isSuccess)
        assertEquals(200.0, b.allocationDe("epicerie", mois).sumOf { it.solde }); assertEquals(800.0, b.cheque("bleu").pretAPlacer)
        // 3) 50 $ du PAP ROUGE dans une épicerie bleue : refusé, rien ne bouge
        assertTrue(virementsBudget.pretAPlacerVersEnveloppe("rouge", "epicerie", 50.0, mois).isFailure)
        assertEquals(200.0, b.allocationDe("epicerie", mois).sumOf { it.solde }); assertEquals(600.0, b.cheque("rouge").pretAPlacer)
        // 4) Épicerie -> PAP ROUGE : refusé
        assertTrue(virementsBudget.enveloppeVersPretAPlacer("epicerie", "rouge", 30.0, mois).isFailure)
        assertEquals(200.0, b.allocationDe("epicerie", mois).sumOf { it.solde }); assertEquals(600.0, b.cheque("rouge").pretAPlacer)
        // 5) Épicerie -> PAP BLEU 30 $ : oui, banque inchangée
        assertTrue(virementsBudget.enveloppeVersPretAPlacer("epicerie", "bleu", 30.0, mois).isSuccess)
        assertEquals(170.0, b.allocationDe("epicerie", mois).sumOf { it.solde }); assertEquals(830.0, b.cheque("bleu").pretAPlacer)
        assertEquals(1000.0, b.soldeDe("bleu")); assertEquals(600.0, b.soldeDe("rouge"))
    }

    @Test
    fun `virement d'enveloppe dans le mois choisi et non le mois courant`() = runTest {
        b.cheque("bleu", 1000.0, 1000.0)
        b.enveloppe("vacances")
        val mars = date(2027, 3, 10)
        assertTrue(virementsBudget.pretAPlacerVersEnveloppe("bleu", "vacances", 100.0, mars).isSuccess)
        assertEquals(100.0, b.allocationDe("vacances", mars).sumOf { it.solde })
        assertTrue(b.allocationDe("vacances", maintenant).isEmpty())
    }

    @Test
    fun `prêt a placer jamais negatif mais enveloppe negative permise`() = runTest {
        b.cheque("bleu", 1000.0, 40.0)
        b.enveloppe("a"); b.enveloppe("b")
        assertTrue(virementsBudget.pretAPlacerVersEnveloppe("bleu", "a", 50.0, maintenant).isFailure)
        assertTrue(virementsBudget.pretAPlacerVersEnveloppe("bleu", "a", 40.0, maintenant).isSuccess)
        // « a » passe à -10 $ pour combler « b » : permis, le déficit reste visible
        assertTrue(virementsBudget.enveloppeVersEnveloppe("a", "b", 50.0, maintenant).isSuccess)
        assertEquals(-10.0, b.allocationDe("a", maintenant).sumOf { it.solde })
        assertEquals(50.0, b.allocationDe("b", maintenant).sumOf { it.solde })
    }

    // ===== Paiement de carte / dette =====

    @Test
    fun `paiement de carte - une sortie, une entree Paiement, enveloppe utilisee, PAP intact`() = runTest {
        b.cheque("bleu", 1000.0, 0.0)
        b.carte("visa", -400.0, nom = "Visa")
        b.categorie("cc", "Cartes de crédit"); b.enveloppe("envVisa", "Visa", "cc")
        b.allocation("envVisa", maintenant, solde = 400.0, provenance = "bleu")

        assertTrue(argent.effectuerPaiementCarteOuDette("bleu", "comptes_cheques", "visa", "comptes_credits", 150.0, "Visa", maintenant).isSuccess)

        assertEquals(850.0, b.soldeDe("bleu")); assertEquals(0.0, b.cheque("bleu").pretAPlacer)
        assertEquals(-250.0, b.soldeDe("visa"))
        assertEquals(250.0, b.allocationDe("envVisa", maintenant).single().solde)
        val types = b.transactions.values.map { it.type }.sorted()
        assertEquals(listOf(TypeTransaction.Paiement, TypeTransaction.PaiementEffectue), types)
    }

    @Test
    fun `un frais de carte ne rembourse jamais une dette du meme nom`() = runTest {
        b.cheque("bleu", 1000.0, 0.0)
        val frais = com.google.gson.Gson().toJsonTree(arrayOf(FraisMensuel(nom = "Auto", montant = 30.0)))
        b.carte("visa", -400.0, fraisJson = frais)
        b.dette("auto", -1000.0, nom = "Auto")
        assertTrue(argent.effectuerPaiementCarteOuDette("bleu", "comptes_cheques", "visa", "comptes_credits", 100.0, null, maintenant).isSuccess)
        assertEquals(-1000.0, b.soldeDe("auto"))
    }

    @Test
    fun `trop-paye d'une dette refuse sans rien ecrire`() = runTest {
        b.cheque("bleu", 1000.0, 0.0)
        b.dette("auto", -80.0)
        assertTrue(argent.effectuerPaiementCarteOuDette("bleu", "comptes_cheques", "auto", "comptes_dettes", 100.0, null, maintenant).isFailure)
        assertEquals(1000.0, b.soldeDe("bleu")); assertEquals(-80.0, b.soldeDe("auto"))
        assertTrue(b.transactions.isEmpty())
    }

    @Test
    fun `dette soldee archivee avec son enveloppe vide, sans suppression`() = runTest {
        b.cheque("bleu", 1000.0, 0.0)
        b.categorie("cd", "Dettes"); b.enveloppe("envAuto", "Auto", "cd")
        b.allocation("envAuto", maintenant, solde = 80.0, provenance = "bleu")
        b.dette("auto", -80.0, nom = "Auto", detailsJson = DetailsDette(enveloppeId = "envAuto").versJson())
        assertTrue(argent.effectuerPaiementCarteOuDette("bleu", "comptes_cheques", "auto", "comptes_dettes", 80.0, null, maintenant).isSuccess)
        assertTrue((b.comptes.getValue("auto") as CompteDette).estArchive)
        assertTrue(b.enveloppes.getValue("envAuto").estArchive)
        assertTrue(b.categories.containsKey("cd"))
    }

    @Test
    fun `date du paiement et mois de l'enveloppe`() = runTest {
        b.cheque("bleu", 1000.0, 0.0)
        b.categorie("cd", "Dettes"); b.enveloppe("envAuto", "Auto", "cd")
        b.dette("auto", -500.0, nom = "Auto", detailsJson = DetailsDette(enveloppeId = "envAuto").versJson())
        val mars = date(2027, 3, 15)
        b.allocation("envAuto", mars, solde = 100.0, provenance = "bleu")
        assertTrue(argent.effectuerPaiementCarteOuDette("bleu", "comptes_cheques", "auto", "comptes_dettes", 100.0, null, mars).isSuccess)
        assertTrue(b.transactions.values.all { BanqueEnMemoire.memeMois(it.date, mars) })
        assertEquals(0.0, b.allocationDe("envAuto", mars).single().solde)
    }

    // ===== Suppression d'un paiement : les deux moitiés ensemble =====

    @Test
    fun `supprimer un paiement de dette remet exactement tout`() = runTest {
        b.cheque("bleu", 1000.0, 0.0)
        b.categorie("cd", "Dettes"); b.enveloppe("envAuto", "Auto", "cd")
        b.allocation("envAuto", maintenant, solde = 100.0, provenance = "bleu")
        b.dette("auto", -1200.0, nom = "Auto", detailsJson = DetailsDette(enveloppeId = "envAuto").versJson())
        assertTrue(argent.effectuerPaiementCarteOuDette("bleu", "comptes_cheques", "auto", "comptes_dettes", 50.0, null, maintenant).isSuccess)
        assertEquals(-1150.0, b.soldeDe("auto"))

        val sortie = b.transactions.values.single { it.type == TypeTransaction.PaiementEffectue }
        assertTrue(suppression.executer(sortie.id).isSuccess)

        assertTrue(b.transactions.isEmpty())
        assertEquals(1000.0, b.soldeDe("bleu")); assertEquals(0.0, b.cheque("bleu").pretAPlacer)
        assertEquals(-1200.0, b.soldeDe("auto"))
        assertEquals(100.0, b.allocationDe("envAuto", maintenant).single().solde)
        assertEquals(0, (b.comptes.getValue("auto") as CompteDette).paiementEffectue)
    }

    @Test
    fun `supprimer une seule moitie orpheline est refuse`() = runTest {
        b.cheque("bleu", 1000.0, 0.0)
        b.depotTransactions.creerTransaction(
            com.xburnsx.toutiebudget.data.modeles.Transaction(type = TypeTransaction.PaiementEffectue, montant = 50.0, compteId = "bleu", collectionCompte = "comptes_cheques")
        )
        val id = b.transactions.keys.single()
        assertTrue(suppression.executer(id).isFailure)
        assertEquals(1000.0, b.soldeDe("bleu"))
        assertTrue(b.transactions.containsKey(id))
    }

    // ===== Virements entre comptes =====

    @Test
    fun `test principal du guide - cheque vers placement, patrimoine conserve`() = runTest {
        b.cheque("desjardins", 1000.0, 1000.0)
        b.placement("ws", 0.0)
        assertTrue(argent.effectuerVirementEntreComptes("desjardins", "ws", 500.0, "Desjardins", "Wealthsimple", maintenant).isSuccess)
        assertEquals(500.0, b.soldeDe("desjardins")); assertEquals(500.0, b.cheque("desjardins").pretAPlacer)
        assertEquals(500.0, b.soldeDe("ws"))
        assertEquals(1000.0, b.soldeDe("desjardins") + b.soldeDe("ws"))
        assertEquals(2, b.transactions.size)

        // Annuler depuis n'importe quelle moitié remet les deux comptes
        assertTrue(suppression.executer(b.transactions.values.first { it.type == TypeTransaction.TransfertEntrant }.id).isSuccess)
        assertEquals(1000.0, b.soldeDe("desjardins")); assertEquals(1000.0, b.cheque("desjardins").pretAPlacer)
        assertEquals(0.0, b.soldeDe("ws")); assertTrue(b.transactions.isEmpty())
    }

    @Test
    fun `virement qui echoue en cours de route ne perd ni ne cree d'argent`() = runTest {
        b.cheque("desjardins", 1000.0, 1000.0)
        b.placement("ws", 0.0)
        b.echouerSur = "creerTransaction#2"
        assertTrue(argent.effectuerVirementEntreComptes("desjardins", "ws", 500.0, "D", "W", maintenant).isFailure)
        assertEquals(1000.0, b.soldeDe("desjardins")); assertEquals(1000.0, b.cheque("desjardins").pretAPlacer)
        assertEquals(0.0, b.soldeDe("ws")); assertTrue(b.transactions.isEmpty())
    }

    @Test
    fun `virement refuse - meme compte, montant invalide, compte archive`() = runTest {
        b.cheque("a", 100.0, 100.0)
        b.placement("ws", 0.0)
        assertTrue(argent.effectuerVirementEntreComptes("a", "a", 10.0, "A", "A", maintenant).isFailure)
        assertTrue(argent.effectuerVirementEntreComptes("a", "ws", -5.0, "A", "W", maintenant).isFailure)
        assertTrue(argent.effectuerVirementEntreComptes("a", "ws", Double.NaN, "A", "W", maintenant).isFailure)
        b.comptes["ws"] = (b.comptes.getValue("ws") as com.xburnsx.toutiebudget.data.modeles.CompteInvestissement).copy(estArchive = true)
        assertTrue(argent.effectuerVirementEntreComptes("a", "ws", 10.0, "A", "W", maintenant).isFailure)
        assertEquals(100.0, b.soldeDe("a")); assertTrue(b.transactions.isEmpty())
    }

    // ===== Refaire la répartition d'un mois =====

    @Test
    fun `plan reset rend seulement l'assigne non depense, le report reste`() = runTest {
        b.cheque("bleu", 1000.0, 0.0)
        b.enveloppe("epicerie")
        // Report de 50 $ + 100 $ assignés ce mois, 30 $ dépensés -> solde 120 $
        b.allocation("epicerie", maintenant, solde = 120.0, alloue = 100.0, depense = 30.0, provenance = "bleu")
        val service = PlanResetService(b.depotComptes, b.depotEnveloppes, b.depotAllocations, b.historiqueService)
        val apercu = service.preparerApercu(maintenant).getOrThrow()
        assertEquals(70.0, apercu.lignes.single().montant)
        val resultat = service.appliquer(maintenant, setOf("epicerie"))
        assertTrue(resultat.isSuccess, resultat.exceptionOrNull()?.stackTraceToString())
        assertEquals(70.0, b.cheque("bleu").pretAPlacer)
        assertEquals(1000.0, b.soldeDe("bleu"))
        assertEquals(50.0, b.allocationDe("epicerie", maintenant).single().solde)
    }
}
