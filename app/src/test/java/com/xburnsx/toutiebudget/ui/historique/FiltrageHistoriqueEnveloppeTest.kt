package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltrageHistoriqueEnveloppeTest {
    private fun ligne(id: String, compte: String, collection: String, date: String, montant: Double) =
        TransactionUi(
            id = id, type = TypeTransaction.Depense,
            montant = montant,
            date = Date.from(LocalDate.parse(date).atStartOfDay(ZoneId.of("America/Toronto")).toInstant()),
            tiersUtiliser = "Irving", nomEnveloppe = "Essence", note = null,
            compteId = compte, collectionCompte = collection
        )

    @Test fun filtreSourceDifferencieDeuxComptesMemeIdEtConserveLaPortion() {
        val lignes = listOf(
            ligne("cheque", "meme-id", "comptes_cheques", "2026-10-12", 15.55),
            ligne("carte", "meme-id", "comptes_credits", "2026-10-12", 40.45),
            ligne("ancien", "meme-id", "comptes_cheques", "2025-10-12", 25.00)
        )
        val resultats = FiltrageHistoriqueEnveloppe.appliquer(
            lignes, "IRV", PeriodeHistorique(VuePeriodeHistorique.MOIS, 2026, 10),
            CompteSourceHistorique("meme-id", "comptes_cheques")
        )
        assertEquals(listOf("cheque"), resultats.map { it.id })
        assertEquals(15.55, resultats.single().montant, 0.000001)
    }

    @Test fun aucunFiltreConserveToutEtRequeteIntrouvableRendVide() {
        val lignes = listOf(
            ligne("cheque", "a", "comptes_cheques", "2026-10-12", 20.0),
            ligne("visa", "b", "comptes_credits", "2026-11-03", 40.0)
        )
        assertEquals(lignes, FiltrageHistoriqueEnveloppe.appliquer(
            lignes, "", PeriodeHistorique(VuePeriodeHistorique.TOUTES), null
        ))
        assertTrue(FiltrageHistoriqueEnveloppe.appliquer(
            lignes, "marchand absent", PeriodeHistorique(VuePeriodeHistorique.TOUTES), null
        ).isEmpty())
    }

    @Test fun rechercheTrouveDescriptionEtNoteDeLaFraction() {
        val achat = ligne("achat", "chq", "comptes_cheques", "2026-10-28", 21.99)
            .copy(estFractionnee = true, sousItems = """
                [{"enveloppeId":"essence","montant":21.99,
                  "description":"Essence ordinaire","note":"Pompe numéro 3"}]
            """.trimIndent())
        assertTrue(RechercheHistorique.correspond(achat, "pompe numero 3"))
        assertTrue(RechercheHistorique.correspond(achat, "ESSENCE ORDINAIRE"))
    }
}
