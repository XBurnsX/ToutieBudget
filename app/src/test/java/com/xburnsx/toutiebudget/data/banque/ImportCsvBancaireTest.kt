package com.xburnsx.toutiebudget.data.banque

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportCsvBancaireTest {
    @Test fun montantEtLibelleCsv() {
        val csv = """date,amount,description,id,status,currency
2026-10-28,-5.00,"Irving, Port-Cartier",tx1,posted,CAD
2026-10-27,10.25,Remboursement,tx2,pending,CAD"""
        val lignes = ImportCsvBancaire.analyser(csv)
        assertEquals(2, lignes.size)
        assertEquals(-500L, lignes[0].montantCentimes)
        assertEquals("Irving, Port-Cartier", lignes[0].libelleBrut)
        assertEquals("EN_ATTENTE", lignes[1].statut)
    }
    @Test fun csvFrancaisEtCentimes() {
        assertEquals(-525L, ImportCsvBancaire.analyser(
            "date;montant;libellé\n2026-10-28;-5,25;Irving").single().montantCentimes)
    }
    @Test fun achatsIdentiquesConserventDeuxIdentifiantsDistincts() {
        val texte = "date,montant,description\n2026-10-28,-5,Irving\n2026-10-28,-5,Irving"
        val a = ImportCsvBancaire.analyser(texte)
        assertEquals(2, a.size)
        assertTrue(a[0].idExterne != a[1].idExterne)
        assertEquals(a.map { it.idExterne }, ImportCsvBancaire.analyser(texte).map { it.idExterne })
    }
    @Test(expected = IllegalArgumentException::class)
    fun refuseDeviseUsd() {
        ImportCsvBancaire.analyser("date,amount,description,currency\n2026-10-28,-5,Irving,USD")
    }
    @Test(expected = IllegalArgumentException::class)
    fun refuseDateAmbigue() {
        ImportCsvBancaire.analyser("date,amount,description\n10/11/2026,-5,Irving")
    }
    @Test(expected = IllegalArgumentException::class)
    fun refuseMontantFractionCentime() {
        ImportCsvBancaire.analyser("date,amount,description\n2026-10-28,5.001,Irving")
    }
}
