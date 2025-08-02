package com.xburnsx.toutiebudget.data.utils

import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 📊 CALCULATEUR D'OBJECTIFS INTELLIGENT AVEC RATTRAPAGE
 * 
 * 🎯 LOGIQUE INTELLIGENTE : Objectif mensuel + rattrapage du retard accumulé
 * 
 * Cette classe calcule les suggestions de façon INTELLIGENTE :
 * elle suggère l'objectif mensuel PLUS le rattrapage du retard accumulé.
 * C'est la seule façon d'atteindre les objectifs !
 * 
 * 📅 VARIABLES UTILISÉES :
 * - `allocationsMensuelles` : Historique complet pour calculer le retard
 * - `dateDebutObjectif` : Point de départ exact de l'objectif
 * - `dateObjectif` : Date d'échéance pour les objectifs à échéance
 * - `objectifMontant` : Montant total de l'objectif
 * - `moisSelectionne` : Mois pour lequel on calcule la suggestion
 * 
 * 📈 EXEMPLE CONCRET (Objectif annuel 120$ = 10$/mois, commencé en Juin) :
 * 
 * 🔍 LOGIQUE DE RATTRAPAGE INTELLIGENT :
 * - Juin : Devrait avoir 10$, a 0$ → Suggère 10$
 * - Juillet : Devrait avoir 20$, a 5$ → Suggère 15$ (10$ + 5$ de retard)
 * - Août : Devrait avoir 30$, a 20$ → Suggère 10$ (objectif mensuel normal)
 * - Septembre : Devrait avoir 40$, a 25$ → Suggère 15$ (10$ + 5$ de retard)
 * 
 * 📊 RATTRAPAGE LOGIQUE :
 * - Si vous êtes en retard, ça vous dit combien mettre pour rattraper
 * - Si vous êtes à jour, ça suggère l'objectif mensuel normal
 * - Navigation cohérente basée sur l'historique réel
 * 
 * ✅ AVANTAGES :
 * - Rattrapage intelligent pour atteindre les objectifs
 * - Suggestions réalistes et cohérentes
 * - Basé sur l'historique réel d'allocations
 * - Navigation temporelle stable
 */
class ObjectifCalculator {

    /**
     * Parse une date depuis un string PocketBase
     */
    private fun parseDateFromPocketBase(dateString: String?): Date? {
        if (dateString == null) return null
        
        return try {
            when {
                dateString.contains("T") -> {
                    // Format ISO: "2024-01-15T10:30:00.000Z"
                    val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault())
                    isoFormat.parse(dateString)
                }
                dateString.contains(" ") -> {
                    // Format avec espace: "2024-01-15 10:30:00.000Z"
                    val spaceFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", java.util.Locale.getDefault())
                    spaceFormat.parse(dateString)
                }
                dateString.matches(Regex("""\d{4}-\d{2}-\d{2}""")) -> {
                    // Format simple: "2024-01-15"
                    val simpleFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    simpleFormat.parse(dateString)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calcule le montant du versement recommandé pour atteindre l'objectif de l'enveloppe.
     * C'est le montant que l'utilisateur devrait idéalement ajouter à l'enveloppe ce mois-ci.
     * 
     * @param enveloppe L'enveloppe concernée
     * @param soldeActuel Le solde actuel de l'enveloppe
     * @param moisSelectionne Le mois pour lequel on calcule la suggestion (ex: août 2024)
     * @param allocationsMensuelles Liste des allocations mensuelles passées pour cette enveloppe
     */
    fun calculerVersementRecommande(
        enveloppe: Enveloppe, 
        soldeActuel: Double,
        moisSelectionne: Date,
        allocationsMensuelles: List<AllocationMensuelle> = emptyList()
    ): Double {
        val objectif = enveloppe.objectifMontant
        // Si pas d'objectif ou si l'objectif est déjà atteint par le solde, pas de versement nécessaire.
        if (objectif <= 0 || soldeActuel >= objectif) return 0.0

        return when (enveloppe.typeObjectif) {
            TypeObjectif.Echeance -> {
                // 🆕 UTILISER LES VRAIES DATES DEPUIS POCKETBASE
                val dateFinObjectif = enveloppe.dateObjectif
                val dateDebutObjectif = enveloppe.dateDebutObjectif
                calculerVersementEcheance(soldeActuel, objectif, dateFinObjectif, moisSelectionne, allocationsMensuelles, dateDebutObjectif)
            }
            TypeObjectif.Annuel -> {
                // 🆕 UTILISER LES VRAIES DATES DEPUIS POCKETBASE
                val dateFinObjectif = enveloppe.dateObjectif
                val dateDebutObjectif = enveloppe.dateDebutObjectif
                calculerVersementAnnuel(soldeActuel, objectif, dateDebutObjectif, dateFinObjectif, moisSelectionne, allocationsMensuelles)
            }
            TypeObjectif.Mensuel -> calculerVersementMensuel(soldeActuel, objectif)
            TypeObjectif.Bihebdomadaire -> calculerVersementBihebdomadaire(soldeActuel, objectif, enveloppe.dateDebutObjectif)
            else -> 0.0 // Pour 'Aucun' ou autres cas.
        }
    }

    /**
     * Pour un objectif à ÉCHÉANCE fixe.
     * 🎯 LOGIQUE INTELLIGENTE : Objectif mensuel + rattrapage du retard accumulé.
     */
    private fun calculerVersementEcheance(
        soldeActuel: Double, 
        objectifTotal: Double, 
        dateEcheance: Date?,
        moisSelectionne: Date,
        allocationsMensuelles: List<AllocationMensuelle>,
        dateDebutObjectif: Date?
    ): Double {
        if (dateEcheance == null) return 0.0
        
        // 🆕 POUR LES OBJECTIFS ÉCHÉANCE : UTILISER LA VRAIE DATE DE DÉBUT DE L'OBJECTIF OU AUJOURD'HUI
        val dateDebut = dateDebutObjectif ?: Date() // Utiliser la vraie date de début ou aujourd'hui
        
        val joursRestants = TimeUnit.MILLISECONDS.toDays(dateEcheance.time - dateDebut.time)
        if (joursRestants <= 0) return max(0.0, objectifTotal - soldeActuel) // Objectif passé
        
        // 🆕 CALCULER LE TEMPS RESTANT JUSQU'À L'ÉCHÉANCE EN JOURS RÉELS (très précis)
        val calendarEcheance = Calendar.getInstance()
        calendarEcheance.time = dateEcheance
        val calendarSelectionne = Calendar.getInstance()
        calendarSelectionne.time = moisSelectionne
        
        // Calculer les jours restants jusqu'à l'échéance
        val joursRestantsJusquEcheance = TimeUnit.MILLISECONDS.toDays(dateEcheance.time - moisSelectionne.time)
        
        // Si on est après l'échéance, objectif passé
        if (joursRestantsJusquEcheance <= 0) return max(0.0, objectifTotal - soldeActuel)
        
        // 🎯 CALCULER L'OBJECTIF MENSUEL BASÉ SUR LA PÉRIODE TOTALE DE L'OBJECTIF
        // Calculer les jours entre la date de début et la date de fin de l'objectif
        val dateDebutObjectif = dateDebutObjectif ?: dateDebut
        val joursTotalObjectif = TimeUnit.MILLISECONDS.toDays(dateEcheance.time - dateDebutObjectif.time)
        
        // Montant par jour = objectif total ÷ jours total de l'objectif
        val montantParJour = objectifTotal / max(1.0, joursTotalObjectif.toDouble())
        
        // Calculer combien de jours il y a dans le mois sélectionné
        val calendarMois = Calendar.getInstance()
        calendarMois.time = moisSelectionne
        val joursDansMois = calendarMois.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        // Objectif mensuel = montant par jour × jours dans le mois
        val objectifMensuel = montantParJour * joursDansMois
        
        // 🎯 RATTRAPAGE INTELLIGENT POUR LES ÉCHÉANCES
        // Calculer combien de mois se sont écoulés depuis la date de début jusqu'au mois sélectionné
        val calendarDebut = Calendar.getInstance()
        calendarDebut.time = dateDebutObjectif
        val calendarMoisRattrapage = Calendar.getInstance()
        calendarMoisRattrapage.time = moisSelectionne
        
        val moisEcoules = (calendarMoisRattrapage.get(Calendar.YEAR) - calendarDebut.get(Calendar.YEAR)) * 12 +
                (calendarMoisRattrapage.get(Calendar.MONTH) - calendarDebut.get(Calendar.MONTH)) + 1 // +1 pour inclure le mois sélectionné
        
        // Si le mois sélectionné est avant la date de début de l'objectif
        if (moisEcoules <= 0) {
            return objectifMensuel
        }
        
        // 🎯 RATTRAPAGE INTELLIGENT : 
        // Ce qui devrait avoir été alloué depuis la date de début jusqu'au mois sélectionné (inclus)
        // MAIS NE PAS DÉPASSER L'OBJECTIF TOTAL !
        val devraitAvoirAlloue = min(moisEcoules * objectifMensuel, objectifTotal)
        
        // Ce qui a été réellement alloué depuis la date de début jusqu'au mois sélectionné (inclus)
        val totalRealementAlloue = calculerTotalAllocationsDepuisDebut(allocationsMensuelles, dateDebutObjectif, moisSelectionne, inclureMoisSelectionne = true)
        
        // Retard accumulé = ce qui devrait être alloué - ce qui a été réellement alloué
        val retardAccumule = max(0.0, devraitAvoirAlloue - totalRealementAlloue)
        
        // Vérifier ce qui a été alloué pour CE MOIS SEULEMENT
        val allocationCeMois = allocationsMensuelles.find { allocation ->
            val calendarAllocation = Calendar.getInstance()
            calendarAllocation.time = allocation.mois
            calendarAllocation.get(Calendar.YEAR) == calendarMoisRattrapage.get(Calendar.YEAR) &&
            calendarAllocation.get(Calendar.MONTH) == calendarMoisRattrapage.get(Calendar.MONTH)
        }
        
        val dejaAlloqueCeMois = allocationCeMois?.alloue ?: 0.0
        
        // 🎯 SUGGESTION INTELLIGENTE : 
        // Pour le mois courant, toujours suggérer ce qu'il manque pour compléter l'objectif mensuel
        val suggestionMensuelle = max(0.0, objectifMensuel - dejaAlloqueCeMois)
        
        // Si on a du retard accumulé, suggérer le maximum entre le retard et l'objectif mensuel
        val suggestion = if (retardAccumule > 0) {
            max(suggestionMensuelle, retardAccumule - dejaAlloqueCeMois)
        } else {
            suggestionMensuelle
        }
        
        return suggestion
    }

    /**
     * Pour un objectif ANNUEL récurrent.
     * 🎯 LOGIQUE INTELLIGENTE : Objectif mensuel basé sur les jours + rattrapage du retard accumulé.
     */
    private fun calculerVersementAnnuel(
        soldeActuel: Double, 
        objectifAnnuel: Double, 
        dateDebutObjectif: Date?,
        dateFinObjectif: Date?,
        moisSelectionne: Date,
        allocationsMensuelles: List<AllocationMensuelle>
    ): Double {
        // Si pas de date de début, utiliser janvier 1er de l'année courante
        val dateDebut = dateDebutObjectif ?: run {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.MONTH, Calendar.JANUARY)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.time
        }
        
        // Utiliser la vraie date de fin ou calculer 1 an après la date de début
        val dateFin = dateFinObjectif ?: run {
            val calendarFin = Calendar.getInstance()
            calendarFin.time = dateDebut
            calendarFin.add(Calendar.YEAR, 1)
            calendarFin.time
        }
        
        // 🎯 CALCULER L'OBJECTIF MENSUEL BASÉ SUR LA PÉRIODE TOTALE DE L'OBJECTIF
        // Calculer les jours entre la date de début et la date de fin de l'objectif
        val joursTotalObjectif = TimeUnit.MILLISECONDS.toDays(dateFin.time - dateDebut.time)
        
        // Montant par jour = objectif annuel ÷ jours total de l'objectif
        val montantParJour = objectifAnnuel / max(1.0, joursTotalObjectif.toDouble())
        
        // Calculer combien de jours il y a dans le mois sélectionné
        val calendarMois = Calendar.getInstance()
        calendarMois.time = moisSelectionne
        val joursDansMois = calendarMois.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        // Objectif mensuel = montant par jour × jours dans le mois
        val objectifMensuel = montantParJour * joursDansMois
        
        // 🎯 RATTRAPAGE INTELLIGENT POUR LES ANNUELS
        // Calculer combien de mois se sont écoulés depuis la date de début jusqu'au mois sélectionné
        val calendarDebut = Calendar.getInstance()
        calendarDebut.time = dateDebut
        val calendarMoisRattrapage = Calendar.getInstance()
        calendarMoisRattrapage.time = moisSelectionne
        
        val moisEcoules = (calendarMoisRattrapage.get(Calendar.YEAR) - calendarDebut.get(Calendar.YEAR)) * 12 +
                (calendarMoisRattrapage.get(Calendar.MONTH) - calendarDebut.get(Calendar.MONTH)) + 1 // +1 pour inclure le mois sélectionné
        
        // Si le mois sélectionné est avant la date de début de l'objectif
        if (moisEcoules <= 0) {
            return objectifMensuel
        }
        
        // 🎯 RATTRAPAGE INTELLIGENT : 
        // Ce qui devrait avoir été alloué depuis la date de début jusqu'au mois sélectionné (inclus)
        // MAIS NE PAS DÉPASSER L'OBJECTIF TOTAL !
        val devraitAvoirAlloue = min(moisEcoules * objectifMensuel, objectifAnnuel)
        
        // Ce qui a été réellement alloué depuis la date de début jusqu'au mois sélectionné (inclus)
        val totalRealementAlloue = calculerTotalAllocationsDepuisDebut(allocationsMensuelles, dateDebut, moisSelectionne, inclureMoisSelectionne = true)
        
        // Retard accumulé = ce qui devrait être alloué - ce qui a été réellement alloué
        val retardAccumule = max(0.0, devraitAvoirAlloue - totalRealementAlloue)
        
        // Vérifier ce qui a été alloué pour CE MOIS SEULEMENT
        val allocationCeMois = allocationsMensuelles.find { allocation ->
            val calendarAllocation = Calendar.getInstance()
            calendarAllocation.time = allocation.mois
            calendarAllocation.get(Calendar.YEAR) == calendarMoisRattrapage.get(Calendar.YEAR) &&
            calendarAllocation.get(Calendar.MONTH) == calendarMoisRattrapage.get(Calendar.MONTH)
        }
        
        val dejaAlloqueCeMois = allocationCeMois?.alloue ?: 0.0
        
        // 🎯 SUGGESTION INTELLIGENTE : 
        // Pour le mois courant, toujours suggérer ce qu'il manque pour compléter l'objectif mensuel
        val suggestionMensuelle = max(0.0, objectifMensuel - dejaAlloqueCeMois)
        
        // Si on a du retard accumulé, suggérer le maximum entre le retard et l'objectif mensuel
        val suggestion = if (retardAccumule > 0) {
            max(suggestionMensuelle, retardAccumule - dejaAlloqueCeMois)
        } else {
            suggestionMensuelle
        }
        
        return suggestion
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
            max(0.0, objectifSemaine1 - soldeActuel)
        } else {
            // 📅 DEUXIÈME SEMAINE : Suggérer pour rattraper si en retard
            val objectifComplet = objectifPeriodique  // 100$ complet
            val retard = max(0.0, objectifComplet - soldeActuel)
            retard
        }

        return versementRecommande
    }

    /**
     * Calcule le total des montants alloués (pas le solde) jusqu'au mois sélectionné (exclusif).
     */
    private fun calculerTotalAllocationsAllouees(
        allocationsMensuelles: List<AllocationMensuelle>,
        moisSelectionne: Date
    ): Double {
        val calendar = Calendar.getInstance()
        calendar.time = moisSelectionne
        val anneeSelectionnee = calendar.get(Calendar.YEAR)
        val moisSelectionneInt = calendar.get(Calendar.MONTH)
        
        return allocationsMensuelles
            .filter { allocation ->
                val calendarAllocation = Calendar.getInstance()
                calendarAllocation.time = allocation.mois
                val anneeAllocation = calendarAllocation.get(Calendar.YEAR)
                val moisAllocation = calendarAllocation.get(Calendar.MONTH)
                
                // Inclure seulement les allocations jusqu'au mois sélectionné (exclusif)
                (anneeAllocation < anneeSelectionnee) || 
                (anneeAllocation == anneeSelectionnee && moisAllocation < moisSelectionneInt)
            }
            .sumOf { it.alloue } // Utiliser 'alloue' au lieu de 'solde'
    }

    /**
     * Calcule le total des montants alloués pour l'année courante jusqu'au mois sélectionné (exclusif).
     */
    private fun calculerTotalAllocationsAlloueesAnnee(
        allocationsMensuelles: List<AllocationMensuelle>,
        moisSelectionne: Date
    ): Double {
        val calendar = Calendar.getInstance()
        calendar.time = moisSelectionne
        val anneeSelectionnee = calendar.get(Calendar.YEAR)
        val moisSelectionneInt = calendar.get(Calendar.MONTH)
        
        return allocationsMensuelles
            .filter { allocation ->
                val calendarAllocation = Calendar.getInstance()
                calendarAllocation.time = allocation.mois
                val anneeAllocation = calendarAllocation.get(Calendar.YEAR)
                val moisAllocation = calendarAllocation.get(Calendar.MONTH)
                
                // Inclure seulement les allocations de l'année courante jusqu'au mois sélectionné (exclusif)
                anneeAllocation == anneeSelectionnee && moisAllocation < moisSelectionneInt
            }
            .sumOf { it.alloue }
    }

    /**
     * 🎯 FONCTION PRÉCISE : Calcule le total des montants alloués depuis une date de début spécifique 
     * jusqu'au mois sélectionné.
     */
    private fun calculerTotalAllocationsDepuisDebut(
        allocationsMensuelles: List<AllocationMensuelle>,
        dateDebut: Date,
        moisSelectionne: Date,
        inclureMoisSelectionne: Boolean = false
    ): Double {
        val calendarDebut = Calendar.getInstance()
        calendarDebut.time = dateDebut
        val anneeDebut = calendarDebut.get(Calendar.YEAR)
        val moisDebut = calendarDebut.get(Calendar.MONTH)
        
        val calendarSelectionne = Calendar.getInstance()
        calendarSelectionne.time = moisSelectionne
        val anneeSelectionnee = calendarSelectionne.get(Calendar.YEAR)
        val moisSelectionneInt = calendarSelectionne.get(Calendar.MONTH)
        
        return allocationsMensuelles
            .filter { allocation ->
                val calendarAllocation = Calendar.getInstance()
                calendarAllocation.time = allocation.mois
                val anneeAllocation = calendarAllocation.get(Calendar.YEAR)
                val moisAllocation = calendarAllocation.get(Calendar.MONTH)
                
                // Inclure les allocations depuis dateDebut
                val apresDebut = (anneeAllocation > anneeDebut) || 
                               (anneeAllocation == anneeDebut && moisAllocation >= moisDebut)
                
                // Inclure jusqu'au mois sélectionné (inclusif ou exclusif selon le paramètre)
                val avantOuEgalSelection = if (inclureMoisSelectionne) {
                    (anneeAllocation < anneeSelectionnee) || 
                    (anneeAllocation == anneeSelectionnee && moisAllocation <= moisSelectionneInt)
                } else {
                    (anneeAllocation < anneeSelectionnee) || 
                    (anneeAllocation == anneeSelectionnee && moisAllocation < moisSelectionneInt)
                }
                
                apresDebut && avantOuEgalSelection
            }
            .sumOf { it.alloue }
    }

    /**
     * Calcule combien de mois se sont écoulés depuis le début de l'année jusqu'au mois sélectionné.
     */
    private fun calculerMoisEcoulesAnnee(moisSelectionne: Date): Int {
        val calendar = Calendar.getInstance()
        calendar.time = moisSelectionne
        // Janvier = 0, donc ajouter 1 pour avoir le nombre de mois écoulés
        return calendar.get(Calendar.MONTH)
    }

    /**
     * Calcule combien de mois se sont écoulés depuis une date de début jusqu'au mois sélectionné.
     */
    private fun calculerMoisEcoulesDepuisDebut(dateDebut: Date, moisSelectionne: Date): Int {
        val calendarDebut = Calendar.getInstance()
        calendarDebut.time = dateDebut
        
        val calendarSelectionne = Calendar.getInstance()
        calendarSelectionne.time = moisSelectionne
        
        val moisDiff = (calendarSelectionne.get(Calendar.YEAR) - calendarDebut.get(Calendar.YEAR)) * 12 +
                (calendarSelectionne.get(Calendar.MONTH) - calendarDebut.get(Calendar.MONTH))
        
        return max(0, moisDiff)
    }
}
