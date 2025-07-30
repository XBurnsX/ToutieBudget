package com.xburnsx.toutiebudget.data.utils

import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

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
            TypeObjectif.Echeance -> {
                // Pour les échéances, utiliser dateObjectif qui contient la date d'échéance
                val dateEcheance = enveloppe.dateObjectif?.let { dateString ->
                    try {
                        // Parser la date d'échéance depuis le string
                        when {
                            dateString.contains("T") -> {
                                val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault())
                                isoFormat.parse(dateString)
                            }
                            dateString.contains(" ") -> {
                                val spaceFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", java.util.Locale.getDefault())
                                spaceFormat.parse(dateString)
                            }
                            dateString.matches(Regex("""\d{4}-\d{2}-\d{2}""")) -> {
                                val simpleFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                simpleFormat.parse(dateString)
                            }
                            else -> null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                calculerVersementEcheance(soldeActuel, objectif, dateEcheance)
            }
            TypeObjectif.Annuel -> calculerVersementAnnuel(soldeActuel, objectif, enveloppe.dateDebutObjectif)
            TypeObjectif.Mensuel -> calculerVersementMensuel(soldeActuel, objectif)
            TypeObjectif.Bihebdomadaire -> calculerVersementBihebdomadaire(soldeActuel, objectif, enveloppe.dateDebutObjectif)
            else -> 0.0 // Pour 'Aucun' ou autres cas.
        }
    }

    /**
     * Pour un objectif à ÉCHÉANCE fixe.
     * 🎯 LOGIQUE INTELLIGENTE : Calcule rattrapage si en retard.
     */
    private fun calculerVersementEcheance(soldeActuel: Double, objectifTotal: Double, dateEcheance: Date?): Double {
        if (dateEcheance == null) return 0.0
        
        val maintenant = Date()
        val joursRestants = TimeUnit.MILLISECONDS.toDays(dateEcheance.time - maintenant.time)
        if (joursRestants <= 0) return max(0.0, objectifTotal - soldeActuel) // Objectif passé
        
        // 🎯 LOGIQUE SIMPLE : Diviser l'objectif par les mois restants
        val moisRestants = max(1.0, ceil(joursRestants / 30.44))
        val versementMensuelNecessaire = objectifTotal / moisRestants
        
        // DEBUG pour voir ce qui se passe
        println("[DEBUG] Échéance - Objectif total: $objectifTotal")
        println("[DEBUG] Échéance - Jours restants: $joursRestants")
        println("[DEBUG] Échéance - Mois restants: $moisRestants")
        println("[DEBUG] Échéance - Versement mensuel nécessaire: $versementMensuelNecessaire")
        println("[DEBUG] Échéance - Solde actuel: $soldeActuel")
        
        // Suggérer = ce qu'il faut ce mois - ce qu'on a déjà ce mois
        val suggestion = max(0.0, versementMensuelNecessaire - soldeActuel)
        println("[DEBUG] Échéance - Suggestion finale: $suggestion")
        
        return suggestion
    }

    /**
     * Pour un objectif ANNUEL récurrent.
     * Calcule ce qu'il faut verser ce mois-ci, en tenant compte d'un éventuel retard.
     */
    private fun calculerVersementAnnuel(soldeActuel: Double, objectifAnnuel: Double, dateDebutObjectif: Date?): Double {
        // 🎯 LOGIQUE SIMPLE : Objectif mensuel - Ce qu'on a déjà ce mois
        val versementMensuelIdeal = objectifAnnuel / 12.0
        return max(0.0, versementMensuelIdeal - soldeActuel)
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
    private fun calculerVersementBihebdomadaire(
        soldeActuel: Double, objectifPeriodique: Double, dateDebutObjectif: Date?
    ): Double {
        val dateDebut = dateDebutObjectif ?: return objectifPeriodique // Nécessite une date de début.

        // Calculer le nombre de jours écoulés depuis le début
        val joursEcoules = TimeUnit.MILLISECONDS.toDays(Date().time - dateDebut.time)
        if (joursEcoules < 0) return 0.0 // L'objectif n'a pas encore commencé.

        // Calculer la position dans le cycle de 14 jours (0-13, puis reset)
        val joursInCycle = (joursEcoules % 14).toInt() // Position dans le cycle actuel (0-13)

        // Déterminer dans quelle semaine on se trouve dans le cycle
        val semaineInCycle = if (joursInCycle < 7) 1 else 2 // Semaine 1 ou 2

        // 🎯 LOGIQUE INTELLIGENTE BIHEBDOMADAIRE
        val versementRecommande = if (semaineInCycle == 1) {
            // 📅 PREMIÈRE SEMAINE : Suggérer pour atteindre 50$
            val objectifSemaine1 = objectifPeriodique / 2.0  // 100$ ÷ 2 = 50$
            println("[DEBUG] Bihebdomadaire - PREMIÈRE SEMAINE - Objectif: $objectifSemaine1")
            max(0.0, objectifSemaine1 - soldeActuel)
        } else {
            // 📅 DEUXIÈME SEMAINE : Suggérer pour rattraper si en retard
            val objectifComplet = objectifPeriodique  // 100$ complet
            val retard = max(0.0, objectifComplet - soldeActuel)
            println("[DEBUG] Bihebdomadaire - DEUXIÈME SEMAINE - Objectif: $objectifComplet, Retard: $retard")
            retard
        }

        // Debug pour comprendre le calcul
        println("[DEBUG] Bihebdomadaire - Jours écoulés: $joursEcoules")
        println("[DEBUG] Bihebdomadaire - Jours in cycle (0-13): $joursInCycle")
        println("[DEBUG] Bihebdomadaire - Semaine in cycle: $semaineInCycle/2")
        println("[DEBUG] Bihebdomadaire - Solde actuel: $soldeActuel")
        println("[DEBUG] Bihebdomadaire - Versement recommandé: $versementRecommande")

        return versementRecommande
    }
}
