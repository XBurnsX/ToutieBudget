package com.xburnsx.toutiebudget.data.utils

import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import org.junit.Test
import java.util.Calendar
import java.util.Date
import kotlin.test.assertEquals

/**
 * Montant suggéré (option B) : ce qui reste, réparti sur les mois restants.
 * Les reports de fin de mois sont simulés comme le fait RolloverServiceImpl :
 * mois fini → alloué = dépenses, montantReporte = ce qui part; mois suivant → alloué = report.
 */
class ObjectifCalculatorTest {

    private val calcul = ObjectifCalculator()
    private var prochainId = 1

    private fun date(annee: Int, mois: Int, jour: Int = 1): Date =
        Calendar.getInstance().apply { clear(); set(annee, mois - 1, jour, 12, 0, 0) }.time

    private fun ligne(mois: Int, alloue: Double, reporte: Double = 0.0, depense: Double = 0.0, annee: Int = 2027) =
        AllocationMensuelle(
            id = "a${prochainId++}", utilisateurId = "u", enveloppeId = "env", mois = date(annee, mois),
            solde = alloue - depense, alloue = alloue, depense = depense,
            compteSourceId = "bleu", collectionCompteSource = "comptes_cheques", montantReporte = reporte
        )

    private fun enveloppe(
        type: TypeObjectif, montant: Double, debut: Date? = null, fin: Date? = null
    ) = Enveloppe(
        id = "env", utilisateurId = "u", nom = "Assurance", categorieId = "cat", estArchive = false, ordre = 0,
        typeObjectif = type, objectifMontant = montant, dateDebutObjectif = debut, dateObjectif = fin
    )

    private val annuel120 = enveloppe(TypeObjectif.Annuel, 120.0, date(2027, 1), date(2028, 1))

    private fun suggestion(env: Enveloppe, mois: Int, lignes: List<AllocationMensuelle>, solde: Double = 0.0, annee: Int = 2027) =
        calcul.calculerVersementRecommande(env, solde, date(annee, mois, 15), lignes)

    // ===== Annuel / échéance =====

    @Test
    fun `120 dollars sur un an donne 10 dollars en janvier`() {
        assertEquals(10.0, suggestion(annuel120, 1, emptyList()))
    }

    // Cas réel « Epargne » : 50 $ pour le 13 avril, enveloppe à −13,64 $ (dépense), rien de placé.
    private val epargne = enveloppe(TypeObjectif.Annuel, 50.0, date(2027, 3, 13), date(2028, 4, 13))
    private val octobreEpargne = listOf(ligne(10, alloue = 0.0, depense = 13.64))

    @Test
    fun `epargne - le deficit se couvre en entier ce mois-ci`() {
        assertEquals(20.78, suggestion(epargne, 10, octobreEpargne)) // 13,64 + 50 ÷ 7 mois
    }

    @Test
    fun `epargne - un mois futur part du solde actuel, pas de zero`() {
        // Rien de placé : le déficit est toujours là et la suggestion MONTE chaque mois
        assertEquals(21.97, suggestion(epargne, 11, octobreEpargne)) // 13,64 + 50 ÷ 6
        assertEquals(30.31, suggestion(epargne, 2, octobreEpargne, annee = 2028)) // 13,64 + 50 ÷ 3
    }

    @Test
    fun `deficit couvert dans le mois - il ne reste que la part de l'objectif`() {
        // −13,64 $ couverts ce mois-ci par 13,64 $ placés : solde à 0
        val octobre = listOf(ligne(10, alloue = 13.64, depense = 13.64))
        // Le dépôt couvre le déficit; il reste 50 ÷ 7 = 7,14 $ à placer
        assertEquals(7.14, suggestion(epargne, 10, octobre))
    }

    @Test
    fun `10 dollars chaque mois avec les reports - toujours 10, jamais 0`() {
        // Janvier : 10 $ mis, puis reporté en février
        val fevrier = listOf(ligne(1, alloue = 0.0, reporte = 10.0), ligne(2, alloue = 10.0))
        assertEquals(10.0, suggestion(annuel120, 2, fevrier))
        // Février : 10 $ de plus, puis reporté en mars
        val mars = listOf(
            ligne(1, alloue = 0.0, reporte = 10.0),
            ligne(2, alloue = 0.0, reporte = 20.0),
            ligne(3, alloue = 20.0)
        )
        assertEquals(10.0, suggestion(annuel120, 3, mars))
    }

    @Test
    fun `un mois saute - le reste est reparti sur les 11 mois restants`() {
        assertEquals(10.91, suggestion(annuel120, 2, emptyList()))
    }

    @Test
    fun `50 dollars d'un coup - 0 ce mois-ci puis les mois suivants baissent`() {
        // Janvier et février : 10 $ chacun, reportés; mars : report de 20 $ + 50 $ mis d'un coup
        val mars = listOf(
            ligne(1, alloue = 0.0, reporte = 10.0),
            ligne(2, alloue = 0.0, reporte = 20.0),
            ligne(3, alloue = 70.0)
        )
        assertEquals(0.0, suggestion(annuel120, 3, mars))
        // Avril : 70 $ déjà mis → (120 − 70) ÷ 9 mois
        val avril = listOf(
            ligne(1, alloue = 0.0, reporte = 10.0),
            ligne(2, alloue = 0.0, reporte = 20.0),
            ligne(3, alloue = 0.0, reporte = 70.0),
            ligne(4, alloue = 70.0)
        )
        assertEquals(5.56, suggestion(annuel120, 4, avril))
    }

    @Test
    fun `un versement partiel ce mois-ci reduit seulement le reste du mois`() {
        val janvier = listOf(ligne(1, alloue = 4.0))
        assertEquals(6.0, suggestion(annuel120, 1, janvier))
    }

    @Test
    fun `jamais plus que ce qui manque pour atteindre l'objectif`() {
        // Décembre avec 115 $ déjà mis : il ne manque que 5 $
        val decembre = listOf(ligne(11, alloue = 0.0, reporte = 115.0), ligne(12, alloue = 115.0))
        assertEquals(5.0, suggestion(annuel120, 12, decembre))
    }

    @Test
    fun `objectif atteint - plus rien a suggerer`() {
        val juin = listOf(ligne(5, alloue = 0.0, reporte = 120.0), ligne(6, alloue = 120.0))
        assertEquals(0.0, suggestion(annuel120, 6, juin))
    }

    @Test
    fun `l'argent depense ne compte pas pour l'objectif`() {
        // 10 $ mis puis 10 $ dépensés en janvier : il n'y a plus rien dans l'enveloppe
        val fevrier = listOf(ligne(1, alloue = 10.0, depense = 10.0), ligne(2, alloue = 0.0))
        assertEquals(10.91, suggestion(annuel120, 2, fevrier))
    }

    @Test
    fun `une depense ce mois-ci fait remonter ce qui manque`() {
        // 40 $ reportés de janvier, 30 $ dépensés en février : il reste 10 $ dans l'enveloppe
        val fevrier = listOf(ligne(1, alloue = 0.0, reporte = 40.0), ligne(2, alloue = 40.0, depense = 30.0))
        assertEquals(10.0, suggestion(annuel120, 2, fevrier)) // (120 − 10) ÷ 11
    }

    @Test
    fun `s'il faut 400 il faut 400 dans l'enveloppe`() {
        val env = enveloppe(TypeObjectif.Echeance, 400.0, date(2027, 1), date(2027, 4, 15))
        // Les 400 $ étaient là, 100 $ ont été dépensés en mars : il manque 100 $ sur 2 mois
        val mars = listOf(ligne(2, alloue = 0.0, reporte = 400.0), ligne(3, alloue = 400.0, depense = 100.0))
        assertEquals(50.0, suggestion(env, 3, mars))
    }

    @Test
    fun `octobre 2026 - report non note, calcul sur le solde actuel`() {
        // Report d'avant GN3 : on ne sait pas ce qui est arrivé ce mois-ci
        val env = enveloppe(TypeObjectif.Echeance, 120.0, date(2026, 10), date(2027, 10, 1))
        val octobre = listOf(ligne(10, alloue = 30.0, annee = 2026))
        assertEquals(7.5, suggestion(env, 10, octobre, annee = 2026)) // (120 − 30) ÷ 12
    }

    @Test
    fun `avant le debut de l'objectif - rien a suggerer`() {
        val env = enveloppe(TypeObjectif.Annuel, 120.0, date(2027, 3), date(2027, 3))
        assertEquals(0.0, suggestion(env, 2, emptyList()))
    }

    @Test
    fun `argent deja present au debut de l'objectif - compte comme deja mis`() {
        // 60 $ reportés de décembre 2026 vers janvier, objectif commencé en janvier
        val janvier = listOf(ligne(12, alloue = 0.0, reporte = 60.0, annee = 2026), ligne(1, alloue = 60.0))
        assertEquals(5.0, suggestion(annuel120, 1, janvier))
    }

    @Test
    fun `echeance au milieu du mois - ce mois compte`() {
        val env = enveloppe(TypeObjectif.Echeance, 120.0, date(2027, 1), date(2027, 6, 15))
        assertEquals(20.0, suggestion(env, 1, emptyList()))
    }

    @Test
    fun `echeance le 1er - preparee jusqu'a la fin du mois precedent`() {
        val env = enveloppe(TypeObjectif.Echeance, 120.0, date(2027, 1), date(2027, 7, 1))
        assertEquals(20.0, suggestion(env, 1, emptyList()))
    }

    @Test
    fun `echeance depassee - tout le reste d'un coup`() {
        val env = enveloppe(TypeObjectif.Echeance, 120.0, date(2027, 1), date(2027, 3, 15))
        val avril = listOf(ligne(3, alloue = 0.0, reporte = 100.0), ligne(4, alloue = 100.0))
        assertEquals(20.0, suggestion(env, 4, avril))
    }

    @Test
    fun `echeance sans date - aucune suggestion`() {
        assertEquals(0.0, suggestion(enveloppe(TypeObjectif.Echeance, 120.0, date(2027, 1), null), 1, emptyList()))
    }

    // ===== Aux deux semaines =====

    private val bihebdo = enveloppe(TypeObjectif.Bihebdomadaire, 100.0, date(2026, 12, 19), date(2027, 1, 2))

    @Test
    fun `aux deux semaines - mois a 3 echeances`() {
        // Janvier 2027 : 2, 16 et 30
        assertEquals(300.0, suggestion(bihebdo, 1, emptyList()))
    }

    @Test
    fun `aux deux semaines - mois a 2 echeances`() {
        // Février 2027 : 13 et 27
        assertEquals(200.0, suggestion(bihebdo, 2, emptyList()))
    }

    @Test
    fun `aux deux semaines - l'argent deja disponible ce mois-la est retire`() {
        // 50 $ reportés de janvier vers février
        val fevrier = listOf(ligne(1, alloue = 250.0, reporte = 50.0, depense = 250.0), ligne(2, alloue = 50.0))
        assertEquals(150.0, suggestion(bihebdo, 2, fevrier))
    }

    @Test
    fun `aux deux semaines - echeances calculees aussi avant la date connue`() {
        // Décembre 2026 : 5 et 19
        assertEquals(200.0, suggestion(bihebdo, 12, emptyList(), annee = 2026))
    }

    // ===== Mensuel (inchangé) =====

    @Test
    fun `mensuel - ce qui manque au progres du mois`() {
        val env = enveloppe(TypeObjectif.Mensuel, 100.0)
        assertEquals(60.0, suggestion(env, 1, emptyList(), solde = 40.0))
    }

    @Test
    fun `sans objectif - zero`() {
        assertEquals(0.0, suggestion(enveloppe(TypeObjectif.Aucun, 0.0), 1, emptyList()))
    }
}
