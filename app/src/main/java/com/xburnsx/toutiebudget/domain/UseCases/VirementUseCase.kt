// chemin/simule: /domain/usecases/VirementUseCase.kt
// Dépendances: CompteRepository, AllocationMensuelleRepository, TransactionRepository, EnveloppeRepository

package com.xburnsx.toutiebudget.domain.usecases

import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.*
import com.xburnsx.toutiebudget.domain.services.ValidationProvenanceService
import com.xburnsx.toutiebudget.di.AppModule
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

/**
 * Use case pour effectuer des virements d'argent entre comptes et enveloppes.
 * Gère tous les types de virements incluant les "prêt à placer".
 */
class VirementUseCase @Inject constructor(
    private val compteRepository: CompteRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository,
    private val transactionRepository: TransactionRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val validationProvenanceService: ValidationProvenanceService
) {

    /**
     * Effectue un virement de "prêt à placer" vers une enveloppe.
     * Diminue le pret_a_placer du compte et augmente le solde de l'enveloppe.
     */
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun effectuerVirementPretAPlacerVersEnveloppe(
        compteId: String,
        enveloppeId: String,
        montant: Double
    ): Result<Unit> = runCatching {


        if (montant <= 0) {
            throw IllegalArgumentException("Le montant doit être positif")
        }

        coroutineScope {
            // 1. Récupérer le compte source
            val compte = compteRepository.recupererCompteParId(compteId, "comptes_cheque")
                .getOrNull() ?: throw IllegalArgumentException("Compte non trouvé")

            if (compte !is CompteCheque) {
                throw IllegalArgumentException("Le prêt à placer n'est disponible que pour les comptes chèque")
            }

            // 2. Vérifier que le prêt à placer est suffisant
            if (compte.pretAPlacer < montant) {
                throw IllegalArgumentException("Prêt à placer insuffisant (${compte.pretAPlacer}$ disponible)")
            }

            // 🔒 VALIDATION DE PROVENANCE - Vérifier avant le virement
            val calendrier = Calendar.getInstance().apply {
                time = Date()
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val premierJourMois = calendrier.time

            val validationResult = validationProvenanceService.validerAjoutArgentEnveloppe(
                enveloppeId = enveloppeId,
                compteSourceId = compteId,
                mois = premierJourMois
            )

            if (validationResult.isFailure) {
                throw IllegalArgumentException(validationResult.exceptionOrNull()?.message ?: "Conflit de provenance détecté")
            }

            // 3. ✅ UTILISER recupererOuCreerAllocation au lieu de créer systématiquement
            val allocationExistante = allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeId, premierJourMois)

            // 4. ✅ METTRE À JOUR l'allocation existante
            val allocationMiseAJour = allocationExistante.copy(
                solde = allocationExistante.solde + montant,
                alloue = allocationExistante.alloue + montant,
                compteSourceId = compteId,
                collectionCompteSource = "comptes_cheque"
            )

            allocationMensuelleRepository.mettreAJourAllocation(allocationMiseAJour)

            // 5. Mettre à jour le prêt à placer du compte
            val resultCompte = compteRepository.mettreAJourPretAPlacerSeulement(
                compteId = compteId,
                variationPretAPlacer = -montant
            )
            if (resultCompte.isFailure) {
                throw resultCompte.exceptionOrNull() ?: Exception("Erreur mise à jour compte")
            }


            // 6. Créer une transaction de traçabilité
            val transaction = Transaction(
                type = TypeTransaction.Depense,
                montant = montant,
                date = Date(), // Utilise l'heure locale actuelle du téléphone
                note = "Virement depuis Prêt à placer vers enveloppe",
                compteId = compteId,
                collectionCompte = "comptes_cheque",
                allocationMensuelleId = allocationMiseAJour.id
            )

            val resultTransaction = transactionRepository.creerTransaction(transaction)
            if (resultTransaction.isFailure) {
                throw resultTransaction.exceptionOrNull() ?: Exception("Erreur création transaction")
            }

            // 🚀 DÉCLENCHER MANUELLEMENT L'ÉVÉNEMENT TEMPS RÉEL
            try {
                val realtimeService = AppModule.provideRealtimeSyncService()
                // Forcer la mise à jour du budget après virement
                kotlinx.coroutines.GlobalScope.launch {
                    realtimeService.triggerBudgetUpdate()
                }
            } catch (_: Exception) {
                // Erreur silencieuse
            }

            // 🔥 FUSION AUTOMATIQUE : Forcer la fusion des allocations après le virement
            try {
                allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeId, premierJourMois)
            } catch (e: Exception) {
                // Erreur silencieuse de fusion - ne pas faire échouer le virement
                println("⚠️ Erreur lors de la fusion des allocations après virement prêt->enveloppe: ${e.message}")
            }

        }
    }

    /**
     * Effectue un virement d'une enveloppe vers "prêt à placer".
     * Diminue le solde de l'enveloppe et augmente le pret_a_placer du compte.
     */
    suspend fun effectuerVirementEnveloppeVersPretAPlacer(
        enveloppeId: String,
        compteId: String,
        montant: Double
    ): Result<Unit> = runCatching {


        if (montant <= 0) {
            throw IllegalArgumentException("Le montant doit être positif")
        }

        coroutineScope {
            // 1. Récupérer le compte destination - utiliser la recherche dans toutes les collections
            val compteResult = compteRepository.recupererCompteParIdToutesCollections(compteId)

            val compte = compteResult.getOrNull() ?: run {
                throw IllegalArgumentException("Compte non trouvé")
            }

            if (compte !is CompteCheque) {
                throw IllegalArgumentException("Le prêt à placer n'est disponible que pour les comptes chèque")
            }

            // 2. Obtenir l'allocation mensuelle de l'enveloppe
            val calendrier = Calendar.getInstance().apply {
                time = Date()
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val premierJourMois = calendrier.time

            // 3. ✅ RÉCUPÉRER l'allocation fusionnée AVANT de vérifier le solde !
            val allocationExistante = allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeId, premierJourMois)

            // 4. ✅ Vérifier le solde de l'allocation fusionnée (plus fiable !)
            if (allocationExistante.solde < montant) {
                throw IllegalArgumentException("Solde d'enveloppe insuffisant (${allocationExistante.solde}$ disponible, ${montant}$ demandé)")
            }

            // 5. ✅ MODIFIER l'allocation existante (déjà récupérée plus haut !)
            
            // ✅ Mettre à jour l'allocation existante (addition automatique)
            val allocationMiseAJour = allocationExistante.copy(
                solde = allocationExistante.solde - montant, // Retirer de l'enveloppe
                alloue = allocationExistante.alloue - montant, // Alloué négatif pour virement sortant
                // ✅ PROVENANCE : Changer seulement si solde était à 0
                compteSourceId = if (allocationExistante.solde <= 0.01) compteId else allocationExistante.compteSourceId,
                collectionCompteSource = if (allocationExistante.solde <= 0.01) compte.collection else allocationExistante.collectionCompteSource
            )
            
            // ✅ Mettre à jour l'allocation existante
            allocationMensuelleRepository.mettreAJourAllocation(allocationMiseAJour)


            // 🔒 VALIDATION DE PROVENANCE - Vérifier que l'argent retourne vers son compte d'origine
            val validationResult = validationProvenanceService.validerRetourVersCompte(
                enveloppeId = enveloppeId,
                compteDestinationId = compteId,
                mois = premierJourMois
            )

            if (validationResult.isFailure) {
                throw IllegalArgumentException(validationResult.exceptionOrNull()?.message ?: "L'argent ne peut retourner que vers son compte d'origine")
            }

            // 6. Mettre à jour le prêt à placer du compte (augmenter)
            compteRepository.mettreAJourPretAPlacerSeulement(
                compteId = compteId,
                variationPretAPlacer = montant
            )


            // 7. ✅ PAS DE TRANSACTION - C'est un virement interne !
            // L'argent reste dans les fonds de l'utilisateur, pas besoin de transaction

            // 🔥 FUSION AUTOMATIQUE : Forcer la fusion des allocations après le virement
            try {
                allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeId, premierJourMois)
            } catch (e: Exception) {
                // Erreur silencieuse de fusion - ne pas faire échouer le virement
                println("⚠️ Erreur lors de la fusion des allocations après virement enveloppe->prêt: ${e.message}")
            }

        }
    }

}