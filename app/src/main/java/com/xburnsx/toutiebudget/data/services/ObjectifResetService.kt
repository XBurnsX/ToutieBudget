package com.xburnsx.toutiebudget.data.services

import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Service responsable du reset automatique des objectifs bihebdomadaires.
 * Vérifie si les cycles de 2 semaines sont terminés et met à jour les dates automatiquement.
 */
class ObjectifResetService(
    private val enveloppeRepository: EnveloppeRepository
) {

    /**
     * Vérifie et met à jour tous les objectifs bihebdomadaires qui ont terminé leur cycle.
     * À appeler périodiquement (par exemple au chargement des données du budget).
     */
    suspend fun verifierEtResetterObjectifsBihebdomadaires(): Result<List<Enveloppe>> {
        return try {
            // Récupérer toutes les enveloppes
            val enveloppesResult = enveloppeRepository.recupererToutesLesEnveloppes()
            val enveloppes = enveloppesResult.getOrElse {
                return Result.failure(Exception("Erreur lors de la récupération des enveloppes"))
            }

            // Filtrer les objectifs bihebdomadaires qui ont besoin d'un reset
            val enveloppesAResetter = enveloppes.filter { enveloppe ->
                enveloppe.typeObjectif == TypeObjectif.Bihebdomadaire &&
                doitEtreReset(enveloppe)
            }

            // Mettre à jour chaque enveloppe qui a besoin d'un reset
            val enveloppesResetees = mutableListOf<Enveloppe>()

            for (enveloppe in enveloppesAResetter) {
                val enveloppeResetee = resetterObjectifBihebdomadaire(enveloppe)

                // Sauvegarder en base
                val updateResult = enveloppeRepository.mettreAJourEnveloppe(enveloppeResetee)
                if (updateResult.isSuccess) {
                    enveloppesResetees.add(enveloppeResetee)
                } else {
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
    private fun doitEtreReset(enveloppe: Enveloppe): Boolean {
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
     * Reset un objectif bihebdomadaire selon la logique :
     * - date_debut_objectif = ancienne date_objectif
     * - date_objectif = nouvelle date_debut + 14 jours
     */
    private fun resetterObjectifBihebdomadaire(enveloppe: Enveloppe): Enveloppe {
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

        return enveloppe.copy(
            dateDebutObjectif = nouvelleDateDebut,
            dateObjectif = nouvelleDateObjectif.toString()
        )
    }
}
