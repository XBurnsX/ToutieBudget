package com.xburnsx.toutiebudget.data.services

import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendrierRenouvellementObjectifTest {
    private val zone = ZoneId.of("America/Toronto")
    private fun date(v: String): Date =
        Date.from(LocalDate.parse(v).atStartOfDay(zone).toInstant())
    private fun jour(v: Date?) = v!!.toInstant().atZone(zone).toLocalDate()

    private fun enveloppe(type: TypeObjectif, debut: String, fin: String) =
        Enveloppe(
            id = "e1", utilisateurId = "u1", nom = "Épargne",
            categorieId = "c1", typeObjectif = type, objectifMontant = 100.0,
            dateDebutObjectif = date(debut), dateObjectif = date(fin),
            resetApresEcheance = true, estArchive = false, ordre = 0
        )

    @Test fun aucunRenouvellementAvantLeLendemainDeLaDateLimite() {
        val source = enveloppe(TypeObjectif.Bihebdomadaire, "2026-10-12", "2026-10-26")
        assertNull(CalendrierRenouvellementObjectif.prochaineVersion(
            source, LocalDate.parse("2026-10-26"), zone
        ))
        val suivant = CalendrierRenouvellementObjectif.prochaineVersion(
            source, LocalDate.parse("2026-10-27"), zone
        )!!
        assertEquals(LocalDate.parse("2026-11-09"), jour(suivant.dateObjectif))
        assertEquals(LocalDate.parse("2026-10-26"), jour(suivant.dateDebutObjectif))
    }

    @Test fun quatreCyclesManquesNeResetentQuUneFoisEtNeTouchentPasMontant() {
        val source = enveloppe(TypeObjectif.Bihebdomadaire, "2026-01-01", "2026-01-15")
        val cible = LocalDate.parse("2026-03-01")
        val version = CalendrierRenouvellementObjectif.prochaineVersion(source, cible, zone)!!
        assertEquals(LocalDate.parse("2026-03-12"), jour(version.dateObjectif))
        assertEquals(LocalDate.parse("2026-02-26"), jour(version.dateDebutObjectif))
        assertEquals(source.objectifMontant, version.objectifMontant, 0.00001)
        assertNull(CalendrierRenouvellementObjectif.prochaineVersion(version, cible, zone))
    }

    @Test fun echeanceTraverseHeureEteSansPerdreUneJournee() {
        val ancienne = enveloppe(TypeObjectif.Echeance, "2026-03-01", "2026-03-15")
        val version = CalendrierRenouvellementObjectif.prochaineVersion(
            ancienne, LocalDate.parse("2026-03-16"), zone
        )!!
        assertEquals(LocalDate.parse("2026-03-15"), jour(version.dateDebutObjectif))
        assertEquals(LocalDate.parse("2026-03-29"), jour(version.dateObjectif))
    }

    @Test fun echeanceSansResetEtEnveloppeArchiveeSontIntouchables() {
        val ancienne = enveloppe(TypeObjectif.Echeance, "2025-01-01", "2025-02-01")
        assertNull(CalendrierRenouvellementObjectif.prochaineVersion(
            ancienne.copy(resetApresEcheance = false), LocalDate.parse("2026-10-28"), zone
        ))
        assertNull(CalendrierRenouvellementObjectif.prochaineVersion(
            ancienne.copy(estArchive = true), LocalDate.parse("2026-10-28"), zone
        ))
    }

    @Test fun annuelRattrapeDeuxAnneesSansModifierLeCapital() {
        val ancienne = enveloppe(TypeObjectif.Annuel, "2023-02-28", "2024-02-29")
        val version = CalendrierRenouvellementObjectif.prochaineVersion(
            ancienne, LocalDate.parse("2026-10-28"), zone
        )!!
        assertEquals(LocalDate.parse("2027-02-28"), jour(version.dateObjectif))
        assertEquals(LocalDate.parse("2026-02-28"), jour(version.dateDebutObjectif))
        assertEquals(ancienne.objectifMontant, version.objectifMontant, 0.00001)
    }
}
