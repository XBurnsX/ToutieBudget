package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import java.util.*
import javax.inject.Inject

/**
 * Service de validation pour empêcher le mélange d'argent de différentes provenances
 */
class ValidationProvenanceService @Inject constructor(
    private val allocationMensuelleRepository: AllocationMensuelleRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val compteRepository: CompteRepository
) {

    /**
     * Récupère le nom d'un compte par son ID
     */
    private suspend fun obtenirNomCompte(compteId: String): String {
        return try {
            // Essayer d'abord dans les comptes chèque
            val compte = compteRepository.recupererCompteParId(compteId, "comptes_cheque").getOrNull()
                ?: compteRepository.recupererCompteParId(compteId, "comptes_epargne").getOrNull()
                ?: compteRepository.recupererCompteParId(compteId, "comptes_credit").getOrNull()

            compte?.nom ?: "Compte inconnu"
        } catch (e: Exception) {
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

        // Récupérer l'allocation existante pour ce mois
        val allocationExistante = enveloppeRepository.recupererAllocationMensuelle(enveloppeId, mois)
            .getOrNull()

        if (allocationExistante != null && allocationExistante.solde > 0) {
            // Il y a déjà de l'argent dans cette enveloppe
            val compteProvenanceExistant = allocationExistante.compteSourceId

            if (compteProvenanceExistant != null && compteProvenanceExistant != compteSourceId) {
                // Récupérer les noms des comptes pour un message plus clair
                val nomCompteExistant = obtenirNomCompte(compteProvenanceExistant)
                val nomCompteTente = obtenirNomCompte(compteSourceId)

                throw IllegalArgumentException(
                    "❌ CONFLIT DE PROVENANCE !\n\n" +
                    "Cette enveloppe contient déjà de l'argent provenant d'un autre compte.\n\n" +
                    "• Provenance actuelle : $nomCompteExistant\n" +
                    "• Provenance tentée : $nomCompteTente\n\n" +
                    "Vous ne pouvez pas mélanger l'argent de différents comptes dans une même enveloppe."
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

        // Récupérer les allocations des deux enveloppes
        val allocationSource = enveloppeRepository.recupererAllocationMensuelle(enveloppeSourceId, mois)
            .getOrNull()
        val allocationCible = enveloppeRepository.recupererAllocationMensuelle(enveloppeCibleId, mois)
            .getOrNull()

        if (allocationSource == null || allocationSource.solde <= 0) {
            throw IllegalArgumentException("L'enveloppe source ne contient pas d'argent à transférer")
        }

        val compteProvenanceSource = allocationSource.compteSourceId

        if (allocationCible != null && allocationCible.solde > 0) {
            // La cible contient déjà de l'argent
            val compteProvenanceCible = allocationCible.compteSourceId

            if (compteProvenanceCible != null && compteProvenanceCible != compteProvenanceSource) {
                // Récupérer les noms des comptes pour un message plus clair
                val nomCompteSource = if (compteProvenanceSource != null) obtenirNomCompte(compteProvenanceSource) else "Compte inconnu"
                val nomCompteCible = obtenirNomCompte(compteProvenanceCible)

                throw IllegalArgumentException(
                    "❌ CONFLIT DE PROVENANCE !\n\n" +
                    "Impossible de transférer cet argent.\n\n" +
                    "• L'enveloppe source contient de l'argent de : $nomCompteSource\n" +
                    "• L'enveloppe cible contient déjà de l'argent de : $nomCompteCible\n\n" +
                    "Vous ne pouvez pas mélanger l'argent de différents comptes."
                )
            }
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
            throw IllegalArgumentException("L'enveloppe ne contient pas d'argent à retourner")
        }

        val compteProvenanceOriginal = allocation.compteSourceId

        if (compteProvenanceOriginal != null && compteProvenanceOriginal != compteDestinationId) {
            // Récupérer les noms des comptes pour un message plus clair
            val nomCompteOriginal = obtenirNomCompte(compteProvenanceOriginal)
            val nomCompteDestination = obtenirNomCompte(compteDestinationId)

            throw IllegalArgumentException(
                "❌ CONFLIT DE PROVENANCE !\n\n" +
                "Cet argent ne peut pas retourner vers ce compte.\n\n" +
                "• Provenance originale : $nomCompteOriginal\n" +
                "• Destination tentée : $nomCompteDestination\n\n" +
                "L'argent ne peut retourner que vers son compte d'origine."
            )
        }
    }

    /**
     * Récupère le compte de provenance d'une enveloppe pour un mois donné
     * @param enveloppeId ID de l'enveloppe
     * @param mois Mois concerné
     * @return ID du compte de provenance ou null si pas d'argent
     */
    suspend fun obtenirCompteProvenance(enveloppeId: String, mois: Date): String? {
        return enveloppeRepository.recupererAllocationMensuelle(enveloppeId, mois)
            .getOrNull()
            ?.takeIf { it.solde > 0 }
            ?.compteSourceId
    }
}
