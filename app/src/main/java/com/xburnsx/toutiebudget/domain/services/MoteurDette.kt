package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.data.modeles.FrequencePaiementDette
import com.xburnsx.toutiebudget.data.modeles.ModeDette
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeAjustementDette
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import java.util.Calendar
import java.util.Date
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Moteur UNIQUE des dettes (DF4) : écran, validation, échéancier et simulations utilisent ces
 * fonctions. Aucun taux ni durée « par défaut » : une donnée manquante donne « indisponible ».
 * Le solde RÉEL (solde_dette, négatif = dette) n'est jamais remplacé par une projection (DF2).
 */
object MoteurDette {

    private const val MAX_MOIS = 600
    private const val MAX_PERIODES = 5200

    fun arrondi(v: Double): Double = MoteurCarteCredit.arrondi(v)

    /** Montant réellement dû (0 si soldée ; un solde créditeur n'est jamais une nouvelle dette). */
    fun restant(dette: CompteDette): Double = arrondi(max(0.0, -dette.soldeDette))

    /** Taux en vigueur à une date : dernier changement daté, sinon le taux du contrat (DA7). */
    fun tauxA(dette: CompteDette, date: Date): Double? {
        // DA7 : l'historique des anciens taux est conservé dans le JSON,
        // mais désactiver « taux variable » ne doit pas continuer à appliquer
        // ces anciennes révisions dans les projections futures.
        val changement = if (dette.details.tauxVariable) dette.details.changementsTaux
            .mapNotNull { c -> MoteurCarteCredit.lireDate(c.date)?.let { it to c.taux } }
            .filter { !it.first.after(date) }
            .maxByOrNull { it.first } else null
        return (changement?.second ?: dette.tauxInteret)
            ?.takeIf { it.isFinite() && it in 0.0..100.0 }
    }

    /** Mensualité d'amortissement d'un prêt financé ; null si une donnée manque (pas de valeur inventée). */
    fun mensualiteContractuelle(principal: Double, tauxAnnuel: Double?, dureeMois: Int?): Double? {
        if (!principal.isFinite() || principal <= 0 || tauxAnnuel == null ||
            !tauxAnnuel.isFinite() || tauxAnnuel !in 0.0..100.0 ||
            dureeMois == null || dureeMois !in 1..MAX_MOIS
        ) return null
        if (tauxAnnuel == 0.0) return arrondi(principal / dureeMois)
        val r = tauxAnnuel / 100.0 / 12.0
        val facteur = (1 + r).pow(dureeMois)
        val m = principal * r * facteur / (facteur - 1)
        return if (m.isFinite()) arrondi(m) else null
    }

    data class SituationContrat(
        val principalInitial: Double,
        val paiementsEcoules: Int,
        val soldeActuel: Double,
        val capitalPaye: Double,
        val interetsPayes: Double,
        val totalVerse: Double,
        val dateFin: Date,
        val jourVersement: Int,
        val nombrePaiements: Int,
        val frequence: FrequencePaiementDette,
        val debutCycleActuel: Date,
        val prochaineDatePaiement: Date?
    )

    /**
     * Reconstitue un prêt déjà commencé à partir de ses conditions contractuelles.
     * La date de début est la date du contrat; la première mensualité arrive un mois plus tard.
     * Les calculs sont arrondis au cent à chaque échéance, comme sur un relevé.
     */
    fun situationContrat(
        dateDebut: Date,
        dureeMois: Int,
        mensualite: Double,
        tauxAnnuel: Double,
        aujourdHui: Date = Date(),
        nombrePaiementsForce: Int? = null,
        principalInitial: Double? = null,
        frequence: FrequencePaiementDette = FrequencePaiementDette.MENSUELLE,
        premierPaiement: Date? = null
    ): SituationContrat? {
        if (dureeMois !in 1..MAX_MOIS || !mensualite.isFinite() || mensualite <= 0.0 ||
            !tauxAnnuel.isFinite() || tauxAnnuel !in 0.0..100.0 ||
            (principalInitial != null && (!principalInitial.isFinite() || principalInitial <= 0.0))
        ) return null
        val versement = arrondi(mensualite)
        if (versement <= 0.0) return null
        val nombrePaiements = nombrePaiementsContrat(dureeMois, frequence)
        val tauxPeriode = tauxAnnuel / 100.0 / frequence.paiementsParAn
        val principalExact = principalInitial ?: if (tauxPeriode == 0.0) {
            versement * nombrePaiements
        } else {
            versement * (1.0 - (1.0 + tauxPeriode).pow(-nombrePaiements)) / tauxPeriode
        }
        if (!principalExact.isFinite() || principalExact <= 0.0) return null
        val principal = arrondi(principalExact)
        val premier = premierPaiement?.takeIf { !it.before(debutJour(dateDebut)) }
        val jour = Calendar.getInstance().apply { time = premier ?: dateDebut }.get(Calendar.DAY_OF_MONTH)
        fun echeance(numero: Int) = dateEcheance(dateDebut, numero, jour, frequence, premier)
        val dateFin = echeance(nombrePaiements)
        val nombre = nombrePaiementsForce?.coerceIn(0, nombrePaiements) ?: run {
            var ecoulees = 0
            for (numero in 1..nombrePaiements) {
                if (!echeance(numero).after(finJour(aujourdHui))) ecoulees++ else break
            }
            ecoulees
        }

        var solde = principal
        var totalVerse = 0.0
        var interets = 0.0
        var datePrecedente = debutJour(dateDebut)
        for (numero in 1..nombre) {
            val dateVersement = echeance(numero)
            // Hebdomadaire / aux deux semaines : intérêt au jour près, comme la banque
            // (solde × taux ÷ 365 × jours réels depuis le versement précédent).
            val interet = if (frequence == FrequencePaiementDette.MENSUELLE) arrondi(solde * tauxPeriode)
                else arrondi(solde * tauxAnnuel / 100.0 / 365.0 * joursEntre(datePrecedente, dateVersement))
            datePrecedente = dateVersement
            val du = arrondi(solde + interet)
            val paiement = min(versement, du)
            solde = arrondi(du - paiement)
            totalVerse = arrondi(totalVerse + paiement)
            interets = arrondi(interets + interet)
        }
        val capital = arrondi(principal - solde)
        val debutCycleActuel = if (nombre == 0) {
            debutJour(dateDebut)
        } else {
            echeance(nombre)
        }
        val prochaineDatePaiement = if (nombre < nombrePaiements) {
            echeance(nombre + 1)
        } else null
        return SituationContrat(
            principalInitial = principal,
            paiementsEcoules = nombre,
            soldeActuel = solde,
            capitalPaye = capital,
            interetsPayes = interets,
            totalVerse = totalVerse,
            dateFin = dateFin,
            jourVersement = jour,
            nombrePaiements = nombrePaiements,
            frequence = frequence,
            debutCycleActuel = debutCycleActuel,
            prochaineDatePaiement = prochaineDatePaiement
        )
    }

    fun situationContrat(dette: CompteDette): SituationContrat? {
        val debut = MoteurCarteCredit.lireDate(dette.details.dateDebut) ?: return null
        val duree = dette.dureeMoisPret ?: return null
        val mensualite = dette.paiementMinimum ?: return null
        val taux = dette.tauxInteret ?: return null
        return situationContrat(
            dateDebut = debut,
            dureeMois = duree,
            mensualite = mensualite,
            tauxAnnuel = taux,
            nombrePaiementsForce = dette.paiementEffectue,
            principalInitial = dette.montantInitial,
            frequence = dette.details.frequencePaiement ?: FrequencePaiementDette.MENSUELLE,
            premierPaiement = MoteurCarteCredit.lireDate(dette.details.datePremierPaiement)
        )
    }

    /** Jours civils entre deux dates (heure d'été sans effet). */
    private fun joursEntre(de: Date, a: Date): Long {
        val zone = java.time.ZoneId.systemDefault()
        return java.time.temporal.ChronoUnit.DAYS.between(
            de.toInstant().atZone(zone).toLocalDate(), a.toInstant().atZone(zone).toLocalDate()
        )
    }

    /** Versement prévu : celui saisi, sinon la mensualité du contrat (prêt financé seulement). */
    fun versementPrevu(dette: CompteDette): Double? =
        dette.paiementMinimum?.takeIf { it.isFinite() && it > 0 }
            ?: if (dette.details.mode == ModeDette.FINANCE) mensualiteContractuelle(dette.montantInitial, dette.tauxInteret, dette.dureeMoisPret) else null

    // ===== Versements réels (DF3) =====

    /** Paiements réellement enregistrés sur la dette, par date et montant. */
    fun paiementsReels(
        transactionsDette: List<Transaction>, jusquAu: Date = Date()
    ): List<Transaction> =
        transactionsDette.filter {
            it.type == TypeTransaction.Paiement &&
                it.montant.isFinite() && it.montant > 0.0 && !it.date.after(jusquAu)
        }.sortedBy { it.date }

    fun totalVerse(transactionsDette: List<Transaction>): Double = arrondi(paiementsReels(transactionsDette).sumOf { it.montant })

    /** DF3 : nombre indicatif d'échéances contractuelles couvertes à date.
     * Un extra versé d'avance ne crée pas de faux mois échus; si les dates
     * contractuelles sont inconnues, aucun compteur d'échéances n'est inventé.
     */
    fun echeancesCouvertes(
        dette: CompteDette, transactionsDette: List<Transaction>, aujourdHui: Date = Date()
    ): Int? {
        val mensualite = versementPrevu(dette)?.takeIf { it.isFinite() && it > 0.005 }
            ?: return null
        val depart = MoteurCarteCredit.lireDate(dette.details.dateDebut) ?: return null
        val jour = dette.details.jourVersement?.takeIf { it in 1..31 } ?: return null
        val fin = MoteurCarteCredit.lireDate(dette.details.dateFin)
        val dateLimite = if (fin != null && fin.before(aujourdHui)) fin else aujourdHui
        if (dateLimite.before(depart)) return 0
        val debutContrat = debutJour(depart)
        val limite = debutJour(dateLimite)
        val frequence = dette.details.frequencePaiement ?: FrequencePaiementDette.MENSUELLE
        val duree = dette.dureeMoisPret ?: return null
        val nombrePaiements = nombrePaiementsContrat(duree, frequence)
        val premier = MoteurCarteCredit.lireDate(dette.details.datePremierPaiement)
        var echeancesExigibles = 0
        for (numero in 1..nombrePaiements) {
            val echeance = dateEcheance(debutContrat, numero, jour, frequence, premier)
            if (echeance.after(limite)) break
            echeancesExigibles++
        }
        val total = arrondi(paiementsReels(transactionsDette, aujourdHui).sumOf { it.montant })
        if (!total.isFinite()) return null
        val equivalents = kotlin.math.floor((total + 0.005) / mensualite).toInt()
        return equivalents.coerceIn(0, echeancesExigibles)
    }

    // ===== Intérêts et frais confirmés (DF7, DA6) =====

    fun interetsAjoutes(dette: CompteDette): Double = arrondi(
        dette.details.ajustements.filter { it.type == TypeAjustementDette.INTERETS }.sumOf { it.soldeAvant - it.soldeApres }
    )

    fun fraisAjoutes(dette: CompteDette): Double = arrondi(
        dette.details.ajustements.filter { it.type == TypeAjustementDette.FRAIS }.sumOf { it.soldeAvant - it.soldeApres }
    )

    // ===== Échéances (DA3) =====

    /** Prochaine date de versement d'après le jour du contrat (null si non configuré ou contrat terminé). */
    fun prochaineEcheance(dette: CompteDette, aujourdHui: Date = Date()): Date? {
        val jour = dette.details.jourVersement?.takeIf { it in 1..31 } ?: return null
        val dateContrat = dette.details.dateDebut?.let(MoteurCarteCredit::lireDate)
        val dateFin = dette.details.dateFin?.let(MoteurCarteCredit::lireDate)
        // DA3 : aucune échéance avant le début réel du contrat. Pour les
        // contrats démarrant le 29-31, utiliser le dernier jour du mois.
        val aPartir = listOfNotNull(debutJour(aujourdHui), dateContrat).maxOrNull() ?: return null
        if (dateFin != null && dateFin.before(aPartir)) return null
        val debut = dateContrat ?: return null
        val frequence = dette.details.frequencePaiement ?: FrequencePaiementDette.MENSUELLE
        val nombrePaiements = dette.dureeMoisPret?.let { nombrePaiementsContrat(it, frequence) }
            ?: MAX_PERIODES
        val premier = MoteurCarteCredit.lireDate(dette.details.datePremierPaiement)
        for (numero in 1..nombrePaiements) {
            val resultat = dateEcheance(debut, numero, jour, frequence, premier)
            if (!resultat.before(aPartir) && (dateFin == null || !resultat.after(dateFin))) {
                return resultat
            }
        }
        return null
    }

    enum class StatutEcheance(val libelle: String) { PAYEE("payée"), PARTIELLE("partiellement payée"), NON_PAYEE("non payée") }

    data class EtatEcheance(val date: Date, val prevu: Double, val verse: Double, val statut: StatutEcheance) {
        val reste: Double get() = arrondi(max(0.0, prevu - verse))
    }

    /** État de l'échéance à venir : versements faits depuis l'échéance précédente. */
    fun etatProchaineEcheance(dette: CompteDette, transactionsDette: List<Transaction>, aujourdHui: Date = Date()): EtatEcheance? {
        val echeance = prochaineEcheance(dette, aujourdHui) ?: return null
        val prevu = min(versementPrevu(dette) ?: return null, restant(dette) + totalVerseDepuis(dette, transactionsDette, echeance))
        val frequence = dette.details.frequencePaiement ?: FrequencePaiementDette.MENSUELLE
        val debutPeriode = reculerPeriode(echeance, frequence)
        val verse = arrondi(paiementsReels(transactionsDette, aujourdHui).filter {
            it.date.after(debutPeriode) && !it.date.after(finJour(echeance))
        }.sumOf { it.montant })
        val statut = when {
            verse + 0.005 >= prevu -> StatutEcheance.PAYEE
            verse > 0.005 -> StatutEcheance.PARTIELLE
            else -> StatutEcheance.NON_PAYEE
        }
        return EtatEcheance(echeance, arrondi(prevu), verse, statut)
    }

    private fun totalVerseDepuis(dette: CompteDette, transactionsDette: List<Transaction>, echeance: Date): Double {
        val frequence = dette.details.frequencePaiement ?: FrequencePaiementDette.MENSUELLE
        val debutPeriode = reculerPeriode(echeance, frequence)
        return paiementsReels(transactionsDette).filter { it.date.after(debutPeriode) }.sumOf { it.montant }
    }

    // ===== Échéancier et simulations (DA2, DA4) =====

    data class Periode(
        val numero: Int,
        val date: Date,
        val soldeInitial: Double,
        val taux: Double,
        val interets: Double,
        val versement: Double,
        val capital: Double,
        val soldeFinal: Double
    )

    data class Echeancier(
        val periodes: List<Periode>,
        val nombrePeriodes: Int?, // null = impossible avec ce versement
        val totalInterets: Double,
        val dateFin: Date?,
        val avertissement: String? = null
    )

    /**
     * Échéancier PRÉVISIONNEL depuis le solde RÉEL d'aujourd'hui : intérêt mensuel contractuel
     * (taux en vigueur à chaque date), versement prévu + extra, arrondi au cent.
     * Null pour une dette sans versement prévu (dette simple sans échéancier).
     */
    fun echeancier(dette: CompteDette, versementSupplementaire: Double = 0.0, aujourdHui: Date = Date()): Echeancier? {
        val versementBase = versementPrevu(dette) ?: return null
        if (!versementSupplementaire.isFinite() || versementSupplementaire < 0.0) {
            return Echeancier(emptyList(), null, 0.0, null, "Versement supplémentaire invalide.")
        }
        val versement = arrondi(versementBase + versementSupplementaire)
        if (!versement.isFinite() || versement <= 0.0) {
            return Echeancier(emptyList(), null, 0.0, null, "Versement mensuel invalide.")
        }
        var solde = restant(dette)
        if (solde <= 0.005) return Echeancier(emptyList(), 0, 0.0, aujourdHui)

        val premiere = prochaineEcheance(dette, aujourdHui)
            ?: return Echeancier(emptyList(), null, 0.0, null, "Jour de versement inconnu pour le contrat.")
        val cal = Calendar.getInstance().apply { time = premiere }
        val jourContrat = dette.details.jourVersement ?: cal.get(Calendar.DAY_OF_MONTH)
        val frequence = dette.details.frequencePaiement ?: FrequencePaiementDette.MENSUELLE
        val periodes = mutableListOf<Periode>()
        var totalInterets = 0.0

        for (numero in 1..MAX_PERIODES) {
            val date = cal.time
            val taux = tauxA(dette, date) ?: return Echeancier(
                periodes, null, arrondi(totalInterets), null,
                "Taux du contrat inconnu pour cette période : impossible de calculer les intérêts."
            )
            // Mensuel : taux ÷ 12 ; hebdomadaire / aux deux semaines : au jour près (÷ 365 × 7 ou 14 jours)
            val interets = when (frequence) {
                FrequencePaiementDette.MENSUELLE -> arrondi(solde * taux / 100.0 / 12.0)
                FrequencePaiementDette.AUX_DEUX_SEMAINES -> arrondi(solde * taux / 100.0 / 365.0 * 14)
                FrequencePaiementDette.HEBDOMADAIRE -> arrondi(solde * taux / 100.0 / 365.0 * 7)
            }
            val du = arrondi(solde + interets)
            val paiement = min(versement, du)
            if (paiement <= interets && du > 0.005) {
                return Echeancier(periodes, null, arrondi(totalInterets), null,
                    "Le versement (${"%.2f".format(versement)} $) ne couvre pas les intérêts du mois : la dette ne diminue pas.")
            }
            val soldeFinal = arrondi(du - paiement)
            periodes += Periode(numero, date, solde, taux, interets, paiement, arrondi(paiement - interets), soldeFinal)
            totalInterets += interets
            solde = soldeFinal
            if (solde <= 0.005) return Echeancier(periodes, numero, arrondi(totalInterets), date)
            cal.time = avancerPeriode(date, jourContrat, frequence)
        }
        return Echeancier(periodes, null, arrondi(totalInterets), null, "Le prêt ne se rembourse pas avec ce versement.")
    }

    private fun debutJour(d: Date): Date = Calendar.getInstance().apply {
        time = d; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time

    private fun finJour(d: Date): Date = Calendar.getInstance().apply {
        time = d; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.time

    fun nombrePaiementsContrat(dureeMois: Int, frequence: FrequencePaiementDette): Int =
        (dureeMois * frequence.paiementsParAn / 12.0).roundToInt().coerceAtLeast(1)

    /**
     * Date de l'échéance n° [numero] (1 = premier versement). Sans [premier] connu, le premier
     * versement arrive une période après le début du contrat ; avec [premier], tout part de cette date.
     */
    private fun dateEcheance(
        debut: Date,
        numero: Int,
        jour: Int,
        frequence: FrequencePaiementDette,
        premier: Date? = null
    ): Date {
        // Avec un 1er versement connu : la n-ième échéance est n − 1 périodes après lui
        val base = premier ?: debut
        val periodes = if (premier != null) numero - 1 else numero
        return Calendar.getInstance().apply {
            time = base
            when (frequence) {
                FrequencePaiementDette.MENSUELLE -> {
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, periodes)
                    set(Calendar.DAY_OF_MONTH, min(jour, getActualMaximum(Calendar.DAY_OF_MONTH)))
                }
                FrequencePaiementDette.AUX_DEUX_SEMAINES -> add(Calendar.DAY_OF_YEAR, periodes * 14)
                FrequencePaiementDette.HEBDOMADAIRE -> add(Calendar.DAY_OF_YEAR, periodes * 7)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    private fun avancerPeriode(date: Date, jour: Int, frequence: FrequencePaiementDette): Date =
        Calendar.getInstance().apply {
            time = date
            when (frequence) {
                FrequencePaiementDette.MENSUELLE -> {
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, 1)
                    set(Calendar.DAY_OF_MONTH, min(jour, getActualMaximum(Calendar.DAY_OF_MONTH)))
                }
                FrequencePaiementDette.AUX_DEUX_SEMAINES -> add(Calendar.DAY_OF_YEAR, 14)
                FrequencePaiementDette.HEBDOMADAIRE -> add(Calendar.DAY_OF_YEAR, 7)
            }
        }.time

    private fun reculerPeriode(date: Date, frequence: FrequencePaiementDette): Date =
        Calendar.getInstance().apply {
            time = date
            when (frequence) {
                FrequencePaiementDette.MENSUELLE -> add(Calendar.MONTH, -1)
                FrequencePaiementDette.AUX_DEUX_SEMAINES -> add(Calendar.DAY_OF_YEAR, -14)
                FrequencePaiementDette.HEBDOMADAIRE -> add(Calendar.DAY_OF_YEAR, -7)
            }
        }.time
}
