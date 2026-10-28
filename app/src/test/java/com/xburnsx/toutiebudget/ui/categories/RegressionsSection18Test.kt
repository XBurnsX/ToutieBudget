package com.xburnsx.toutiebudget.ui.categories

import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.data.services.ObjectifResetService
import com.xburnsx.toutiebudget.fakes.BanqueEnMemoire
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegressionsSection18Test {
    private val zone = ZoneId.of("America/Toronto")
    private fun date(v: String) = Date.from(
        LocalDate.parse(v).atStartOfDay(zone).toInstant()
    )

    @Test fun renouvelerCycleNeModifieJamaisAllocationsExistantes() = runTest {
        val b = BanqueEnMemoire()
        b.enveloppes["essence"] = b.enveloppe("essence").copy(
            typeObjectif = TypeObjectif.Bihebdomadaire,
            objectifMontant = 250.0,
            dateDebutObjectif = date("2025-01-01"),
            dateObjectif = date("2025-01-15")
        )
        val historique = b.allocation(
            "essence", date("2025-01-01"),
            solde = 120.0, alloue = 300.0, depense = 180.0
        )
        val service = ObjectifResetService(b.depotEnveloppes, b.depotAllocations)

        val premier = service.verifierEtResetterObjectifsBihebdomadaires()
        assertTrue(premier.isSuccess)
        assertEquals(1, premier.getOrThrow().size)
        assertEquals(historique, b.allocations.getValue(historique.id))
        assertEquals(1, b.allocations.size)
        val apres = b.enveloppes.getValue("essence")
        assertTrue(apres.dateObjectif!!.after(Date()))

        val deuxieme = service.verifierEtResetterObjectifsBihebdomadaires()
        assertTrue(deuxieme.isSuccess)
        assertTrue(deuxieme.getOrThrow().isEmpty())
        assertEquals(historique, b.allocations.getValue(historique.id))
    }

    @Test fun archiverAvecSoldeNonNulEstRefuseSansPerdreLesLiens() = runTest {
        val b = BanqueEnMemoire()
        b.enveloppe("e", nom = "Factures")
        val allocation = b.allocation("e", date("2025-08-01"), solde = -0.01)
        val resultat = b.depotEnveloppes.archiverEnveloppeApresVerification("e")
        assertTrue(resultat.isFailure)
        assertFalse(b.enveloppes.getValue("e").estArchive)
        assertEquals(allocation, b.allocations.getValue(allocation.id))
    }

    @Test fun archivageAvecSoldeZeroPreserveIdEtAllocations() = runTest {
        val b = BanqueEnMemoire()
        b.enveloppe("e", nom = "Factures")
        val allocation = b.allocation("e", date("2025-08-01"), solde = 0.0, alloue = 10.0)
        assertTrue(b.depotEnveloppes.archiverEnveloppeApresVerification("e").isSuccess)
        assertTrue(b.enveloppes.getValue("e").estArchive)
        assertEquals(allocation, b.allocations.getValue(allocation.id))
        assertTrue(b.depotEnveloppes.archiverEnveloppeApresVerification("e").isSuccess)
    }
}
