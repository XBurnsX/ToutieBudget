package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.ui.virement.VirementErrorMessages
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.util.Calendar
import java.util.Date

/**
 * Virements INTERNES au budget (section 7 du TXT) : « Prêt à placer » ↔ enveloppes et enveloppe → enveloppe.
 *
 * - V1 : tout se fait dans le mois passé en paramètre (jamais Date()).
 * - V2 : l'allocation est retrouvée par enveloppe ET mois, fusionnée s'il y en a plusieurs.
 * - V3 : le solde bancaire ne bouge jamais ; seul le « Prêt à placer » du bon compte change.
 * - V4 : tout est validé avant la première écriture ; si la deuxième écriture échoue,
 *        la première est annulée, et l'erreur est remontée (jamais de succès annoncé à tort).
 * - V7 : la provenance (compte d'origine) suit l'argent et les mélanges de comptes sont refusés.
 * - V8 : une enveloppe peut passer sous 0 $ (le déficit reste visible et à renflouer) ;
 *        le « Prêt à placer » d'un compte, lui, ne peut pas devenir négatif.
 */
class VirementBudgetService(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository,
    private val historiqueAllocationService: HistoriqueAllocationService
) {

    fun premierJourDuMois(date: Date): Date = Calendar.getInstance().apply {
        time = date
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    private suspend fun compteCheque(compteId: String): CompteCheque =
        compteRepository.recupererCompteParIdToutesCollections(compteId).getOrNull() as? CompteCheque
            ?: throw IllegalArgumentException(VirementErrorMessages.EnveloppeVersPretAPlacer.COMPTE_DESTINATION_INTROUVABLE)

    private suspend fun enveloppe(enveloppeId: String, messageSiAbsente: String): Enveloppe =
        enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow().find { it.id == enveloppeId }
            ?: throw IllegalArgumentException(messageSiAbsente)

    private suspend fun nomCompte(compteId: String?): String =
        compteId?.let { compteRepository.recupererCompteParIdToutesCollections(it).getOrNull()?.nom } ?: "Inconnu"

    /** Provenance retenue quand l'enveloppe contient de l'argent ; null si elle est vide ou négative. */
    private fun provenanceActive(allocation: AllocationMensuelle): String? =
        if (allocation.solde > 0.01) allocation.compteSourceId else null

    private suspend fun ecrire(allocation: AllocationMensuelle) {
        allocationMensuelleRepository.mettreAJourAllocation(allocation)
    }

    // ===== PRÊT À PLACER → ENVELOPPE =====

    suspend fun pretAPlacerVersEnveloppe(compteId: String, enveloppeId: String, montant: Double, mois: Date): Result<Unit> = runCatching {
        require(montant > 0) { VirementErrorMessages.General.MONTANT_INVALIDE }
        val premierJour = premierJourDuMois(mois)
        val compte = compteCheque(compteId)
        val env = enveloppe(enveloppeId, VirementErrorMessages.PretAPlacerVersEnveloppe.enveloppeIntrouvable(""))

        if (compte.pretAPlacer + 0.005 < montant) {
            throw IllegalStateException(VirementErrorMessages.ClavierBudget.fondsInsuffisants(compte.pretAPlacer))
        }
        val avant = allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeId, premierJour)
        provenanceActive(avant)?.let { provenance ->
            if (provenance != compte.id) {
                throw IllegalArgumentException(
                    VirementErrorMessages.PretAPlacerVersEnveloppe.conflitProvenance(nomCompte(provenance), compte.nom)
                )
            }
        }

        val nouveauPretAPlacer = MoneyFormatter.roundAmount(compte.pretAPlacer - montant)
        compteRepository.mettreAJourCompte(compte.copy(pretAPlacerRaw = nouveauPretAPlacer)).getOrThrow()

        val apres = avant.copy(
            solde = MoneyFormatter.roundAmount(avant.solde + montant),
            alloue = MoneyFormatter.roundAmount(avant.alloue + montant),
            compteSourceId = if (avant.solde <= 0.01) compte.id else avant.compteSourceId,
            collectionCompteSource = if (avant.solde <= 0.01) compte.collection else avant.collectionCompteSource
        )
        try {
            ecrire(apres)
        } catch (e: Exception) {
            // V4 : rendre le « Prêt à placer » retiré
            compteRepository.mettreAJourCompte(compte)
            throw e
        }

        historique(avant, apres, compte, env, montant, compte.pretAPlacer, nouveauPretAPlacer)
    }

    // ===== ENVELOPPE → PRÊT À PLACER =====

    suspend fun enveloppeVersPretAPlacer(enveloppeId: String, compteId: String, montant: Double, mois: Date): Result<Unit> = runCatching {
        require(montant > 0) { VirementErrorMessages.General.MONTANT_INVALIDE }
        val premierJour = premierJourDuMois(mois)
        val compte = compteCheque(compteId)
        val env = enveloppe(enveloppeId, VirementErrorMessages.EnveloppeVersPretAPlacer.enveloppeSourceIntrouvable(""))

        val avant = allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeId, premierJour)
        if (avant.solde <= 0.01) {
            throw IllegalStateException(VirementErrorMessages.EnveloppeVersEnveloppe.ENVELOPPE_SOURCE_VIDE)
        }
        val provenance = avant.compteSourceId
        if (provenance != null && provenance != compte.id) {
            throw IllegalArgumentException(
                VirementErrorMessages.EnveloppeVersPretAPlacer.conflitProvenance(nomCompte(provenance), compte.nom)
            )
        }

        val apres = avant.copy(
            solde = MoneyFormatter.roundAmount(avant.solde - montant),
            alloue = MoneyFormatter.roundAmount(avant.alloue - montant)
        )
        ecrire(apres)

        val nouveauPretAPlacer = MoneyFormatter.roundAmount(compte.pretAPlacer + montant)
        val resultat = compteRepository.mettreAJourCompte(compte.copy(pretAPlacerRaw = nouveauPretAPlacer))
        if (resultat.isFailure) {
            ecrire(avant) // V4 : remettre l'enveloppe comme avant
            throw resultat.exceptionOrNull() ?: IllegalStateException("Mise à jour du prêt à placer impossible")
        }

        historique(avant, apres, compte, env, -montant, compte.pretAPlacer, nouveauPretAPlacer)
    }

    // ===== ENVELOPPE → ENVELOPPE =====

    suspend fun enveloppeVersEnveloppe(sourceId: String, destinationId: String, montant: Double, mois: Date): Result<Unit> = runCatching {
        require(montant > 0) { VirementErrorMessages.General.MONTANT_INVALIDE }
        require(sourceId != destinationId) { VirementErrorMessages.General.SOURCE_DESTINATION_IDENTIQUES }
        val premierJour = premierJourDuMois(mois)
        val envSource = enveloppe(sourceId, VirementErrorMessages.EnveloppeVersEnveloppe.ENVELOPPE_SOURCE_INTROUVABLE)
        val envDest = enveloppe(destinationId, VirementErrorMessages.EnveloppeVersEnveloppe.ENVELOPPE_DESTINATION_INTROUVABLE)

        val sourceAvant = allocationMensuelleRepository.recupererOuCreerAllocation(sourceId, premierJour)
        val destAvant = allocationMensuelleRepository.recupererOuCreerAllocation(destinationId, premierJour)

        // Sans argent dans la source, il n'y a pas de provenance à transmettre
        if (sourceAvant.solde <= 0.01) {
            throw IllegalStateException(VirementErrorMessages.EnveloppeVersEnveloppe.ENVELOPPE_SOURCE_VIDE)
        }
        val provenance = sourceAvant.compteSourceId
        // Une destination vide ou négative est toujours compatible (on peut combler un déficit)
        provenanceActive(destAvant)?.let { provenanceDest ->
            if (provenance != null && provenanceDest != provenance) {
                throw IllegalArgumentException(
                    VirementErrorMessages.EnveloppeVersEnveloppe.conflitProvenance(nomCompte(provenance), nomCompte(provenanceDest))
                )
            }
        }

        val sourceApres = sourceAvant.copy(
            solde = MoneyFormatter.roundAmount(sourceAvant.solde - montant),
            alloue = MoneyFormatter.roundAmount(sourceAvant.alloue - montant)
        )
        val destApres = destAvant.copy(
            solde = MoneyFormatter.roundAmount(destAvant.solde + montant),
            alloue = MoneyFormatter.roundAmount(destAvant.alloue + montant),
            compteSourceId = if (destAvant.solde <= 0.01) provenance else destAvant.compteSourceId,
            collectionCompteSource = if (destAvant.solde <= 0.01) sourceAvant.collectionCompteSource else destAvant.collectionCompteSource
        )

        ecrire(sourceApres)
        try {
            ecrire(destApres)
        } catch (e: Exception) {
            ecrire(sourceAvant) // V4
            throw e
        }

        provenance?.let { id ->
            (compteRepository.recupererCompteParIdToutesCollections(id).getOrNull() as? CompteCheque)?.let { compte ->
                historique(sourceAvant, sourceApres, compte, envSource, -montant, compte.pretAPlacer, compte.pretAPlacer)
                historique(destAvant, destApres, compte, envDest, montant, compte.pretAPlacer, compte.pretAPlacer)
            }
        }
    }

    private suspend fun historique(
        avant: AllocationMensuelle,
        apres: AllocationMensuelle,
        compte: CompteCheque,
        enveloppe: Enveloppe,
        montant: Double,
        pretAPlacerAvant: Double,
        pretAPlacerApres: Double
    ) {
        try {
            historiqueAllocationService.enregistrerModificationAllocation(
                allocationAvant = avant,
                allocationApres = apres,
                compte = compte,
                enveloppe = enveloppe,
                montantModification = montant,
                soldeAvant = compte.solde,
                soldeApres = compte.solde, // V3 : le solde bancaire ne change pas
                pretAPlacerAvant = pretAPlacerAvant,
                pretAPlacerApres = pretAPlacerApres
            )
        } catch (_: Exception) {
            // L'historique ne doit pas faire échouer un virement déjà enregistré
        }
    }
}
