package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.modeles.AjustementDette
import com.xburnsx.toutiebudget.data.modeles.ChangementTaux
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.data.modeles.DetailsDette
import com.xburnsx.toutiebudget.data.modeles.FrequencePaiementDette
import com.xburnsx.toutiebudget.data.modeles.ModeDette
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeAjustementDette
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import org.junit.Test
import java.util.Calendar
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Section 11 : l'exemple du TXT (1 200 $ à 0 % sur 12 mois) et les règles sans valeur inventée. */
class MoteurDetteTest {

    /** Contrat commencé il y a 6 mois, versements le 15. */
    private val debutContrat: String = java.text.SimpleDateFormat("yyyy-MM-dd").format(
        Calendar.getInstance().apply { add(Calendar.MONTH, -6) }.time
    )

    private fun dette(
        solde: Double,
        initial: Double = 1200.0,
        taux: Double? = 0.0,
        duree: Int? = 12,
        versement: Double? = null,
        // DA2 : un contrat a un jour de versement connu (le moteur n'en invente plus)
        details: DetailsDette = DetailsDette(mode = ModeDette.FINANCE, jourVersement = 15, dateDebut = debutContrat)
    ) = CompteDette(
        id = "d1", nom = "Auto", soldeDette = solde, estArchive = false, ordre = 0, montantInitial = initial,
        tauxInteret = taux, paiementMinimum = versement, dureeMoisPret = duree, detailsDetteJson = details.versJson()
    )

    private fun paiement(montant: Double, joursAvant: Int = 0) = Transaction(
        type = TypeTransaction.Paiement, montant = montant, compteId = "d1", collectionCompte = "comptes_dettes",
        date = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -joursAvant) }.time
    )

    @Test
    fun `exemple du TXT - payer 50 dollars laisse 1150`() {
        val d = dette(solde = -1150.0)
        assertEquals(1150.0, MoteurDette.restant(d))
        assertEquals(100.0, MoteurDette.versementPrevu(d))
        // L'échéancier part du solde RÉEL : 1 150 $ à 100 $/mois = 12 versements (le dernier de 50 $)
        val e = assertNotNull(MoteurDette.echeancier(d))
        assertEquals(12, e.nombrePeriodes)
        assertEquals(50.0, e.periodes.last().versement)
        assertEquals(0.0, e.totalInterets)
    }

    @Test
    fun `changer le taux ou la duree ne touche pas le solde reel`() {
        val avant = dette(solde = -1150.0)
        val apres = avant.copy(tauxInteret = 5.0, dureeMoisPret = 24)
        assertEquals(MoteurDette.restant(avant), MoteurDette.restant(apres))
    }

    @Test
    fun `mensualite d'amortissement`() {
        // 10 000 $ à 6 % sur 60 mois = 193,33 $
        assertEquals(193.33, MoteurDette.mensualiteContractuelle(10000.0, 6.0, 60))
        assertEquals(100.0, MoteurDette.mensualiteContractuelle(1200.0, 0.0, 12))
    }

    @Test
    fun `pret deja commence calcule cinq paiements et le solde actuel`() {
        val debut = assertNotNull(MoteurCarteCredit.lireDate("2026-05-05"))
        val aujourdhui = assertNotNull(MoteurCarteCredit.lireDate("2026-10-27"))
        val s = assertNotNull(MoteurDette.situationContrat(
            dateDebut = debut,
            dureeMois = 36,
            mensualite = 189.21,
            tauxAnnuel = 10.0,
            aujourdHui = aujourdhui,
            principalInitial = 5863.81
        ))
        assertEquals(5, s.paiementsEcoules)
        assertEquals(5863.81, s.principalInitial)
        assertEquals(5150.31, s.soldeActuel)
        assertEquals(713.50, s.capitalPaye)
        assertEquals(232.55, s.interetsPayes)
        assertEquals("2029-05-05", MoteurCarteCredit.ecrireDate(s.dateFin))
        assertEquals(5, s.jourVersement)
    }

    @Test
    fun `paiement aux deux semaines compte les vraies periodes de quatorze jours`() {
        val debut = assertNotNull(MoteurCarteCredit.lireDate("2026-05-05"))
        val aujourdhui = assertNotNull(MoteurCarteCredit.lireDate("2026-06-03"))
        val s = assertNotNull(MoteurDette.situationContrat(
            dateDebut = debut,
            dureeMois = 36,
            mensualite = 200.0,
            tauxAnnuel = 0.0,
            aujourdHui = aujourdhui,
            principalInitial = 10000.0,
            frequence = FrequencePaiementDette.AUX_DEUX_SEMAINES
        ))
        assertEquals(78, s.nombrePaiements)
        assertEquals(2, s.paiementsEcoules)
        assertEquals(9600.0, s.soldeActuel)
        assertEquals("2026-06-16", MoteurCarteCredit.ecrireDate(assertNotNull(s.prochaineDatePaiement)))
    }

    /**
     * Cas réel (relevé de la banque) : 21 436,23 $ à 14,99 %, 234,11 $ aux deux semaines depuis le 10 mars 2023.
     * La banque compte les intérêts au jour près et a pris le 1er paiement le 17 mars (7 jours après).
     * Relevé du 29 octobre 2026 : 7 371,24 $ (intérêts quotidiens 3,03 $).
     */
    @Test
    fun `aux deux semaines avec 1er paiement reel - meme solde que la banque`() {
        val s = assertNotNull(MoteurDette.situationContrat(
            dateDebut = assertNotNull(MoteurCarteCredit.lireDate("2023-03-10")),
            dureeMois = 59,
            mensualite = 234.11,
            tauxAnnuel = 14.99,
            aujourdHui = assertNotNull(MoteurCarteCredit.lireDate("2026-10-29")),
            principalInitial = 21436.23,
            frequence = FrequencePaiementDette.AUX_DEUX_SEMAINES,
            premierPaiement = MoteurCarteCredit.lireDate("2023-03-17")
        ))
        assertEquals(95, s.paiementsEcoules)
        assertEquals(7370.88, s.soldeActuel) // banque : 7 371,24 $ (écart d'arrondi de 36 ¢)
        assertEquals("2026-11-06", MoteurCarteCredit.ecrireDate(assertNotNull(s.prochaineDatePaiement)))
        assertEquals(17, s.jourVersement)
    }

    @Test
    fun `aux deux semaines sans 1er paiement - une periode apres le debut, comme avant`() {
        val s = assertNotNull(MoteurDette.situationContrat(
            dateDebut = assertNotNull(MoteurCarteCredit.lireDate("2023-03-10")),
            dureeMois = 59,
            mensualite = 234.11,
            tauxAnnuel = 14.99,
            aujourdHui = assertNotNull(MoteurCarteCredit.lireDate("2026-10-29")),
            principalInitial = 21436.23,
            frequence = FrequencePaiementDette.AUX_DEUX_SEMAINES
        ))
        assertEquals(94, s.paiementsEcoules)
        assertEquals("2026-10-30", MoteurCarteCredit.ecrireDate(assertNotNull(s.prochaineDatePaiement)))
    }

    @Test
    fun `paiement chaque semaine compte les vraies periodes de sept jours`() {
        val debut = assertNotNull(MoteurCarteCredit.lireDate("2026-05-05"))
        val aujourdhui = assertNotNull(MoteurCarteCredit.lireDate("2026-05-27"))
        val s = assertNotNull(MoteurDette.situationContrat(
            dateDebut = debut,
            dureeMois = 12,
            mensualite = 100.0,
            tauxAnnuel = 0.0,
            aujourdHui = aujourdhui,
            principalInitial = 10000.0,
            frequence = FrequencePaiementDette.HEBDOMADAIRE
        ))
        assertEquals(52, s.nombrePaiements)
        assertEquals(3, s.paiementsEcoules)
        assertEquals(9700.0, s.soldeActuel)
        assertEquals("2026-06-02", MoteurCarteCredit.ecrireDate(assertNotNull(s.prochaineDatePaiement)))
    }

    @Test
    fun `aucun taux ni duree inventes`() {
        assertNull(MoteurDette.mensualiteContractuelle(1200.0, null, 12))
        assertNull(MoteurDette.mensualiteContractuelle(1200.0, 5.0, null))
        assertNull(MoteurDette.mensualiteContractuelle(1200.0, -1.0, 12))
        val simple = dette(solde = -500.0, taux = null, duree = null, details = DetailsDette(mode = ModeDette.SIMPLE))
        assertNull(MoteurDette.versementPrevu(simple))
        assertNull(MoteurDette.echeancier(simple))
    }

    @Test
    fun `une dette soldee ou creditrice ne doit rien`() {
        assertEquals(0.0, MoteurDette.restant(dette(solde = 0.0)))
        assertEquals(0.0, MoteurDette.restant(dette(solde = 25.0)))
    }

    @Test
    fun `echeances couvertes selon les montants et non les clics`() {
        val d = dette(solde = -1100.0)
        assertEquals(1, MoteurDette.echeancesCouvertes(d, listOf(paiement(50.0), paiement(50.0))))
        assertEquals(0, MoteurDette.echeancesCouvertes(d, listOf(paiement(50.0))))
        assertEquals(100.0, MoteurDette.totalVerse(listOf(paiement(50.0), paiement(50.0))))
    }

    @Test
    fun `versement supplementaire raccourcit et evite des interets`() {
        val d = dette(solde = -10000.0, initial = 10000.0, taux = 6.0, duree = 60)
        val normal = assertNotNull(MoteurDette.echeancier(d))
        val accelere = assertNotNull(MoteurDette.echeancier(d, versementSupplementaire = 100.0))
        assertTrue(accelere.nombrePeriodes!! < normal.nombrePeriodes!!)
        assertTrue(accelere.totalInterets < normal.totalInterets)
    }

    @Test
    fun `taux variable applique a partir de sa date`() {
        val details = DetailsDette(
            mode = ModeDette.FINANCE,
            tauxVariable = true,
            changementsTaux = listOf(ChangementTaux(date = "2020-01-01", taux = 9.0))
        )
        val d = dette(solde = -1000.0, taux = 3.0, details = details)
        val avant = Calendar.getInstance().apply { set(2019, Calendar.JUNE, 1) }.time
        assertEquals(3.0, MoteurDette.tauxA(d, avant))
        assertEquals(9.0, MoteurDette.tauxA(d, Date()))
    }

    @Test
    fun `interets ajoutes confirmes`() {
        val details = DetailsDette(
            ajustements = listOf(
                AjustementDette(date = "2026-10-01", type = TypeAjustementDette.INTERETS, soldeAvant = -1000.0, soldeApres = -1012.5),
                AjustementDette(date = "2026-10-02", type = TypeAjustementDette.SOLDE_OFFICIEL, soldeAvant = -1012.5, soldeApres = -990.0)
            )
        )
        assertEquals(12.5, MoteurDette.interetsAjoutes(dette(solde = -990.0, details = details)))
    }

    @Test
    fun `chaque periode tombe juste au cent`() {
        listOf(0.0, 3.5, 12.0, 24.99).forEach { taux ->
            val e = assertNotNull(MoteurDette.echeancier(dette(solde = -4321.09, initial = 5000.0, taux = taux, duree = 36)))
            e.periodes.forEach { p ->
                assertEquals(MoteurDette.arrondi(p.soldeInitial + p.interets - p.versement), p.soldeFinal)
                assertEquals(MoteurDette.arrondi(p.versement - p.interets), p.capital)
            }
            assertEquals(0.0, e.periodes.last().soldeFinal)
        }
    }
}
