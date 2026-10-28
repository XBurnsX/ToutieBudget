package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.room.entities.SyncJob
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TendanceEtSyncEnveloppeTest {
    private val zone = ZoneId.of("America/Toronto")

    private fun ligne(id: String, mois: String, type: TypeTransaction, portion: Double) =
        TransactionUi(
            id = id, type = type, montant = portion,
            date = Date.from(LocalDate.parse(mois + "-15").atStartOfDay(zone).toInstant()),
            tiersUtiliser = "Commerce", nomEnveloppe = "Essence", note = null
        )

    @Test fun evolutionParMoisAdditionneLesPortionsPasLesAchatsComplets() {
        val janvier = ligne("janv", "2026-01", TypeTransaction.Depense, 10.25)
            .copy(estFractionnee = true, montantTotalTransaction = 120.25)
        val fevrier = ligne("fev", "2026-02", TypeTransaction.Depense, 20.70)
        val revenu = ligne("r", "2026-02", TypeTransaction.Revenu, 3.50)
        val paiement = ligne("p", "2026-02", TypeTransaction.Paiement, 15.00)
        val tendance = TendanceMensuelleEnveloppe.calculer(
            listOf(janvier, fevrier, revenu, paiement, fevrier), zone
        )
        assertEquals(listOf("2026-02", "2026-01"), tendance.map { it.mois.toString() })
        assertEquals(2070L, tendance[0].depensesCentimes)
        assertEquals(350L, tendance[0].revenusCentimes)
        assertEquals(1025L, tendance[1].depensesCentimes)
        assertEquals(2, tendance[0].mouvements)
    }

    private fun job(id: String, statut: String, type: String = "TRANSACTION") =
        SyncJob(
            id = "job-" + id, type = type, action = "UPDATE",
            dataJson = """{"id":"t1"}""",
            recordId = id, status = statut, utilisateurId = "u"
        )

    @Test fun attenteEtEchecAffichentLesStatutsVerifiables() {
        assertEquals("En attente d'envoi", StatutSyncHistorique.libelle("t1", "u",
            listOf(job("t1", "PENDING"))))
        assertEquals("Échec d'envoi local", StatutSyncHistorique.libelle("t1", "u",
            listOf(job("t1", "PENDING"), job("t1", "FAILED"))))
    }

    @Test fun aucuneTacheNeProuveJamaisUnSuccesDistant() {
        assertTrue(StatutSyncHistorique.libelle("t1", "u", emptyList()).contains("Non vérifié"))
        assertTrue(StatutSyncHistorique.libelle("t1", "u",
            listOf(job("t1", "PENDING", "COMPTE"))).contains("Non vérifié"))
        assertTrue(StatutSyncHistorique.libelle("t1", "u",
            listOf(job("t1", "PENDING").copy(utilisateurId = "autre"))).contains("Non vérifié"))
    }

    @Test fun legacyJobSansRecordIdEstReconnuAvecSonJson() {
        val legacy = job("", "PENDING").copy(dataJson = """{"id":"t1"}""")
        assertEquals("En attente d'envoi", StatutSyncHistorique.libelle("t1", "u", listOf(legacy)))
    }
}
