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
                    enveloppeRepository.mettreAJourEnveloppe(enveloppeResetee).getOrThrow()
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
                    enveloppeRepository.mettreAJourEnveloppe(enveloppeResetee).getOrThrow()
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
                    enveloppeRepository.mettreAJourEnveloppe(enveloppeResetee).getOrThrow()
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
     * - date_debut_objectif = ancienne date_objectif (même date)
     * - date_objectif = nouvelle date_debut + 14 jours exactement
     * - Reset le solde alloué selon VOTRE logique (garder le surplus)
     */
    private suspend fun resetterObjectifBihebdomadaire(enveloppe: Enveloppe): Enveloppe {
        val dateObjectif = enveloppe.dateObjectif ?: return enveloppe

        // 🆕 CORRECTION : La nouvelle date de début = ancienne date d'objectif (même date)
        // Pas +1 jour ! C'est juste pour le reset qu'on ajoute 1 jour
        val nouvelleDateDebut = dateObjectif

        // 🆕 CORRECTION : Pour un objectif BIHEBDOMADAIRE, nouvelle date = +14 jours exactement
        // Pour respecter le cycle bihebdomadaire (2 semaines)
        val nouvelleDateObjectif = Calendar.getInstance().apply {
            time = nouvelleDateDebut
            add(Calendar.DAY_OF_YEAR, 14)  // ← +14 jours exactement pour 2 semaines
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        // 🆕 RESET DU SOLDE ALLOUÉ : Supprimer SEULEMENT le montant de l'objectif
        // Garder le surplus pour le prochain cycle
        val moisNouveauCycle = obtenirPremierJourDuMois(nouvelleDateDebut)
        
        // Récupérer l'allocation existante pour ce mois ou en créer une nouvelle
        val allocationExistante = allocationMensuelleRepository.recupererOuCreerAllocation(
            enveloppeId = enveloppe.id,
            mois = moisNouveauCycle
        )
        
        // 🆕 CORRECTION : Reset = alloue - objectifMontant (garder le surplus !)
        val montantAReset = enveloppe.objectifMontant
        val nouveauAlloue = allocationExistante.alloue - montantAReset
        
        // Si le nouveau alloué est négatif, le mettre à 0
        val alloueFinal = if (nouveauAlloue < 0.0) 0.0 else nouveauAlloue
        
        val allocationResetee = allocationExistante.copy(
            alloue = alloueFinal  // ← Garde le surplus du cycle précédent !
            // solde et depense restent inchangés (statistiques préservées)
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
        
        // Reset SEULEMENT l'alloué pour le nouveau cycle
        // IMPORTANT : Préserver le solde ET les dépenses (statistiques)
        val allocationResetee = allocationExistante.copy(
            alloue = 0.0  // ← Reset pour nouveau cycle
            // solde et depense restent inchangés (statistiques préservées)
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

        // CE18 : les millisecondes ne sont pas des jours calendaires
        // (passage à l'heure d'été/hiver). Recalculer en dates locales,
        // sans raccourcir une échéance d'un jour à cause du DST.
        val zone = java.time.ZoneId.systemDefault()
        val debutCalendaire = dateDebutObjectif.toInstant().atZone(zone).toLocalDate()
        val finCalendaire = dateObjectif.toInstant().atZone(zone).toLocalDate()
        val periodeEnJours = java.time.temporal.ChronoUnit.DAYS.between(
            debutCalendaire, finCalendaire
        )
        if (periodeEnJours <= 0L) return enveloppe

        // Renouvellement du cycle seulement si l'utilisateur l'a demandé
        // par resetApresEcheance (vérifié dans doitEtreResetEcheance).
        val nouvelleDateDebut = dateObjectif
        val nouvelleDateObjectif = Date.from(
            finCalendaire.plusDays(periodeEnJours)
                .atStartOfDay(zone).toInstant()
        )

        // 🆕 RESET SEULEMENT DES DÉPENSES : Pour les objectifs d'échéance,
        // on garde l'argent non dépensé mais on reset les dépenses
        val moisNouveauCycle = obtenirPremierJourDuMois(nouvelleDateDebut)
        
        // Récupérer l'allocation existante pour ce mois ou en créer une nouvelle
        val allocationExistante = allocationMensuelleRepository.recupererOuCreerAllocation(
            enveloppeId = enveloppe.id,
            mois = moisNouveauCycle
        )
        
        // Reset SEULEMENT l'alloué pour le nouveau cycle
        // IMPORTANT : Préserver le solde ET les dépenses (statistiques)
        val allocationResetee = allocationExistante.copy(
            alloue = 0.0  // ← Reset pour nouveau cycle
            // solde et depense restent inchangés (statistiques préservées)
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
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }



}
