package com.xburnsx.toutiebudget.data.services

import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Types d'alertes pour les cartes de crédit.
 */
enum class TypeAlerte {
    ECHEANCE_PROCHE,
    PAIEMENT_EN_RETARD,
    UTILISATION_ELEVEE,
    LIMITE_ATTEINTE,
    INTERETS_APPLIQUES,
    PROCHAIN_RELEVE
}

/**
 * Niveaux de priorité pour les alertes.
 */
enum class Priorite(val ordre: Int) {
    CRITIQUE(0),
    HAUTE(1),
    MOYENNE(2),
    BASSE(3)
}

/**
 * Représente une alerte pour une carte de crédit.
 */
data class AlerteCarteCredit(
    val type: TypeAlerte,
    val carte: CompteCredit,
    val message: String,
    val priorite: Priorite,
    val dateCreation: Date = Date()
)

/**
 * CC9 : alertes générées UNIQUEMENT à partir de données réelles (solde, limite, relevés saisis,
 * paiements enregistrés). Sans échéance confirmée, aucune alerte de retard n'est produite.
 * ToutieBudget n'envoie jamais de paiement lui-même.
 */
object GenerateurAlertesCarte {

    private const val JOURS_ECHEANCE_PROCHE = 5
    private const val SEUIL_CREDIT_FAIBLE = 0.10

    fun generer(carte: CompteCredit, transactionsCarte: List<Transaction>, aujourdHui: Date = Date()): List<AlerteCarteCredit> {
        val alertes = mutableListOf<AlerteCarteCredit>()
        val format = SimpleDateFormat("d MMM", Locale.CANADA_FRENCH)
        val dette = MoteurCarteCredit.dette(carte)

        if (MoteurCarteCredit.limiteDepassee(carte)) {
            alertes += AlerteCarteCredit(
                TypeAlerte.LIMITE_ATTEINTE, carte,
                "Limite dépassée de ${MoneyFormatter.formatAmount(dette - carte.limiteCredit)}.",
                Priorite.CRITIQUE
            )
        } else if (carte.limiteCredit > 0 && dette > 0 && MoteurCarteCredit.creditDisponible(carte) < carte.limiteCredit * SEUIL_CREDIT_FAIBLE) {
            alertes += AlerteCarteCredit(
                TypeAlerte.UTILISATION_ELEVEE, carte,
                "Crédit disponible faible : ${MoneyFormatter.formatAmount(MoteurCarteCredit.creditDisponible(carte))}.",
                Priorite.HAUTE
            )
        }

        val releve = MoteurCarteCredit.dernierReleve(carte)
        val echeance = MoteurCarteCredit.lireDate(releve?.dateEcheance)
        val emission = MoteurCarteCredit.lireDate(releve?.dateEmission)
        if (releve != null && echeance != null && emission != null) {
            val minimum = releve.minimumOfficiel ?: MoteurCarteCredit.minimumEstime(carte, releve.soldeOfficiel)
            val paye = MoteurCarteCredit.paiementsDepuis(transactionsCarte, emission)
            val payeAvantEcheance = transactionsCarte.filter {
                it.type == com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Paiement &&
                    !debutJour(it.date).before(debutJour(emission)) &&
                    !debutJour(it.date).after(debutJour(echeance))
            }.sumOf { kotlin.math.max(0.0, it.montant) }
            val joursRestants = java.time.temporal.ChronoUnit.DAYS.between(
                debutJour(aujourdHui).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                echeance.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            )
            when {
                joursRestants < 0 && payeAvantEcheance + 0.005 < minimum -> alertes += AlerteCarteCredit(
                    TypeAlerte.PAIEMENT_EN_RETARD, carte,
                    "Selon les paiements saisis, le minimum du relevé du ${format.format(echeance)} " +
                        "n'a pas été couvert à temps. Vérifiez la réception par la banque.",
                    Priorite.HAUTE
                )
                paye + 0.005 >= minimum -> Unit // minimum couvert dans les mouvements locaux
                joursRestants <= JOURS_ECHEANCE_PROCHE -> alertes += AlerteCarteCredit(
                    TypeAlerte.ECHEANCE_PROCHE, carte,
                    "Minimum de ${MoneyFormatter.formatAmount(minimum - paye)} à payer d'ici le ${format.format(echeance)}.",
                    Priorite.HAUTE
                )
            }
            if (releve.interetsFactures > 0.005) {
                alertes += AlerteCarteCredit(
                    TypeAlerte.INTERETS_APPLIQUES, carte,
                    "Intérêts facturés au dernier relevé : ${MoneyFormatter.formatAmount(releve.interetsFactures)}.",
                    Priorite.MOYENNE
                )
            }
        }

        MoteurCarteCredit.prochaineCloture(carte, aujourdHui)?.let { cloture ->
            val jours = java.time.temporal.ChronoUnit.DAYS.between(
                debutJour(aujourdHui).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                cloture.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            )
            if (jours in 0..JOURS_ECHEANCE_PROCHE) {
                alertes += AlerteCarteCredit(
                    TypeAlerte.PROCHAIN_RELEVE, carte, "Prochain relevé le ${format.format(cloture)}.", Priorite.BASSE
                )
            }
        }

        return alertes.sortedBy { it.priorite.ordre }
    }

    private fun debutJour(date: Date): Date = Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time
}
