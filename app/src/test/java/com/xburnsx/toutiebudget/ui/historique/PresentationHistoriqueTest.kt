package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationHistoriqueTest {
    @Test fun paiementSurCarteEstEntreeEtPaiementDuChequeEstSortie() {
        assertEquals(
            SensMontantHistorique.Sens.ENTREE,
            SensMontantHistorique.determiner(TypeTransaction.Paiement, "comptes_credits")
        )
        assertEquals(
            SensMontantHistorique.Sens.SORTIE,
            SensMontantHistorique.determiner(TypeTransaction.PaiementEffectue, "comptes_cheques")
        )
    }

    @Test fun depenseCarteResteUneSortieEtRemboursementRecuResteEntree() {
        assertEquals(
            SensMontantHistorique.Sens.SORTIE,
            SensMontantHistorique.determiner(TypeTransaction.Depense, "comptes_credits")
        )
        assertEquals(
            SensMontantHistorique.Sens.ENTREE,
            SensMontantHistorique.determiner(TypeTransaction.RemboursementRecu, "comptes_cheques")
        )
    }

    @Test fun archiveConserveSonNomReelEtAfficheSonEtat() {
        val archive = Enveloppe(
            id = "auto", utilisateurId = "u", nom = "Essence",
            categorieId = "transport", estArchive = true, ordre = 0
        )
        assertEquals(
            "Essence (archivée)",
            LibellesHistorique.enveloppe("auto", mapOf("auto" to archive))
        )
    }

    @Test fun referencePerdueNestPasRebaptiseeSansEnveloppe() {
        val resultat = LibellesHistorique.enveloppe("env_historique_42", emptyMap())
        assertTrue(resultat.contains("introuvable"))
        assertTrue(resultat.contains("env_historique_42"))
        assertTrue(LibellesHistorique.allocationIntrouvable("allocation_21").contains("allocation_21"))
    }
}
