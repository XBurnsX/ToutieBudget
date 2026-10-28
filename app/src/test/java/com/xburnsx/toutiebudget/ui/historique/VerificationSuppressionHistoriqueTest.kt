package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import java.util.Date
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aucune écriture financière : comparaison de versions sur des données de lecture. */
class VerificationSuppressionHistoriqueTest {
    private val date = Date(1_791_000_000_000L)
    private val ecriture = Transaction(
        id = "achat", utilisateurId = "u", type = TypeTransaction.Depense,
        montant = 60.0, date = date, note = "Essence",
        compteId = "chq", collectionCompte = "comptes_cheques",
        tiersUtiliser = "Station", estFractionnee = true,
        sousItems = """[{"enveloppeId":"auto","montant":20.00},{"enveloppeId":"courses","montant":40.00}]"""
    )

    private val portion = TransactionUi(
        id = "achat", type = TypeTransaction.Depense, montant = 20.0,
        montantTotalTransaction = 60.0, date = date,
        tiersUtiliser = "Station", tiersBrut = "Station",
        nomEnveloppe = "Auto", note = "Essence",
        compteId = "chq", collectionCompte = "comptes_cheques",
        estFractionnee = true, sousItems = ecriture.sousItems
    )

    @Test fun confirmeAvecLaPortionMaisCompareLeTotalBancaire() {
        assertTrue(VerificationSuppressionHistorique.estEncoreLaMeme(portion, ecriture))
    }

    @Test fun refuseMontantModifieDepuisLaffichage() {
        assertFalse(VerificationSuppressionHistorique.estEncoreLaMeme(
            portion, ecriture.copy(montant = 61.0)))
    }

    @Test fun refuseChangementDateOuTiers() {
        assertFalse(VerificationSuppressionHistorique.estEncoreLaMeme(
            portion, ecriture.copy(date = Date(date.time + 86_400_000L))))
        assertFalse(VerificationSuppressionHistorique.estEncoreLaMeme(
            portion, ecriture.copy(tiersUtiliser = "Autre")))
    }

    @Test fun refuseModificationDeVentilationMemeSiTotalIdentique() {
        assertFalse(VerificationSuppressionHistorique.estEncoreLaMeme(
            portion, ecriture.copy(sousItems =
                """[{"enveloppeId":"auto","montant":30.00},{"enveloppeId":"courses","montant":30.00}]""")))
    }

    @Test fun refuseAutreCompteEtRevisionMiseAJour() {
        assertFalse(VerificationSuppressionHistorique.estEncoreLaMeme(
            portion, ecriture.copy(compteId = "autre")))
        assertFalse(VerificationSuppressionHistorique.estEncoreLaMeme(
            portion.copy(revisionTransaction = Date(1234L)),
            ecriture.copy(updated = Date(5678L))))
    }

    @Test fun refuseModificationDeLaNoteOuDeLallocation() {
        assertFalse(VerificationSuppressionHistorique.estEncoreLaMeme(
            portion, ecriture.copy(note = "Corrigée")))
        assertFalse(VerificationSuppressionHistorique.estEncoreLaMeme(
            portion, ecriture.copy(allocationMensuelleId = "une-allocation")))
    }
}
