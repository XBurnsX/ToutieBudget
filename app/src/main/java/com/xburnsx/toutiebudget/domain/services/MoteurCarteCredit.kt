package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.ReleveCarte
import com.xburnsx.toutiebudget.data.modeles.NatureMouvementCarte
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeFrais
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Moteur UNIQUE des calculs de carte de crédit (CC1) : la carte, les détails, le minimum,
 * le calculateur, le plan et les simulations passent tous par ici et donnent les mêmes chiffres.
 *
 * Convention de solde (CC3) : dette = solde NÉGATIF, carte à zéro = 0, trop-payé = solde POSITIF.
 * Toute valeur calculée ici est une ESTIMATION ; les montants officiels viennent des relevés (CC6).
 */
object MoteurCarteCredit {

    private const val JOURS_PAR_AN = 365.0
    private const val MAX_MOIS = 600
    private val formatJour get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }

    fun arrondi(montant: Double): Double = BigDecimal.valueOf(montant).setScale(2, RoundingMode.HALF_UP).toDouble()
    private fun arrondiHaut(montant: Double): Double = BigDecimal.valueOf(montant).setScale(2, RoundingMode.CEILING).toDouble()

    fun lireDate(texte: String?): Date? = texte?.trim()?.takeIf {
        it.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
    }?.let { try { formatJour.parse(it) } catch (_: Exception) { null } }
    fun ecrireDate(date: Date): String = formatJour.format(date)

    // ===== Solde, crédit, utilisation =====

    /** Montant dû (0 pour une carte à zéro ou créditrice, jamais abs() d'un avoir). */
    fun dette(carte: CompteCredit): Double = arrondi(max(0.0, -carte.solde))

    /** Peut être négatif quand la limite est dépassée ; un avoir augmente le disponible. */
    fun creditDisponible(carte: CompteCredit): Double = arrondi(carte.limiteCredit + carte.solde)

    /** Non plafonné à 100 % : un dépassement reste visible. */
    fun tauxUtilisation(carte: CompteCredit): Double =
        if (carte.limiteCredit <= 0) 0.0 else dette(carte) / carte.limiteCredit

    fun limiteDepassee(carte: CompteCredit): Boolean = dette(carte) > carte.limiteCredit + 0.005

    // ===== Taux =====

    fun tauxAchats(carte: CompteCredit): Double = carte.tauxInteret ?: 0.0

    /** Taux appliqué aux achats à une date donnée (promotion en cours si configurée). */
    fun tauxEffectif(carte: CompteCredit, date: Date = Date()): Double {
        val d = carte.details
        val debutPromo = lireDate(d.debutPromo)
        val finPromo = lireDate(d.finPromo)
        val jour = debutJour(date)
        val dansPromotion = d.tauxPromo != null &&
            (debutPromo == null || !jour.before(debutPromo)) &&
            (finPromo == null || !jour.after(finPromo)) &&
            (debutPromo != null || finPromo != null)
        return if (dansPromotion) d.tauxPromo!! else tauxAchats(carte)
    }

    /** Intérêts estimés pour un nombre de jours sur un montant dû (taux annuel / 365, calcul quotidien). */
    fun interetsEstimes(montantDu: Double, tauxAnnuel: Double, jours: Int): Double =
        if (montantDu <= 0 || tauxAnnuel <= 0 || jours <= 0) 0.0 else montantDu * tauxAnnuel / 100.0 / JOURS_PAR_AN * jours

    /** Estimation sur 30 jours à partir d'aujourd'hui (projection, pas des intérêts passés). */
    fun interetsMensuelsEstimes(carte: CompteCredit): Double =
        arrondi(interetsEstimes(dette(carte), tauxEffectif(carte), 30))

    // ===== Relevés et cycle (CC4, CC6) =====

    fun dernierReleve(carte: CompteCredit): ReleveCarte? = carte.details.relevesTries.firstOrNull()

    /** Prochaine clôture d'après le jour configuré (null si non configuré). */
    fun prochaineCloture(carte: CompteCredit, aujourdHui: Date = Date()): Date? {
        val jour = carte.details.jourCloture ?: return null
        val cal = Calendar.getInstance().apply { time = debutJour(aujourdHui) }
        repeat(2) {
            val candidat = (cal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, min(jour, getActualMaximum(Calendar.DAY_OF_MONTH)))
            }
            if (!candidat.time.before(debutJour(aujourdHui))) return candidat.time
            cal.add(Calendar.MONTH, 1)
            cal.set(Calendar.DAY_OF_MONTH, 1)
        }
        return null
    }

    /**
     * Échéance à venir : celle du dernier relevé officiel si elle n'est pas passée,
     * sinon (prochaine clôture + délai configuré). Null si rien n'est confirmé.
     */
    fun prochaineEcheance(carte: CompteCredit, aujourdHui: Date = Date()): Date? {
        val echeanceReleve = lireDate(dernierReleve(carte)?.dateEcheance)
        if (echeanceReleve != null && !echeanceReleve.before(debutJour(aujourdHui))) return echeanceReleve
        val cloture = prochaineCloture(carte, aujourdHui) ?: return null
        val delai = carte.details.joursAvantEcheance ?: return null
        return Calendar.getInstance().apply { time = cloture; add(Calendar.DAY_OF_MONTH, delai) }.time
    }

    // ===== Minimum (CC7) =====

    /** Minimum estimé pour un solde de RELEVÉ selon les règles configurées (pas les achats après la clôture). */
    fun minimumEstime(carte: CompteCredit, soldeReleve: Double): Double {
        if (!soldeReleve.isFinite() || soldeReleve < 0.0) {
            throw IllegalArgumentException("Solde du relevé invalide")
        }
        if (soldeReleve <= 0.005) return 0.0
        val d = carte.details
        require(d.pourcentageMinimumEffectif.isFinite() && d.pourcentageMinimumEffectif in 0.0..100.0) {
            "Pourcentage minimum invalide"
        }
        var minimum = arrondiHaut(soldeReleve * d.pourcentageMinimumEffectif / 100.0)
        d.plancherMinimumEffectif?.let { plancher ->
            require(plancher.isFinite() && plancher >= 0.0) { "Plancher minimum invalide" }
            minimum = max(minimum, plancher)
        }
        return min(minimum, arrondi(soldeReleve))
    }

    data class MinimumAPayer(
        val montant: Double,
        val officiel: Boolean,
        val echeance: Date?,
        val source: String
    )

    /**
     * Le minimum OFFICIEL du dernier relevé a priorité ; sinon le minimum saisi sur la carte ;
     * sinon une estimation sur le solde du relevé (ou le solde actuel s'il n'y a pas de relevé).
     */
    fun minimumAPayer(carte: CompteCredit): MinimumAPayer? {
        val releve = dernierReleve(carte)
        if (releve?.minimumOfficiel != null) {
            return MinimumAPayer(releve.minimumOfficiel, true, lireDate(releve.dateEcheance), "Relevé du ${releve.dateEmission}")
        }
        carte.paiementMinimum?.takeIf { it > 0 }?.let {
            return MinimumAPayer(it, false, prochaineEcheance(carte), "Montant saisi (non confirmé par relevé)")
        }
        val base = releve?.soldeOfficiel ?: dette(carte)
        if (base <= 0.005) return null
        return MinimumAPayer(minimumEstime(carte, base), false, prochaineEcheance(carte), "Estimation (${carte.details.pourcentageMinimumEffectif} %)")
    }

    // ===== Paiements depuis un relevé (CC9) =====

    private val typesPaiement = setOf(
        TypeTransaction.Paiement, TypeTransaction.Emprunt, TypeTransaction.TransfertEntrant,
        TypeTransaction.Revenu, TypeTransaction.RemboursementRecu
    )

    /** Effet d'une transaction de la carte sur la dette : + augmente la dette, - la réduit. */
    private fun effetSurDette(tx: Transaction): Double =
        if (tx.type in typesPaiement) -tx.montant else tx.montant

    // CC7/CC9 : seules les vraies transactions de paiement de carte
    // couvrent le minimum. Un revenu/remboursement marchand ne doit pas
    // supprimer par erreur l'alerte d'échéance.
    fun paiementsDepuis(transactionsCarte: List<Transaction>, date: Date): Double =
        transactionsCarte.filter { it.type == TypeTransaction.Paiement && !it.date.before(date) }
            .sumOf { max(0.0, it.montant) }

    // ===== Intérêts du cycle en cours (CC5) =====

    data class EstimationCycle(
        val montant: Double,
        val debut: Date,
        val jours: Int,
        val graceProbable: Boolean,
        val estimationDisponible: Boolean = true,
        val avertissement: String? = null
    )

    /**
     * Intérêts ESTIMÉS depuis le début du cycle, à partir du solde de chaque jour reconstitué
     * avec les transactions datées. Si le dernier relevé a été payé en entier avant l'échéance,
     * les achats sont présumés dans le délai de grâce (0 $). Rien n'est ajouté au solde.
     */
    fun interetsCycleCourant(carte: CompteCredit, transactionsCarte: List<Transaction>, aujourdHui: Date = Date()): EstimationCycle? {
        val releve = dernierReleve(carte)
        val debut = lireDate(releve?.dateEmission)
            ?: carte.details.jourCloture?.let { prochaineCloture(carte, aujourdHui)?.let { c -> Calendar.getInstance().apply { time = c; add(Calendar.MONTH, -1) }.time } }
            ?: return null
        val fin = debutJour(aujourdHui)
        val jours = TimeUnit.MILLISECONDS.toDays(fin.time - debutJour(debut).time).toInt()
        if (jours <= 0) return EstimationCycle(0.0, debut, 0, false)
        // CC5 : un relevé ancien ne représente pas le cycle actuel.
        if (jours > 62) return EstimationCycle(
            0.0, debut, jours, false, estimationDisponible = false,
            avertissement = "Le dernier relevé est trop ancien pour reconstituer le cycle courant."
        )
        val charges = transactionsCarte.filter { t ->
            !t.date.before(debut) && !t.date.after(aujourdHui) &&
                (t.type == TypeTransaction.Depense ||
                    t.type == TypeTransaction.TransfertSortant ||
                    t.type == TypeTransaction.Pret ||
                    t.type == TypeTransaction.PaiementEffectue)
        }
        val qualifications = carte.details.naturesMouvements
        val naturePartielle = charges.any { qualifications[it.id] == null }
        val mouvementsTauxDistincts = charges.any {
            val nature = qualifications[it.id]
            nature == NatureMouvementCarte.AVANCE_FONDS ||
                nature == NatureMouvementCarte.TRANSFERT_SOLDE
        }
        if (naturePartielle || mouvementsTauxDistincts) {
            return EstimationCycle(
                0.0, debut, jours, false, estimationDisponible = false,
                avertissement = if (naturePartielle)
                    "Nature de certains mouvements inconnue. Classe-les pour identifier les taux applicables."
                else
                    "Avances/transferts présents : sans les soldes ventilés et la règle de répartition des paiements de la banque, un chiffre exact serait trompeur."
            )
        }

        val echeance = lireDate(releve?.dateEcheance)
        val payeEnEntier = releve != null && echeance != null &&
            transactionsCarte.filter { it.type == TypeTransaction.Paiement && !it.date.before(debut) && !it.date.after(echeance) }
                .sumOf { it.montant } + 0.005 >= releve.soldeOfficiel
        if (payeEnEntier) return EstimationCycle(0.0, debut, jours, true)

        // Solde de chaque jour en remontant à partir du solde actuel
        val apresDebut = transactionsCarte.filter { !it.date.before(debut) }
        var total = 0.0
        val cal = Calendar.getInstance().apply { time = fin }
        var detteDuJour = dette(carte)
        for (i in 0 until jours) {
            val jourCourant = cal.time
            cal.add(Calendar.DAY_OF_MONTH, -1)
            val veille = cal.time
            // CC5 : respecter le taux promotionnel ou régulier POUR chaque jour,
            // y compris le jour où une promotion expire dans un même cycle.
            total += interetsEstimes(detteDuJour, tauxEffectif(carte, veille), 1)
            // Annuler l'effet des transactions de ce jour pour obtenir le solde de la veille
            val effetJour = apresDebut.filter { !it.date.before(veille) && it.date.before(jourCourant) }.sumOf { effetSurDette(it) }
            detteDuJour = max(0.0, detteDuJour - effetJour)
        }
        return EstimationCycle(arrondi(total), debut, jours, false)
    }

    // ===== Simulations et plans (CC8) =====

    enum class Strategie(val libelle: String) {
        MINIMUM("Minimum"),
        FIXE("Montant fixe"),
        POURCENTAGE("% du solde")
    }

    data class LigneSimulation(
        val mois: Int,
        val date: Date,
        val soldeInitial: Double,
        val interets: Double,
        val frais: Double,
        val paiement: Double,
        val capital: Double,
        val soldeFinal: Double
    )

    data class ResultatSimulation(
        val lignes: List<LigneSimulation>,
        val nombreMois: Int?, // null = impossible dans la durée simulée
        val totalInterets: Double,
        val totalFrais: Double,
        val totalPaye: Double,
        val avertissement: String? = null
    )

    /**
     * Simulation mois par mois en cents : intérêts quotidiens sur les jours réels du mois,
     * frais mensuels chaque mois et annuels tous les 12 mois (les frais ponctuels ne sont pas projetés),
     * paiement selon la stratégie. Le minimum est RECALCULÉ chaque mois (dégressif), jamais figé.
     */
    fun simuler(
        carte: CompteCredit,
        strategie: Strategie,
        valeur: Double = 0.0,
        dureeMax: Int = MAX_MOIS,
        debut: Date = Date()
    ): ResultatSimulation {
        if (dureeMax !in 1..MAX_MOIS) {
            return ResultatSimulation(emptyList(), null, 0.0, 0.0, 0.0,
                "Durée de simulation invalide (1 à $MAX_MOIS mois).")
        }
        if (strategie != Strategie.MINIMUM && (!valeur.isFinite() || valeur <= 0.0 ||
                    (strategie == Strategie.POURCENTAGE && valeur > 100.0))) {
            return ResultatSimulation(emptyList(), null, 0.0, 0.0, 0.0,
                "Montant fixe ou pourcentage invalide.")
        }
        var solde = dette(carte)
        if (solde <= 0.005) return ResultatSimulation(emptyList(), 0, 0.0, 0.0, 0.0)

        val fraisConfigures = carte.fraisMensuels
        val cal = Calendar.getInstance().apply { time = prochaineEcheance(carte, debut) ?: debut }
        if (prochaineEcheance(carte, debut) == null) cal.add(Calendar.MONTH, 1)

        val lignes = mutableListOf<LigneSimulation>()
        var totalInterets = 0.0
        var totalFrais = 0.0
        var totalPaye = 0.0
        var dateMoisPrecedent = debut

        for (mois in 1..dureeMax) {
            val dateMois = cal.time
            val jours = max(1, TimeUnit.MILLISECONDS.toDays(dateMois.time - dateMoisPrecedent.time).toInt())
            val interets = arrondi(interetsEstimes(solde, tauxEffectif(carte, dateMois), jours))
            // CC8/CC10 : appliquer les frais à la bonne date, pas ajouter
            // tous les frais annuels arbitrairement au 12e mois.
            val frais = arrondi(fraisConfigures.sumOf { entree ->
                if (!entree.montant.isFinite() || entree.montant < 0) {
                    throw IllegalArgumentException("Frais de carte invalide")
                }
                val dateApplication = lireDate(entree.dateApplication)
                if (dateApplication != null && dateMois.before(dateApplication)) return@sumOf 0.0
                when (entree.type) {
                    TypeFrais.MENSUEL -> entree.montant
                    TypeFrais.ANNUEL -> if (dateApplication == null) {
                        if (mois % 12 == 0) entree.montant else 0.0
                    } else {
                        val debutFrais = Calendar.getInstance().apply { time = dateApplication }
                        val periode = Calendar.getInstance().apply { time = dateMois }
                        if (periode.get(Calendar.MONTH) == debutFrais.get(Calendar.MONTH)) entree.montant else 0.0
                    }
                    TypeFrais.PONCTUEL -> 0.0 // Jamais inventer une facturation récurrente
                }
            })
            val du = arrondi(solde + interets + frais)
            if (!du.isFinite() || du <= 0.0) {
                return ResultatSimulation(lignes, null, arrondi(totalInterets), arrondi(totalFrais),
                    arrondi(totalPaye), "Projection invalide : montant ou intérêts trop élevés.")
            }

            val paiementVoulu = when (strategie) {
                Strategie.MINIMUM -> minimumEstime(carte, du)
                Strategie.FIXE -> valeur
                Strategie.POURCENTAGE -> {
                    var p = arrondiHaut(du * valeur / 100.0)
                    carte.details.plancherMinimumEffectif?.let { p = max(p, it) }
                    p
                }
            }
            if (!paiementVoulu.isFinite() || paiementVoulu < 0.0) {
                return ResultatSimulation(lignes, null, arrondi(totalInterets), arrondi(totalFrais),
                    arrondi(totalPaye), "Projection de paiement invalide.")
            }
            val paiement = arrondi(min(paiementVoulu, du))

            if (paiement <= interets + frais && du > 0.005) {
                return ResultatSimulation(
                    lignes, null, arrondi(totalInterets), arrondi(totalFrais), arrondi(totalPaye),
                    "Le versement (${"%.2f".format(paiement)} $) ne couvre pas les intérêts et les frais du mois : la dette ne diminue pas."
                )
            }

            val capital = arrondi(paiement - interets - frais)
            val soldeFinal = arrondi(du - paiement)
            lignes += LigneSimulation(mois, dateMois, solde, interets, frais, paiement, capital, soldeFinal)
            totalInterets += interets
            totalFrais += frais
            totalPaye += paiement
            solde = soldeFinal
            dateMoisPrecedent = dateMois
            cal.add(Calendar.MONTH, 1)

            if (solde <= 0.005) {
                return ResultatSimulation(lignes, mois, arrondi(totalInterets), arrondi(totalFrais), arrondi(totalPaye))
            }
        }
        return ResultatSimulation(
            lignes, null, arrondi(totalInterets), arrondi(totalFrais), arrondi(totalPaye),
            "Remboursement impossible en ${dureeMax / 12} ans avec cette stratégie."
        )
    }

    /** Versement fixe nécessaire pour rembourser en [mois] mois (recherche au cent près). */
    fun paiementPourDateCible(carte: CompteCredit, mois: Int): Double? {
        if (mois <= 0) return null
        val dette = dette(carte)
        if (dette <= 0.005) return 0.0
        var bas = 0.0
        var haut = dette * 2 + 1000
        repeat(60) {
            val milieu = (bas + haut) / 2
            val r = simuler(carte, Strategie.FIXE, milieu, mois)
            if (r.nombreMois != null && r.nombreMois <= mois) haut = milieu else bas = milieu
        }
        return arrondiHaut(haut)
    }

    private fun debutJour(date: Date): Date = Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time
}
