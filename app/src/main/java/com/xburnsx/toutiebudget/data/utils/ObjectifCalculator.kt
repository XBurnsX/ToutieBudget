package com.xburnsx.toutiebudget.data.utils

import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.max

class ObjectifCalculator {

    /**
     * Calcule le montant du versement recommandé pour atteindre l'objectif de l'enveloppe.
     * C'est le montant que l'utilisateur devrait idéalement ajouter à l'enveloppe ce mois-ci.
     */
    fun calculerVersementRecommande(enveloppe: Enveloppe, soldeActuel: Double): Double {
        val objectif = enveloppe.objectifMontant
        // Si pas d'objectif ou si l'objectif est déjà atteint par le solde, pas de versement nécessaire.
        if (objectif <= 0 || soldeActuel >= objectif) return 0.0

        return when (enveloppe.typeObjectif) {
            TypeObjectif.Echeance -> calculerVersementEcheance(soldeActuel, objectif, enveloppe.dateDebutObjectif)
            TypeObjectif.Annuel -> calculerVersementAnnuel(soldeActuel, objectif, enveloppe.dateDebutObjectif)
            TypeObjectif.Mensuel -> calculerVersementMensuel(soldeActuel, objectifMensuel = objectif)
            TypeObjectif.Bihebdomadaire -> calculerVersementBihebdomadaire(soldeActuel, objectif, enveloppe.dateDebutObjectif)
            else -> 0.0 // Pour 'Aucun' ou autres cas.
        }
    }

    /**
     * Pour un objectif à ÉCHÉANCE fixe.
     * Calcule le montant à épargner chaque mois pour atteindre le total à la date butoir.
     */
    private fun calculerVersementEcheance(soldeActuel: Double, objectifTotal: Double, dateEcheance: Date?): Double {
        if (dateEcheance == null) return 0.0 // Pas de date, pas de calcul.
        val montantRestant = objectifTotal - soldeActuel
        if (montantRestant <= 0) return 0.0

        val joursRestants = TimeUnit.MILLISECONDS.toDays(dateEcheance.time - Date().time)
        if (joursRestants <= 0) return montantRestant // Si la date est passée, il faut tout mettre.

        // On divise par le nombre de mois restants (au minimum 1).
        val moisRestants = max(1.0, ceil(joursRestants / 30.44))
        return montantRestant / moisRestants
    }

    /**
     * Pour un objectif ANNUEL récurrent.
     * Calcule ce qu'il faut verser ce mois-ci, en tenant compte d'un éventuel retard.
     */
    private fun calculerVersementAnnuel(soldeActuel: Double, objectifAnnuel: Double, dateDebutObjectif: Date?): Double {
        val dateDebut = dateDebutObjectif ?: return objectifAnnuel // Si pas de date de début, on ne peut rien calculer.
        val versementMensuelIdeal = objectifAnnuel / 12.0

        // Calculer combien de mois se sont écoulés depuis le début du cycle.
        val calDebut = Calendar.getInstance().apply { time = dateDebut }
        val calActuel = Calendar.getInstance()
        var moisEcoules = (calActuel.get(Calendar.YEAR) - calDebut.get(Calendar.YEAR)) * 12
        moisEcoules += calActuel.get(Calendar.MONTH) - calDebut.get(Calendar.MONTH)
        moisEcoules = max(0, moisEcoules)

        // Le montant qui aurait dû être épargné jusqu'à la fin du mois dernier.
        val objectifProrata = versementMensuelIdeal * moisEcoules
        // Le retard accumulé.
        val retard = max(0.0, objectifProrata - soldeActuel)

        // Le versement pour ce mois est le versement idéal + le rattrapage du retard.
        return versementMensuelIdeal + retard
    }

    /**
     * Pour un objectif MENSUEL simple.
     * Le montant à verser est simplement ce qui manque pour atteindre l'objectif du mois.
     */
    private fun calculerVersementMensuel(soldeActuel: Double, objectifMensuel: Double): Double {
        return max(0.0, objectifMensuel - soldeActuel)
    }

    /**
     * Pour un objectif BIHEBDOMADAIRE (toutes les 2 semaines).
     * 50$ première semaine + 50$ deuxième semaine = 100$ total, puis RESET.
     */
    private fun calculerVersementBihebdomadaire(soldeActuel: Double, objectifPeriodique: Double, dateDebutObjectif: Date?): Double {
        val dateDebut = dateDebutObjectif ?: return objectifPeriodique // Nécessite une date de début.

        // Calculer le nombre de jours écoulés depuis le début
        val joursEcoules = TimeUnit.MILLISECONDS.toDays(Date().time - dateDebut.time)
        if (joursEcoules < 0) return 0.0 // L'objectif n'a pas encore commencé.

        // Calculer la position dans le cycle de 14 jours (0-13, puis reset)
        val joursInCycle = (joursEcoules % 14).toInt() // Position dans le cycle actuel (0-13)

        // Déterminer dans quelle semaine on se trouve dans le cycle
        val semaineInCycle = if (joursInCycle < 7) 1 else 2 // Semaine 1 ou 2

        // Calcul de l'objectif pour le cycle actuel - PAS DE PROGRESSION !
        val objectifActuel = if (semaineInCycle == 1) {
            // Première semaine : 50$ COMPLET
            objectifPeriodique / 2.0  // 100$ ÷ 2 = 50$
        } else {
            // Deuxième semaine : 50$ + 50$ = 100$ COMPLET
            objectifPeriodique  // 100$ complet
        }

        // Debug pour comprendre le calcul
        println("[DEBUG] Bihebdomadaire - Jours écoulés: $joursEcoules")
        println("[DEBUG] Bihebdomadaire - Jours in cycle (0-13): $joursInCycle")
        println("[DEBUG] Bihebdomadaire - Semaine in cycle: $semaineInCycle/2")
        if (semaineInCycle == 1) {
            println("[DEBUG] Bihebdomadaire - PREMIÈRE SEMAINE - Objectif: 50$")
        } else {
            println("[DEBUG] Bihebdomadaire - DEUXIÈME SEMAINE - Objectif: 100$")
        }
        println("[DEBUG] Bihebdomadaire - Objectif actuel pour ce cycle: $objectifActuel")
        println("[DEBUG] Bihebdomadaire - Solde actuel: $soldeActuel")

        // Le montant nécessaire est la différence entre l'objectif et le solde actuel
        val versementRecommande = max(0.0, objectifActuel - soldeActuel)
        println("[DEBUG] Bihebdomadaire - Versement recommandé: $versementRecommande")

        return versementRecommande
    }
}
