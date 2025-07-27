package com.xburnsx.toutiebudget.data.utils

import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class ObjectifCalculator @Inject constructor() {

    /**
     * Calcule le montant du versement recommandé pour une enveloppe en fonction de son objectif.
     * La logique s'adapte au progrès actuel pour ajuster les versements futurs.
     *
     * @param enveloppe L'enveloppe pour laquelle calculer le versement.
     * @param progresActuel Le progrès actuel vers l'objectif (solde + dépenses).
     * @return Le montant du versement recommandé. Retourne 0.0 si aucun versement n'est nécessaire.
     */
    fun calculerVersementRecommande(enveloppe: Enveloppe, progresActuel: Double): Double {
        // Pas de calcul si l'objectif n'est pas défini ou est déjà atteint
        if (enveloppe.objectifMontant <= 0 || progresActuel >= enveloppe.objectifMontant) {
            return 0.0
        }

        val montantRestant = (enveloppe.objectifMontant - progresActuel).coerceAtLeast(0.0)

        return when (enveloppe.objectifType) {
            TypeObjectif.Echeance -> {
                enveloppe.objectifDate?.let { dateEcheance ->
                    val dateEcheanceLocal = dateEcheance.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                    val aujourdhui = LocalDate.now()

                    // Si l'échéance est passée, on ne recommande plus de versement
                    if (dateEcheanceLocal.isBefore(aujourdhui)) {
                        return 0.0
                    }

                    // Calcule le nombre de mois restants, en incluant le mois en cours.
                    val moisRestants = ChronoUnit.MONTHS.between(aujourdhui.withDayOfMonth(1), dateEcheanceLocal.withDayOfMonth(1)) + 1

                    if (moisRestants > 0) {
                        // Divise le montant restant par le nombre de mois restants
                        (montantRestant / moisRestants).coerceAtLeast(0.0)
                    } else {
                        // S'il reste moins d'un mois (ou si l'échéance est ce mois-ci),
                        // il faut verser tout ce qui reste.
                        montantRestant
                    }
                } ?: 0.0 // Si la date n'est pas définie pour l'échéance, retourne 0
            }
            TypeObjectif.Mensuel -> {
                // Pour un objectif mensuel, le versement recommandé est simplement le montant
                // restant pour atteindre l'objectif ce mois-ci.
                montantRestant
            }
            TypeObjectif.Annuel -> {
                val aujourdhui = LocalDate.now()
                val finAnnee = LocalDate.of(aujourdhui.year, 12, 31)

                // Si la fin de l'année est déjà passée (ne devrait pas arriver en cours d'année)
                if (aujourdhui.isAfter(finAnnee)) {
                    return montantRestant // Il faut tout verser
                }

                val moisRestants = ChronoUnit.MONTHS.between(aujourdhui.withDayOfMonth(1), finAnnee.withDayOfMonth(1)) + 1
                if (moisRestants > 0) {
                    (montantRestant / moisRestants).coerceAtLeast(0.0)
                } else {
                    montantRestant // Moins d'un mois restant
                }
            }
            TypeObjectif.Bihebdomadaire -> {
                // Un mois contient environ 2.167 périodes bihebdomadaires (4.33 semaines / 2).
                // Le versement mensuel recommandé est le montant de l'objectif multiplié par ce facteur.
                (enveloppe.objectifMontant * 2.167).coerceAtLeast(0.0)
            }
            TypeObjectif.Aucun -> 0.0
        }
    }
}
