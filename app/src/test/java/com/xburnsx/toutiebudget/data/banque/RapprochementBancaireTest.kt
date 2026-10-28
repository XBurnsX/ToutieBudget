package com.xburnsx.toutiebudget.data.banque

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RapprochementBancaireTest {
    private val date = LocalDate.of(2026, 10, 28)
    private val bancaire = OperationImporteeCandidate(
        utilisateurId = "tester", compteId = "ws",
        collectionCompte = "comptes_cheques", date = date,
        montantCentimes = 500L, libelleBancaireBrut = "IRV 0421 QC",
        tiersReconnuId = "tiers-irving"
    )
    private fun candidate(
        id: String, compte: String = "ws", montant: Long = 500,
        utilisateur: String = "tester", dateSaisie: LocalDate = date,
        tiers: String? = "tiers-irving", collection: String = "comptes_cheques"
    ) = DepenseSaisieCandidate(
        transactionId = id, utilisateurId = utilisateur,
        compteId = compte, collectionCompte = collection,
        date = dateSaisie, montantCentimes = montant,
        tiersId = tiers, libelle = "Irving Port-Cartier"
    )

    @Test fun memeCompteEtMontantEstProposeSansJumeler() {
        val resultats = RapprochementBancaire.proposer(bancaire, listOf(candidate("tx1")))
        assertEquals(1, resultats.size)
        assertEquals("tx1", resultats.single().transactionId)
        assertFalse(resultats.single().montantDifferent)
    }

    @Test fun jamaisUnAutreUtilisateurOuUnAutreCompte() {
        val resultats = RapprochementBancaire.proposer(bancaire, listOf(
            candidate("autreUser", utilisateur = "autre"),
            candidate("autreCompte", compte = "visa"),
            candidate("autreCollection", collection = "comptes_credits")
        ))
        assertTrue(resultats.isEmpty())
    }

    @Test fun memeMontantPlusieursFoisPresenteTousLesCandidats() {
        val resultat = RapprochementBancaire.proposer(bancaire, listOf(
            candidate("achat1"), candidate("achat2")
        ))
        assertEquals(2, resultat.size)
        assertEquals(setOf("achat1", "achat2"), resultat.map { it.transactionId }.toSet())
    }

    @Test fun montantDifferentExigeUnIndiceDeTiersOuDeLibelle() {
        val ecartPossible = RapprochementBancaire.proposer(
            bancaire, listOf(candidate("pourboire", montant = 550)))
        assertEquals(1, ecartPossible.size)
        assertTrue(ecartPossible.single().montantDifferent)
        val sansIdentite = RapprochementBancaire.proposer(bancaire, listOf(
            candidate("inconnu", montant = 550, tiers = null)))
        assertTrue(sansIdentite.isEmpty())
    }

    @Test fun horsFenetreDateEtMontantNegatifRefuses() {
        assertTrue(RapprochementBancaire.proposer(
            bancaire, listOf(candidate("ancien", dateSaisie = date.minusDays(6)))).isEmpty())
        assertTrue(RapprochementBancaire.proposer(
            bancaire, listOf(candidate("refund", montant = -500))).isEmpty())
    }

    @Test fun pasDeModificationDesDonneesDentree() {
        val originaux = listOf(candidate("tx1"))
        RapprochementBancaire.proposer(bancaire, originaux)
        assertEquals("tx1", originaux.single().transactionId)
        assertEquals(500L, originaux.single().montantCentimes)
    }
}
