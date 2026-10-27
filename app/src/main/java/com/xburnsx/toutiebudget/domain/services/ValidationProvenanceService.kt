package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.ui.virement.VirementErrorMessages
import java.util.*
import javax.inject.Inject

/**
 * Service de validation pour empêcher le mélange d'argent de différentes provenances
 */
class ValidationProvenanceService @Inject constructor(
    private val enveloppeRepository: EnveloppeRepository,
    private val compteRepository: CompteRepository
) {

    /**
     * Récupère le nom d'un compte par son ID
     */
    private suspend fun obtenirNomCompte(compteId: String): String {
        return try {
            // Utiliser la méthode générique qui cherche dans toutes les collections
            compteRepository.recupererTousLesComptes()
                .getOrNull()
                ?.find { it.id == compteId }
                ?.nom ?: "Compte inconnu"
        } catch (_: Exception) {
            "Compte inconnu"
        }
    }

    /**
     * Vérifie si on peut ajouter de l'argent d'un compte source vers une enveloppe
     * @param enveloppeId ID de l'enveloppe cible
     * @param compteSourceId ID du compte d'où vient l'argent
     * @param mois Mois concerné
     * @return Result avec message d'erreur si conflit détecté
     */
    suspend fun validerAjoutArgentEnveloppe(
        enveloppeId: String,
        compteSourceId: String,
        mois: Date
    ): Result<Unit> = runCatching {
        // A2 : une erreur de Room n'est JAMAIS une enveloppe vide.
        val allocations = enveloppeRepository.recupererAllocationsPourMois(mois).getOrThrow()
            .filter { it.enveloppeId == enveloppeId }
        val financementsActifs = allocations.filter {
            if (!it.solde.isFinite()) throw IllegalStateException("Solde d'allocation invalide")
            kotlin.math.round(it.solde * 100.0).toLong() > 0L
        }
        if (financementsActifs.isNotEmpty()) {
            // Une provenance ne peut pas être déduite du seul « plus gros solde » :
            // ce serait attribuer silencieusement à un compte l'argent d'un autre.
            val comptes = financementsActifs.map { it.compteSourceId }.toSet()
            if (comptes.size != 1 || comptes.singleOrNull().isNullOrBlank()) {
                throw IllegalStateException(
                    "Provenance de l'enveloppe inconnue ou mélangée : impossible d'assigner."
                )
            }
            val provenance = comptes.single()!!
            if (provenance != compteSourceId) {
                val nomCompteExistant = obtenirNomCompte(provenance)
                val nomCompteTente = obtenirNomCompte(compteSourceId)
                throw IllegalArgumentException(
                    VirementErrorMessages.PretAPlacerVersEnveloppe.conflitProvenance(
                        nomCompteExistant, nomCompteTente
                    )
                )
            }
        }
    }

    /**
     * Vérifie si on peut transférer de l'argent d'une enveloppe vers une autre
     * @param enveloppeSourceId ID de l'enveloppe source
     * @param enveloppeCibleId ID de l'enveloppe cible
     * @param mois Mois concerné
     * @return Result avec message d'erreur si conflit détecté
     */
    suspend fun validerTransfertEntreEnveloppes(
        enveloppeSourceId: String,
        enveloppeCibleId: String,
        mois: Date
    ): Result<Unit> = runCatching {

        // 🔥 CORRECTION: Utiliser le mois passé en paramètre au lieu de Date()
        // 🔥 DEBUG VALIDATION: Date reçue: $mois
        // 🔥 DEBUG VALIDATION: Date utilisée (corrigée): $mois

        // 🔥 CORRECTION: Utiliser le mois passé en paramètre
        val toutesAllocations = enveloppeRepository.recupererAllocationsPourMois(mois)
            .getOrNull() ?: emptyList()
        
        val allocationSource = toutesAllocations.find { it.enveloppeId == enveloppeSourceId }
        val allocationCible = toutesAllocations.find { it.enveloppeId == enveloppeCibleId }

        // 🔥 DEBUG VALIDATION: Toutes allocations trouvées: ${toutesAllocations.size}
        // 🔥 DEBUG VALIDATION: Allocation source trouvée: $allocationSource
        // 🔥 DEBUG VALIDATION: Solde source: ${allocationSource?.solde}

        if (allocationSource == null || allocationSource.solde <= 0) {
            // 🔥 DEBUG VALIDATION: ÉCHEC - Source null: ${allocationSource == null}, Solde <= 0: ${allocationSource?.solde ?: 0}
            throw IllegalArgumentException(VirementErrorMessages.EnveloppeVersEnveloppe.ENVELOPPE_SOURCE_VIDE)
        }

        val compteProvenanceSource = allocationSource.compteSourceId

        if (allocationCible != null && allocationCible.solde > 0.01) { // ✅ Même correction ici
            // La cible contient déjà de l'argent
            val compteProvenanceCible = allocationCible.compteSourceId

            if (compteProvenanceCible != null && compteProvenanceCible != compteProvenanceSource) {
                // Récupérer les noms des comptes pour un message plus clair
                val nomCompteSource = obtenirNomCompte(compteProvenanceSource ?: "")
                val nomCompteCible = obtenirNomCompte(compteProvenanceCible)

                throw IllegalArgumentException(
                    VirementErrorMessages.EnveloppeVersEnveloppe.conflitProvenance(
                        nomCompteSource,
                        nomCompteCible
                    )
                )
            }
        }
    }

    /**
     * Vérifie si on peut transférer de l'argent d'une enveloppe vers un compte.
     * Le transfert est autorisé uniquement si le compte de destination est le même
     * que le compte d'origine de l'argent dans l'enveloppe.
     * @param enveloppeSourceId ID de l'enveloppe source
     * @param compteCibleId ID du compte cible
     * @param mois Mois concerné
     * @return Result avec message d'erreur si conflit détecté
     */
    suspend fun validerTransfertEnveloppeVersCompte(
        enveloppeSourceId: String,
        compteCibleId: String,
        mois: Date
    ): Result<Unit> = runCatching {

        // Récupérer l'allocation de l'enveloppe source
        val allocationSource = enveloppeRepository.recupererAllocationMensuelle(enveloppeSourceId, mois)
            .getOrNull()

        if (allocationSource == null || allocationSource.solde <= 0) {
            // Pas besoin de lancer une exception ici, car le solde est vérifié ailleurs.
            // On peut simplement retourner succès si l'enveloppe est vide.
            return@runCatching
        }

        val compteProvenanceSource = allocationSource.compteSourceId

        // Si l'enveloppe a une provenance, elle doit correspondre au compte cible
        if (compteProvenanceSource != null && compteProvenanceSource != compteCibleId) {
            val nomCompteSource = obtenirNomCompte(compteProvenanceSource)
            val nomCompteCible = obtenirNomCompte(compteCibleId)

            throw IllegalArgumentException(
                VirementErrorMessages.EnveloppeVersPretAPlacer.conflitProvenance(
                    nomCompteSource,
                    nomCompteCible
                )
            )
        }
    }

    /**
     * Vérifie si on peut retourner de l'argent d'une enveloppe vers un compte
     * @param enveloppeId ID de l'enveloppe
     * @param compteDestinationId ID du compte de destination
     * @param mois Mois concerné
     * @return Result avec message d'erreur si conflit détecté
     */
    suspend fun validerRetourVersCompte(
        enveloppeId: String,
        compteDestinationId: String,
        mois: Date
    ): Result<Unit> = runCatching {

        // Récupérer l'allocation de l'enveloppe
        val allocation = enveloppeRepository.recupererAllocationMensuelle(enveloppeId, mois)
            .getOrNull()

        if (allocation == null || allocation.solde <= 0) {
            throw IllegalArgumentException(VirementErrorMessages.EnveloppeVersEnveloppe.ENVELOPPE_SOURCE_VIDE)
        }

        val compteProvenanceOriginal = allocation.compteSourceId

        if (compteProvenanceOriginal != null && compteProvenanceOriginal != compteDestinationId) {
            // Récupérer les noms des comptes pour un message plus clair
            val nomCompteOriginal = obtenirNomCompte(compteProvenanceOriginal)
            val nomCompteDestination = obtenirNomCompte(compteDestinationId)

            throw IllegalArgumentException(
                VirementErrorMessages.EnveloppeVersPretAPlacer.conflitProvenance(
                    nomCompteOriginal,
                    nomCompteDestination
                )
            )
        }
    }

}
