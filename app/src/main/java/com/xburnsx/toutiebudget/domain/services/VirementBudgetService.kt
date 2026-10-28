package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.ui.virement.VirementErrorMessages
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import com.xburnsx.toutiebudget.utils.OperationAtomique
import com.xburnsx.toutiebudget.utils.OperationAtomiqueDirecte
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
    private val historiqueAllocationService: HistoriqueAllocationService,
    private val operationAtomique: OperationAtomique = OperationAtomiqueDirecte
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
        compteRepository.recupererCompteParIdToutesCollections(compteId).getOrThrow() as? CompteCheque
            ?: throw IllegalArgumentException(VirementErrorMessages.EnveloppeVersPretAPlacer.COMPTE_DESTINATION_INTROUVABLE)

    private suspend fun enveloppe(enveloppeId: String, messageSiAbsente: String): Enveloppe =
        enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow().find { it.id == enveloppeId && !it.estArchive }
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

    suspend fun pretAPlacerVersEnveloppe(compteId: String, enveloppeId: String, montant: Double, mois: Date): Result<Unit> = runCatching { operationAtomique.executer {
        require(montant.isFinite() && montant > 0) { VirementErrorMessages.General.MONTANT_INVALIDE }
        val premierJour = premierJourDuMois(mois)
        val compte = compteCheque(compteId)
        val env = enveloppe(enveloppeId, VirementErrorMessages.PretAPlacerVersEnveloppe.enveloppeIntrouvable(""))
        if (compte.estArchive) throw IllegalStateException("Ce compte est archivé.")
        if (!compte.pretAPlacer.isFinite() || !montant.isFinite() ||
            kotlin.math.round(compte.pretAPlacer * 100.0).toLong() < kotlin.math.round(montant * 100.0).toLong()
        ) {
            throw IllegalStateException(VirementErrorMessages.ClavierBudget.fondsInsuffisants(compte.pretAPlacer))
        }
        val allocationExistante = allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeId, premierJour)
        provenanceActive(allocationExistante)?.let { provenance ->
            if (provenance != compte.id) {
                throw IllegalArgumentException(
                    VirementErrorMessages.PretAPlacerVersEnveloppe.conflitProvenance(nomCompte(provenance), compte.nom)
                )
            }
        }

        // V7 : ne pas attribuer l'ancien historique à un autre compte si le
        // solde de l'allocation précédente était devenu nul/négatif.
        val avaitHistorique = allocationExistante.compteSourceId != null ||
            kotlin.math.round(allocationExistante.alloue * 100.0).toLong() != 0L ||
            kotlin.math.round(allocationExistante.depense * 100.0).toLong() != 0L
        val changerProvenance = avaitHistorique && allocationExistante.compteSourceId != compte.id &&
            kotlin.math.round(allocationExistante.solde * 100.0).toLong() <= 0L
        val avant = if (changerProvenance) {
            allocationMensuelleRepository.creerNouvelleAllocation(allocationExistante.copy(
                id = "", solde = 0.0, alloue = 0.0, depense = 0.0, montantReporte = 0.0,
                compteSourceId = compte.id, collectionCompteSource = compte.collection
            ))
        } else allocationExistante
        val nouveauPretAPlacer = MoneyFormatter.roundAmount(compte.pretAPlacer - montant)
        compteRepository.mettreAJourCompte(compte.copy(pretAPlacerRaw = nouveauPretAPlacer)).getOrThrow()

        val apres = avant.copy(
            solde = MoneyFormatter.roundAmount(avant.solde + montant),
            alloue = MoneyFormatter.roundAmount(avant.alloue + montant),
            compteSourceId = if (avant.solde <= 0.01) compte.id else avant.compteSourceId,
            collectionCompteSource = if (avant.solde <= 0.01) compte.collection else avant.collectionCompteSource
        )
        ecrire(apres)

        historique(avant, apres, compte, env, montant, compte.pretAPlacer, nouveauPretAPlacer)
    }
    }

    // ===== ENVELOPPE → PRÊT À PLACER =====

    suspend fun enveloppeVersPretAPlacer(enveloppeId: String, compteId: String, montant: Double, mois: Date): Result<Unit> = runCatching { operationAtomique.executer {
        require(montant.isFinite() && montant > 0) { VirementErrorMessages.General.MONTANT_INVALIDE }
        val premierJour = premierJourDuMois(mois)
        val compte = compteCheque(compteId)
        if (compte.estArchive) throw IllegalStateException("Le compte de destination est archivé.")
        val env = enveloppe(enveloppeId, VirementErrorMessages.EnveloppeVersPretAPlacer.enveloppeSourceIntrouvable(""))

        val avant = allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeId, premierJour)
        if (avant.solde <= 0.01) {
            throw IllegalStateException(VirementErrorMessages.EnveloppeVersEnveloppe.ENVELOPPE_SOURCE_VIDE)
        }
        val provenance = avant.compteSourceId
        if (provenance.isNullOrBlank()) {
            throw IllegalStateException("Le compte d'origine de l'enveloppe est inconnu; impossible de retourner les fonds.")
        }
        if (provenance != compte.id) {
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
        compteRepository.mettreAJourCompte(compte.copy(pretAPlacerRaw = nouveauPretAPlacer)).getOrThrow()

        historique(avant, apres, compte, env, -montant, compte.pretAPlacer, nouveauPretAPlacer)
    }
    }

    // ===== ENVELOPPE → ENVELOPPE =====

    suspend fun enveloppeVersEnveloppe(sourceId: String, destinationId: String, montant: Double, mois: Date): Result<Unit> = runCatching { operationAtomique.executer {
        require(montant.isFinite() && montant > 0) { VirementErrorMessages.General.MONTANT_INVALIDE }
        require(sourceId != destinationId) { VirementErrorMessages.General.SOURCE_DESTINATION_IDENTIQUES }
        val premierJour = premierJourDuMois(mois)
        val envSource = enveloppe(sourceId, VirementErrorMessages.EnveloppeVersEnveloppe.ENVELOPPE_SOURCE_INTROUVABLE)
        val envDest = enveloppe(destinationId, VirementErrorMessages.EnveloppeVersEnveloppe.ENVELOPPE_DESTINATION_INTROUVABLE)

        val sourceAvant = allocationMensuelleRepository.recupererOuCreerAllocation(sourceId, premierJour)
        val destExistante = allocationMensuelleRepository.recupererOuCreerAllocation(destinationId, premierJour)

        // Sans argent dans la source, il n'y a pas de provenance à transmettre
        // V8 : le déficit d'une enveloppe est autorisé volontairement.
        // Une source sans aucune provenance ne peut toutefois créer des fonds.
        val provenance = sourceAvant.compteSourceId
        if (provenance.isNullOrBlank()) {
            throw IllegalStateException("Impossible de virer depuis cette enveloppe : compte d'origine inconnu.")
        }
        // Une destination vide ou négative est toujours compatible (on peut combler un déficit)
        provenanceActive(destExistante)?.let { provenanceDest ->
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
        val destHistorique = destExistante.compteSourceId != null ||
            kotlin.math.round(destExistante.alloue * 100.0).toLong() != 0L ||
            kotlin.math.round(destExistante.depense * 100.0).toLong() != 0L
        val nouvelleLigneDestination = destHistorique && destExistante.compteSourceId != provenance &&
            kotlin.math.round(destExistante.solde * 100.0).toLong() <= 0L
        val destAvant = if (nouvelleLigneDestination) {
            allocationMensuelleRepository.creerNouvelleAllocation(destExistante.copy(
                id = "", solde = 0.0, alloue = 0.0, depense = 0.0, montantReporte = 0.0,
                compteSourceId = provenance,
                collectionCompteSource = sourceAvant.collectionCompteSource
            ))
        } else destExistante
        val destApres = destAvant.copy(
            solde = MoneyFormatter.roundAmount(destAvant.solde + montant),
            alloue = MoneyFormatter.roundAmount(destAvant.alloue + montant),
            compteSourceId = if (destAvant.solde <= 0.01) provenance else destAvant.compteSourceId,
            collectionCompteSource = if (destAvant.solde <= 0.01) sourceAvant.collectionCompteSource else destAvant.collectionCompteSource
        )

        ecrire(sourceApres)
        ecrire(destApres)

        // V7 : un mouvement de fonds connus doit garder une provenance valide.
        val compte = provenance?.let { compteCheque(it) }
            ?: throw IllegalStateException("Source sans provenance bancaire valide")
        historique(sourceAvant, sourceApres, compte, envSource, -montant, compte.pretAPlacer, compte.pretAPlacer)
        historique(destAvant, destApres, compte, envDest, montant, compte.pretAPlacer, compte.pretAPlacer)
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
    }
}
