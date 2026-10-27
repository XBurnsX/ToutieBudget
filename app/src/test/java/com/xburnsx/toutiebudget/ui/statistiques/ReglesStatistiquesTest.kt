package com.xburnsx.toutiebudget.ui.statistiques

import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ReglesStatistiquesTest {
    private fun tx(type: TypeTransaction, montant: Double = 100.0, tiers: String? = null) =
        Transaction(id = type.name + montant, type = type, montant = montant, tiersUtiliser = tiers)

    @Test fun `paiement carte transfert pret et remboursement ne sont pas une nouvelle depense`() {
        listOf(TypeTransaction.PaiementEffectue, TypeTransaction.TransfertSortant, TypeTransaction.Pret, TypeTransaction.RemboursementDonne)
            .forEach { assertFalse(ReglesStatistiques.estDepenseBudget(tx(it))) }
        assertTrue(ReglesStatistiques.estDepenseBudget(tx(TypeTransaction.Depense)))
    }

    @Test fun `emprunt et remboursement recu ne sont pas un revenu gagne`() {
        assertFalse(ReglesStatistiques.estRevenuBudget(tx(TypeTransaction.Emprunt)))
        assertFalse(ReglesStatistiques.estRevenuBudget(tx(TypeTransaction.RemboursementRecu)))
        assertTrue(ReglesStatistiques.estRevenuBudget(tx(TypeTransaction.Revenu)))
    }

    @Test fun `flux de tresorerie garde les mouvements sans les classer au budget`() {
        assertTrue(ReglesStatistiques.estSortieTresorerie(tx(TypeTransaction.PaiementEffectue)))
        assertTrue(ReglesStatistiques.estEntreeTresorerie(tx(TypeTransaction.Emprunt)))
        assertTrue(ReglesStatistiques.estEntreeTresorerie(tx(TypeTransaction.TransfertEntrant)))
    }

    @Test fun `correction technique est exclue et sommes sont au cent`() {
        val transactions = listOf(
            tx(TypeTransaction.Depense, 0.105),
            tx(TypeTransaction.Depense, 0.105),
            tx(TypeTransaction.Depense, 50.0, "Mise à jour : rapprochement")
        )
        assertEquals(0.22, ReglesStatistiques.totalAuCent(transactions, ReglesStatistiques::estDepenseBudget), 0.0001)
    }

    @Test fun `fractions acceptent ancienne et nouvelle cle mais pas total incomplet`() {
        val ancien = tx(TypeTransaction.Depense, 10.0).copy(
            estFractionnee = true,
            sousItems = """[{"enveloppeId":"epicerie","montant":4.0},
                             {"enveloppe_id":"loyer","montant":6.0}]"""
        )
        val fractions = ReglesStatistiques.lireFractions(ancien)
        assertEquals(2, fractions.size)
        assertEquals("epicerie", fractions.first().enveloppeId)
        assertEquals("loyer", fractions.last().enveloppeId)
        val fauxTotal = ancien.copy(sousItems = """[{"enveloppe_id":"epicerie","montant":4.0}]""")
        assertTrue(runCatching { ReglesStatistiques.lireFractions(fauxTotal) }.isFailure)
    }

    @Test fun `SA5 moyenne quotidiennes ne comptent que les depenses et jamais les virements`() {
        val a = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val b = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 7, 23, 59, 59); set(Calendar.MILLISECOND, 999)
        }.time
        val transactions = listOf(
            tx(TypeTransaction.Depense, 70.0).copy(date = a),
            tx(TypeTransaction.TransfertSortant, 700.0).copy(date = a),
            tx(TypeTransaction.PaiementEffectue, 70.0).copy(date = a),
            tx(TypeTransaction.Revenu, 1000.0).copy(date = a)
        )
        assertEquals(10.0, ReglesStatistiques.moyenneDepensesQuotidienne(
            transactions, a, b, 7, maintenant = b
        ) ?: -1.0, 0.0001)
    }

    @Test fun `aucune moyenne actuelle pour une periode entierement future`() {
        val maintenant = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 12, 0, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val debutFutur = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val finFuture = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 30, 23, 59, 59); set(Calendar.MILLISECOND, 999)
        }.time
        assertTrue(ReglesStatistiques.moyenneDepenses7Jours(
            emptyList(), finFuture, maintenant, dateDebutPeriode = debutFutur
        ).isEmpty())
    }

    @Test fun `moyenne 7 jours inclut toute la derniere journee et exclut les jours futurs`() {
        val maintenant = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 12, 0, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val soir = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 23, 59, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val finFuture = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 31, 23, 59, 59); set(Calendar.MILLISECOND, 999)
        }.time
        val resultat = ReglesStatistiques.moyenneDepenses7Jours(
            listOf(Transaction(id = "soir", type = TypeTransaction.Depense, montant = 70.0, date = soir)),
            finFuture,
            maintenant
        )
        assertEquals(10.0, resultat.last().second, 0.0001)
    }
}
