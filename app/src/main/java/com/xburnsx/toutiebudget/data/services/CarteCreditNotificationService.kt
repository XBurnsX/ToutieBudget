package com.xburnsx.toutiebudget.data.services

import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.repositories.CarteCreditRepository
import java.util.*

/**
 * Service pour gérer les notifications et alertes liées aux cartes de crédit.
 */
class CarteCreditNotificationService(
    private val carteCreditRepository: CarteCreditRepository
) {

    /**
     * Vérifie toutes les cartes de crédit pour les alertes.
     * @return Liste des alertes à afficher
     */
    suspend fun verifierAlertes(): List<AlerteCarteCredit> {
        val alertes = mutableListOf<AlerteCarteCredit>()
        
        try {
            val cartes = carteCreditRepository.recupererCartesCredit().getOrThrow()
            
            cartes.forEach { carte ->
                // Vérifier l'échéance de paiement
                val prochaineEcheance = carteCreditRepository.calculerProchaineEcheance(carte)
                val maintenant = Date()
                val joursAvantEcheance = ((prochaineEcheance.time - maintenant.time) / (1000 * 60 * 60 * 24)).toInt()
                
                if (joursAvantEcheance <= 7 && joursAvantEcheance > 0) {
                    alertes.add(
                        AlerteCarteCredit(
                            type = TypeAlerte.ECHEANCE_PROCHE,
                            carte = carte,
                            message = "Échéance de paiement dans $joursAvantEcheance jour(s)",
                            priorite = if (joursAvantEcheance <= 3) Priorite.HAUTE else Priorite.MOYENNE
                        )
                    )
                }
                
                // Vérifier si en retard
                if (carteCreditRepository.estEnRetard(carte)) {
                    alertes.add(
                        AlerteCarteCredit(
                            type = TypeAlerte.PAIEMENT_EN_RETARD,
                            carte = carte,
                            message = "Paiement en retard !",
                            priorite = Priorite.CRITIQUE
                        )
                    )
                }
                
                // Vérifier l'utilisation élevée
                val tauxUtilisation = carteCreditRepository.calculerTauxUtilisation(carte)
                if (tauxUtilisation > 0.8) {
                    alertes.add(
                        AlerteCarteCredit(
                            type = TypeAlerte.UTILISATION_ELEVEE,
                            carte = carte,
                            message = "Utilisation élevée: ${(tauxUtilisation * 100).toInt()}%",
                            priorite = Priorite.MOYENNE
                        )
                    )
                }
                
                // Vérifier si proche de la limite
                if (tauxUtilisation > 0.9) {
                    alertes.add(
                        AlerteCarteCredit(
                            type = TypeAlerte.LIMITE_ATTEINTE,
                            carte = carte,
                            message = "Limite presque atteinte: ${(tauxUtilisation * 100).toInt()}%",
                            priorite = Priorite.HAUTE
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Gérer l'erreur silencieusement
        }
        
        return alertes.sortedBy { it.priorite.ordre }
    }

    /**
     * Applique les intérêts mensuels sur toutes les cartes si nécessaire.
     */
    suspend fun appliquerInteretsMensuelsSiNecessaire() {
        try {
            val cartes = carteCreditRepository.recupererCartesCredit().getOrThrow()
            val maintenant = Calendar.getInstance()
            
            cartes.forEach { carte ->
                // TODO: Implémenter la logique d'application des intérêts quand nécessaire
                // Pour l'instant, on applique les intérêts sur toutes les cartes
                if (carte.tauxInteret != null && carte.tauxInteret!! > 0) {
                    carteCreditRepository.appliquerInteretsMensuels(carte.id)
                }
            }
        } catch (e: Exception) {
            // Gérer l'erreur silencieusement
        }
    }
}

/**
 * Types d'alertes pour les cartes de crédit.
 */
enum class TypeAlerte {
    ECHEANCE_PROCHE,
    PAIEMENT_EN_RETARD,
    UTILISATION_ELEVEE,
    LIMITE_ATTEINTE,
    INTERETS_APPLIQUES
}

/**
 * Niveaux de priorité pour les alertes.
 */
enum class Priorite(val ordre: Int) {
    CRITIQUE(0),
    HAUTE(1),
    MOYENNE(2),
    BASSE(3)
}

/**
 * Représente une alerte pour une carte de crédit.
 */
data class AlerteCarteCredit(
    val type: TypeAlerte,
    val carte: CompteCredit,
    val message: String,
    val priorite: Priorite,
    val dateCreation: Date = Date()
) 