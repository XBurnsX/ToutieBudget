package com.xburnsx.toutiebudget.data.services

import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

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
     * Vérifie et met à jour tous les objectifs bihebdomadaires et annuels qui ont terminé leur cycle.
     * À appeler périodiquement (par exemple au chargement des données du budget).
     */
    suspend fun verifierEtResetterObjectifsBihebdomadaires(): Result<List<Enveloppe>> {
        return try {
            // Récupérer toutes les enveloppes
            val enveloppesResult = enveloppeRepository.recupererToutesLesEnveloppes()
            val enveloppes = enveloppesResult.getOrElse {
                return Result.failure(Exception("Erreur lors de la récupération des enveloppes"))
            }

            // Filtrer les objectifs bihebdomadaires et annuels qui ont besoin d'un reset
            val enveloppesAResetter = enveloppes.filter { enveloppe ->
                (enveloppe.typeObjectif == TypeObjectif.Bihebdomadaire && doitEtreResetBihebdomadaire(enveloppe)) ||
                (enveloppe.typeObjectif == TypeObjectif.Annuel && doitEtreResetAnnuel(enveloppe))
            }

            // Mettre à jour chaque enveloppe qui a besoin d'un reset
            val enveloppesResetees = mutableListOf<Enveloppe>()

            for (enveloppe in enveloppesAResetter) {
                val enveloppeResetee = when (enveloppe.typeObjectif) {
                    TypeObjectif.Bihebdomadaire -> resetterObjectifBihebdomadaire(enveloppe)
                    TypeObjectif.Annuel -> resetterObjectifAnnuel(enveloppe)
                    else -> enveloppe // Ne devrait jamais arriver
                }

                // Sauvegarder en base
                val updateResult = enveloppeRepository.mettreAJourEnveloppe(enveloppeResetee)
                if (updateResult.isSuccess) {
                    enveloppesResetees.add(enveloppeResetee)
                } else {
                    // Log l'erreur si nécessaire
                }
            }

            Result.success(enveloppesResetees)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Vérifie si un objectif bihebdomadaire doit être reseté.
     * Reset quand aujourd'hui = date_objectif + 1 jour (pour laisser le temps de payer).
     */
    private fun doitEtreResetBihebdomadaire(enveloppe: Enveloppe): Boolean {
        val dateDebutObjectif = enveloppe.dateDebutObjectif ?: return false

        // Calculer la date d'objectif actuelle (date_debut + 14 jours)
        val dateObjectifActuelle = Calendar.getInstance().apply {
            time = dateDebutObjectif
            add(Calendar.DAY_OF_YEAR, 14)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        // Calculer la date de reset (date_objectif + 1 jour de grâce)
        val dateReset = Calendar.getInstance().apply {
            time = dateObjectifActuelle
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
     * Reset quand aujourd'hui = date_debut_objectif + 12 mois + 1 jour (pour laisser le temps de payer).
     */
    private fun doitEtreResetAnnuel(enveloppe: Enveloppe): Boolean {
        val dateDebutObjectif = enveloppe.dateDebutObjectif ?: return false

        // Calculer la date de fin de l'objectif annuel (date_debut + 12 mois)
        val dateFinObjectif = Calendar.getInstance().apply {
            time = dateDebutObjectif
            add(Calendar.MONTH, 12)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        // Calculer la date de reset (date_fin + 1 jour de grâce)
        val dateReset = Calendar.getInstance().apply {
            time = dateFinObjectif
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
     * - date_debut_objectif = ancienne date_objectif
     * - date_objectif = nouvelle date_debut + 14 jours
     * - Reset le solde alloué à 0 pour le nouveau cycle
     */
    private suspend fun resetterObjectifBihebdomadaire(enveloppe: Enveloppe): Enveloppe {
        val dateDebutObjectif = enveloppe.dateDebutObjectif ?: return enveloppe

        // Calculer l'ancienne date d'objectif (date_debut + 14 jours)
        val ancienneDateObjectif = Calendar.getInstance().apply {
            time = dateDebutObjectif
            add(Calendar.DAY_OF_YEAR, 14)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        // La nouvelle date de début = ancienne date d'objectif
        val nouvelleDateDebut = ancienneDateObjectif

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
        
        // Reset le solde alloué ET les dépenses à 0 pour le nouveau cycle
        // Important : reset aussi depense car solde = alloue - depense
        val allocationResetee = allocationExistante.copy(
            solde = 0.0,
            alloue = 0.0,
            depense = 0.0
        )
        
        // Mettre à jour l'allocation en base
        allocationMensuelleRepository.mettreAJourAllocation(allocationResetee)

        return enveloppe.copy(
            dateDebutObjectif = nouvelleDateDebut,
            dateObjectif = nouvelleDateObjectif.toString()
        )
    }

    /**
     * Reset un objectif annuel selon la logique :
     * - date_debut_objectif = ancienne date_fin_objectif
     * - date_objectif = nouvelle date_debut + 12 mois
     * - Reset le solde alloué ET les dépenses à 0 pour le nouveau cycle
     */
    private suspend fun resetterObjectifAnnuel(enveloppe: Enveloppe): Enveloppe {
        val dateDebutObjectif = enveloppe.dateDebutObjectif ?: return enveloppe

        // Calculer l'ancienne date de fin d'objectif (date_debut + 12 mois)
        val ancienneDateFinObjectif = Calendar.getInstance().apply {
            time = dateDebutObjectif
            add(Calendar.MONTH, 12)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        // La nouvelle date de début = ancienne date de fin d'objectif
        val nouvelleDateDebut = ancienneDateFinObjectif

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
            dateObjectif = nouvelleDateObjectif.toString()
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
