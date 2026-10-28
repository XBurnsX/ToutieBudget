package com.xburnsx.toutiebudget.ui.categories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VerificationArchivageEnveloppeTest {
    @Test fun archiveSeulementLesSoldesExactementNuls() {
        assertEquals(0L, VerificationArchivageEnveloppe.verifierSoldes(emptyList()))
        assertEquals(0L, VerificationArchivageEnveloppe.verifierSoldes(listOf(0.0, -0.0)))
    }

    @Test fun interditSoldesPositifsEtNegatifsMemeAnciens() {
        for (montant in listOf(0.01, -0.01, 50.0, -99.0)) {
            assertThrows(IllegalArgumentException::class.java) {
                VerificationArchivageEnveloppe.verifierSoldes(listOf(0.0, montant, 0.0))
            }
        }
    }

    @Test fun interditNonFiniOuFractionsDeCentime() {
        for (montant in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.001, -0.009)) {
            assertThrows(IllegalArgumentException::class.java) {
                VerificationArchivageEnveloppe.verifierSoldes(listOf(montant))
            }
        }
    }

    @Test fun montantValideEstConvertiSansPerteAuCentime() {
        assertEquals(105L, VerificationArchivageEnveloppe.centimesExact(1.05))
        assertEquals(-105L, VerificationArchivageEnveloppe.centimesExact(-1.05))
    }
}
