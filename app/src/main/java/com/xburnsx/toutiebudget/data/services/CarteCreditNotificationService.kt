package com.xburnsx.toutiebudget.data.services

import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import java.util.*

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