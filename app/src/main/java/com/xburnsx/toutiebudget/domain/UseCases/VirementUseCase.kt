// chemin/simule: /domain/usecases/VirementUseCase.kt
// Dépendances: CompteRepository, AllocationMensuelleRepository, TransactionRepository, EnveloppeRepository

package com.xburnsx.toutiebudget.domain.usecases

import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.*
import kotlinx.coroutines.coroutineScope
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
    private val enveloppeRepository: EnveloppeRepository
) {

    /**
     * Effectue un virement de "prêt à placer" vers une enveloppe.
     * Diminue le pret_a_placer du compte et augmente le solde de l'enveloppe.
     */
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

            // 3. Obtenir ou créer l'allocation mensuelle
            val calendrier = Calendar.getInstance().apply {
                time = Date()
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val premierJourMois = calendrier.time

            // 3. Mettre à jour le prêt à placer du compte
            val resultCompte = compteRepository.mettreAJourPretAPlacerSeulement(
                compteId = compteId,
                variationPretAPlacer = -montant
            )
            if (resultCompte.isFailure) {
                throw resultCompte.exceptionOrNull() ?: Exception("Erreur mise à jour compte")
            }


            // 4. Récupérer ou créer l'allocation mensuelle (EXACTEMENT comme allouerArgentEnveloppe)

            try {
                println("[DEBUG] 🚀 CRÉATION d'une NOUVELLE allocation de +$montant pour enveloppe $enveloppeId")

                // Créer une NOUVELLE allocation qui va s'additionner automatiquement
                val nouvelleAllocation = AllocationMensuelle(
                    id = "", // PocketBase va générer un nouvel ID
                    utilisateurId = compte.utilisateurId,
                    enveloppeId = enveloppeId,
                    mois = premierJourMois,
                    solde = montant, // +5$ qui va s'additionner aux -30$ existants
                    alloue = montant,
                    depense = 0.0,
                    compteSourceId = compteId,
                    collectionCompteSource = "comptes_cheque"
                )

                println("[DEBUG] 📝 Appel creerNouvelleAllocation() pour créer dans PocketBase...")
                val allocationCreee = allocationMensuelleRepository.creerNouvelleAllocation(nouvelleAllocation)
                println("[DEBUG] ✅ SUCCÈS! Nouvelle allocation créée avec ID: ${allocationCreee.id}")

                // 5. Créer une transaction de traçabilité
                println("[DEBUG] 📋 Création transaction de traçabilité...")
                val transaction = Transaction(
                    type = TypeTransaction.Depense,
                    montant = montant,
                    date = Date(),
                    note = "Virement depuis Prêt à placer vers enveloppe",
                    compteId = compteId,
                    collectionCompte = "comptes_cheque",
                    allocationMensuelleId = allocationCreee.id
                )

                val resultTransaction = transactionRepository.creerTransaction(transaction)
                if (resultTransaction.isFailure) {
                    throw resultTransaction.exceptionOrNull() ?: Exception("Erreur création transaction")
                }

            } catch (e: Exception) {
                println("[DEBUG] ❌ ERREUR CRITIQUE dans VirementUseCase: ${e.message}")
                println("[DEBUG] 🔍 Stack trace: ${e.stackTraceToString()}")
                throw e
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
            // 1. Récupérer le compte destination
            val compte = compteRepository.recupererCompteParId(compteId, "comptes_cheque")
                .getOrNull() ?: throw IllegalArgumentException("Compte non trouvé")

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

            val allocation = allocationMensuelleRepository.getOrCreateAllocationMensuelle(
                enveloppeId = enveloppeId,
                mois = premierJourMois
            )

            // 3. Vérifier que l'enveloppe a suffisamment d'argent
            if (allocation.solde < montant) {
                throw IllegalArgumentException("Solde d'enveloppe insuffisant (${allocation.solde}$ disponible)")
            }

            // 4. Mettre à jour l'allocation mensuelle (diminuer)
            val nouvelleAllocation = allocation.copy(
                solde = allocation.solde - montant,
                depense = allocation.depense + montant
            )

            allocationMensuelleRepository.mettreAJourAllocation(nouvelleAllocation)


            // 5. Mettre à jour le prêt à placer du compte (augmenter)
            compteRepository.mettreAJourPretAPlacerSeulement(
                compteId = compteId,
                variationPretAPlacer = montant
            )


            // 6. Créer une transaction de traçabilité
            val transaction = Transaction(
                type = TypeTransaction.Revenu,
                montant = montant,
                date = Date(),
                note = "Virement depuis enveloppe vers Prêt à placer",
                compteId = compteId,
                collectionCompte = "comptes_cheque",
                allocationMensuelleId = allocation.id
            )

            val resultTransaction = transactionRepository.creerTransaction(transaction)
            if (resultTransaction.isFailure) {
                throw resultTransaction.exceptionOrNull() ?: Exception("Erreur création transaction")
            }

        }
    }

    /**
     * Vérifie si une chaîne de caractères représente un ID de "prêt à placer".
     */
    private fun estPretAPlacer(id: String): Boolean {
        return id.startsWith("pret_a_placer_")
    }

    /**
     * Extrait l'ID du compte depuis un ID de "prêt à placer".
     * Format attendu: "pret_a_placer_[COMPTE_ID]"
     */
    private fun extraireCompteIdDepuisPretAPlacer(pretAPlacerId: String): String {
        return pretAPlacerId.removePrefix("pret_a_placer_")
    }
}