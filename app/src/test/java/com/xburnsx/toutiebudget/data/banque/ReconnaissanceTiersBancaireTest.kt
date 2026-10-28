package com.xburnsx.toutiebudget.data.banque

import com.xburnsx.toutiebudget.data.modeles.Tiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReconnaissanceTiersBancaireTest {
    private val irving = Tiers(id = "tiers1", nom = "Irving Port-Cartier", utilisateur_id = "tester")
    private val autre = Tiers(id = "tiers2", nom = "Autre Irving", utilisateur_id = "tester")
    private fun regle(id: String, compte: String?, brut: String, tiers: String = "tiers1") =
        RegleAliasBancaire(id, "tester", compte, brut, tiers)

    @Test fun normaliseAccentsCasseEtEspaces() {
        assertEquals("IRV 0421 QC", ReconnaissanceTiersBancaire.normaliser("  irv  0421  qç  "))
    }

    @Test fun reutiliseTiersExistantSansDuplication() {
        assertEquals(irving, ReconnaissanceTiersBancaire.reconnaitre(
            "tester", "wealthsimple", " irv 0421 qc ",
            listOf(regle("r1", "wealthsimple", "IRV 0421 QC")), listOf(irving, autre)))
    }

    @Test fun neConfondNiTerminusNiComptes() {
        val regles = listOf(regle("r1", "wealthsimple", "IRV 0421 QC"))
        assertNull(ReconnaissanceTiersBancaire.reconnaitre(
            "tester", "wealthsimple", "IRV 9999 QC", regles, listOf(irving)))
        assertNull(ReconnaissanceTiersBancaire.reconnaitre(
            "tester", "visa", "IRV 0421 QC", regles, listOf(irving)))
    }

    @Test fun regleSpecifiquePrimeSurRegleGenerale() {
        val regles = listOf(
            regle("global", null, "IRV 0421 QC", "tiers2"),
            regle("specifique", "wealthsimple", "IRV 0421 QC"))
        assertEquals(irving, ReconnaissanceTiersBancaire.reconnaitre(
            "tester", "wealthsimple", "IRV 0421 QC", regles, listOf(irving, autre)))
        assertEquals(autre, ReconnaissanceTiersBancaire.reconnaitre(
            "tester", "visa", "IRV 0421 QC", regles, listOf(irving, autre)))
    }

    @Test fun conflitRefuseSelectionAutomatique() {
        assertNull(ReconnaissanceTiersBancaire.reconnaitre(
            "tester", "visa", "IRV 0421 QC",
            listOf(regle("a", null, "IRV 0421 QC"), regle("b", null, "IRV 0421 QC", "tiers2")),
            listOf(irving, autre)))
    }

    @Test fun regleDesactiveeTiersAbsentEtUtilisateurDifferent() {
        assertNull(ReconnaissanceTiersBancaire.reconnaitre(
            "tester", "visa", "IRV 0421 QC",
            listOf(regle("a", null, "IRV 0421 QC").copy(active = false)), listOf(irving)))
        assertNull(ReconnaissanceTiersBancaire.reconnaitre(
            "tester", "visa", "IRV 0421 QC",
            listOf(regle("a", null, "IRV 0421 QC")), emptyList()))
        assertNull(ReconnaissanceTiersBancaire.reconnaitre(
            "autre", "visa", "IRV 0421 QC",
            listOf(regle("a", null, "IRV 0421 QC")), listOf(irving)))
    }
}
