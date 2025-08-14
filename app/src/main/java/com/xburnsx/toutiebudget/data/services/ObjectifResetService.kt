package com.xburnsx.toutiebudget.data.services

import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import java.util.Calendar
import java.util.Date

/**
 * Service responsable du reset automatique des objectifs bihebdomadaires.
 * Vérifie si les cycles de 2 semaines sont terminés et met à jour les dates automatiquement.
 * Reset également le solde alloué à 0 pour le nouveau cycle.
 */
class ObjectifResetService(
    private val enveloppeRepository: EnveloppeRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository
) {

    /**
     * Vérifie si un objectif d'échéance doit être reset.
     * Un objectif d'échéance doit être reset si :
     * - La date de fin d'échéance est dépassée
     * - L'enveloppe a resetApresEcheance = true
     */
    private fun doitEtreResetEcheance(enveloppe: Enveloppe): Boolean {
        if (enveloppe.typeObjectif != TypeObjectif.Echeance) return false
        if (!enveloppe.resetApresEcheance) return false
        
        val dateObjectif = enveloppe.dateObjectif ?: return false
        
        // Vérifier si la date de fin d'échéance est dépassée (avec 1 jour de grâce)
        val maintenant = Date()
        val dateFinPlusGrace = Calendar.getInstance().apply {
            time = dateObjectif
            add(Calendar.DAY_OF_MONTH, 1) // 1 jour de grâce
        }.time
        
        return maintenant.after(dateFinPlusGrace)
    }

    /**
     * Vérifie et reset automatiquement les objectifs bihebdomadaires.
     */
    suspend fun verifierEtResetterObjectifsBihebdomadaires(): Result<List<Enveloppe>> {
        return try {
            val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
            val enveloppesResetees = mutableListOf<Enveloppe>()
            
            enveloppes.forEach { enveloppe ->
                if (doitEtreResetBihebdomadaire(enveloppe)) {
                    val enveloppeResetee = resetterObjectifBihebdomadaire(enveloppe)
                    enveloppeRepository.mettreAJourEnveloppe(enveloppeResetee)
                    enveloppesResetees.add(enveloppeResetee)
                }
            }
            
            Result.success(enveloppesResetees)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Vérifie et reset automatiquement les objectifs annuels.
     */
    suspend fun verifierEtResetterObjectifsAnnuels(): Result<List<Enveloppe>> {
        return try {
            val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
            val enveloppesResetees = mutableListOf<Enveloppe>()
            
            enveloppes.forEach { enveloppe ->
                if (doitEtreResetAnnuel(enveloppe)) {
                    val enveloppeResetee = resetterObjectifAnnuel(enveloppe)
                    enveloppeRepository.mettreAJourEnveloppe(enveloppeResetee)
                    enveloppesResetees.add(enveloppeResetee)
                }
            }
            
            Result.success(enveloppesResetees)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Vérifie et reset automatiquement les objectifs d'échéance.
     */
    suspend fun verifierEtResetterObjectifsEcheance(): Result<List<Enveloppe>> {
        return try {
            val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
            val enveloppesResetees = mutableListOf<Enveloppe>()
            
            enveloppes.forEach { enveloppe ->
                if (doitEtreResetEcheance(enveloppe)) {
                    val enveloppeResetee = resetterObjectifEcheance(enveloppe)
                    enveloppeRepository.mettreAJourEnveloppe(enveloppeResetee)
                    enveloppesResetees.add(enveloppeResetee)
                }
            }
            
            Result.success(enveloppesResetees)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Vérifie si un objectif bihebdomadaire doit être reseté.
     * Reset quand aujourd'hui = date_objectif (date de fin) + 1 jour (pour laisser le temps de payer).
     */
    private fun doitEtreResetBihebdomadaire(enveloppe: Enveloppe): Boolean {
        if (enveloppe.typeObjectif != TypeObjectif.Bihebdomadaire) return false
        
        val dateObjectif = enveloppe.dateObjectif ?: return false

        // Calculer la date de reset (date_objectif + 1 jour de grâce)
        val dateReset = Calendar.getInstance().apply {
            time = dateObjectif
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val aujourdhui = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val doitReset = aujourdhui >= dateReset

        return doitReset
    }

    /**
     * Vérifie si un objectif annuel doit être reseté.
     * Reset quand aujourd'hui = date_objectif (date de fin) + 1 jour (pour laisser le temps de payer).
     */
    private fun doitEtreResetAnnuel(enveloppe: Enveloppe): Boolean {
        if (enveloppe.typeObjectif != TypeObjectif.Annuel) return false
        
        val dateObjectif = enveloppe.dateObjectif ?: return false

        // Calculer la date de reset (date_objectif + 1 jour de grâce)
        val dateReset = Calendar.getInstance().apply {
            time = dateObjectif
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val aujourdhui = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val doitReset = aujourdhui >= dateReset

        return doitReset
    }

    /**
     * Reset un objectif bihebdomadaire selon la logique :
     * - date_debut_objectif = ancienne date_objectif (la date de fin qui vient de passer)
     * - date_objectif = nouvelle date_debut + 14 jours
     * - Reset le solde alloué à 0 pour le nouveau cycle
     */
    private suspend fun resetterObjectifBihebdomadaire(enveloppe: Enveloppe): Enveloppe {
        val dateObjectif = enveloppe.dateObjectif ?: return enveloppe

        // La nouvelle date de début = ancienne date d'objectif (la date de fin qui vient de passer)
        val nouvelleDateDebut = dateObjectif

        // La nouvelle date d'objectif = nouvelle date de début + 14 jours
        val nouvelleDateObjectif = Calendar.getInstance().apply {
            time = nouvelleDateDebut
            add(Calendar.DAY_OF_YEAR, 14)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        // 🆕 RESET DU SOLDE ALLOUÉ : Créer une nouvelle allocation avec solde = 0
        // pour le nouveau cycle (mois de la nouvelle date de début)
        val moisNouveauCycle = obtenirPremierJourDuMois(nouvelleDateDebut)
        
        // Récupérer l'allocation existante pour ce mois ou en créer une nouvelle
        val allocationExistante = allocationMensuelleRepository.recupererOuCreerAllocation(
            enveloppeId = enveloppe.id,
            mois = moisNouveauCycle
        )
        
        // Reset seulement l'alloué et les dépenses à 0 pour le nouveau cycle
        // IMPORTANT : Garder le solde existant pour ne pas perdre l'argent
        val allocationResetee = allocationExistante.copy(
            solde = allocationExistante.solde,  // ← Conserver le solde existant
            alloue = 0.0,
            depense = 0.0
        )
        
        // Mettre à jour l'allocation en base
        allocationMensuelleRepository.mettreAJourAllocation(allocationResetee)

        return enveloppe.copy(
            dateDebutObjectif = nouvelleDateDebut,
            dateObjectif = nouvelleDateObjectif
        )
    }

    /**
     * Reset un objectif annuel selon la logique :
     * - date_debut_objectif = ancienne date_objectif (la date de fin qui vient de passer)
     * - date_objectif = nouvelle date_debut + 12 mois
     * - Reset le solde alloué ET les dépenses à 0 pour le nouveau cycle
     */
    private suspend fun resetterObjectifAnnuel(enveloppe: Enveloppe): Enveloppe {
        val dateObjectif = enveloppe.dateObjectif ?: return enveloppe

        // La nouvelle date de début = ancienne date d'objectif (la date de fin qui vient de passer)
        val nouvelleDateDebut = dateObjectif

        // La nouvelle date d'objectif = nouvelle date de début + 12 mois
        val nouvelleDateObjectif = Calendar.getInstance().apply {
            time = nouvelleDateDebut
            add(Calendar.MONTH, 12)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        // 🆕 RESET DU SOLDE ALLOUÉ ET DES DÉPENSES : Pour les objectifs annuels,
        // les dépenses s'accumulent sur 12 mois, donc il faut tout reset
        val moisNouveauCycle = obtenirPremierJourDuMois(nouvelleDateDebut)
        
        // Récupérer l'allocation existante pour ce mois ou en créer une nouvelle
        val allocationExistante = allocationMensuelleRepository.recupererOuCreerAllocation(
            enveloppeId = enveloppe.id,
            mois = moisNouveauCycle
        )
        
        // Reset le solde alloué ET les dépenses à 0 pour le nouveau cycle
        // Important : reset aussi depense car les dépenses s'accumulent sur 12 mois
        val allocationResetee = allocationExistante.copy(
            solde = 0.0,
            alloue = 0.0,
            depense = 0.0
        )
        
        // Mettre à jour l'allocation en base
        allocationMensuelleRepository.mettreAJourAllocation(allocationResetee)

        return enveloppe.copy(
            dateDebutObjectif = nouvelleDateDebut,
            dateObjectif = nouvelleDateObjectif
        )
    }

    /**
     * Reset un objectif d'échéance avec reset automatique selon la logique :
     * - Calculer la période exacte entre dateDebutObjectif et dateObjectif
     * - date_debut_objectif = ancienne date_objectif (la date de fin qui vient de passer)
     * - date_objectif = nouvelle date_debut + période calculée
     * - Reset SEULEMENT les dépenses à 0 (l'argent non dépensé reste)
     */
    private suspend fun resetterObjectifEcheance(enveloppe: Enveloppe): Enveloppe {
        val dateDebutObjectif = enveloppe.dateDebutObjectif ?: return enveloppe
        val dateObjectif = enveloppe.dateObjectif ?: return enveloppe

        // 🆕 CALCULER LA PÉRIODE EXACTE entre début et fin
        val periodeEnMillis = dateObjectif.time - dateDebutObjectif.time
        val periodeEnJours = periodeEnMillis / (1000L * 60 * 60 * 24)

        // La nouvelle date de début = ancienne date d'objectif (la date de fin qui vient de passer)
        val nouvelleDateDebut = dateObjectif

        // La nouvelle date d'objectif = nouvelle date de début + période calculée
        val nouvelleDateObjectif = Calendar.getInstance().apply {
            time = nouvelleDateDebut
            add(Calendar.DAY_OF_YEAR, periodeEnJours.toInt())
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        // 🆕 RESET SEULEMENT DES DÉPENSES : Pour les objectifs d'échéance,
        // on garde l'argent non dépensé mais on reset les dépenses
        val moisNouveauCycle = obtenirPremierJourDuMois(nouvelleDateDebut)
        
        // Récupérer l'allocation existante pour ce mois ou en créer une nouvelle
        val allocationExistante = allocationMensuelleRepository.recupererOuCreerAllocation(
            enveloppeId = enveloppe.id,
            mois = moisNouveauCycle
        )
        
        // Reset SEULEMENT les dépenses à 0, garder le solde et l'alloué
        val allocationResetee = allocationExistante.copy(
            depense = 0.0
            // solde et alloue restent inchangés
        )
        
        // Mettre à jour l'allocation en base
        allocationMensuelleRepository.mettreAJourAllocation(allocationResetee)

        return enveloppe.copy(
            dateDebutObjectif = nouvelleDateDebut,
            dateObjectif = nouvelleDateObjectif
        )
    }
    
    /**
     * Obtient le premier jour du mois pour une date donnée.
     */
    private fun obtenirPremierJourDuMois(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }
}
