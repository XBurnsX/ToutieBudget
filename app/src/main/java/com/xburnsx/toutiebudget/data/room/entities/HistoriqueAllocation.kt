package com.xburnsx.toutiebudget.data.room.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité pour l'historique des modifications d'allocations mensuelles.
 * Cette entité permet de suivre toutes les modifications apportées aux allocations
 * pour afficher l'historique complet dans la page Historique.
 */
@Entity(tableName = "historique_allocation")
data class HistoriqueAllocation(
    @PrimaryKey
    val id: String,
    
    // Informations de l'utilisateur et du compte
    val utilisateurId: String,
    val compteId: String,
    val collectionCompte: String,
    
    // Informations de l'enveloppe
    val enveloppeId: String,
    val enveloppeNom: String,
    
    // Type d'action effectuée
    val typeAction: String, // "CREATION", "MODIFICATION", "SUPPRESSION"
    
    // Description de l'action
    val description: String,
    
    // Montant de la modification
    val montant: Double,
    
    // États avant et après la modification
    val soldeAvant: Double,
    val soldeApres: Double,
    val pretAPlacerAvant: Double,
    val pretAPlacerApres: Double,
    
    // Date et heure de l'action
    val dateAction: String, // Format: "yyyy-MM-dd HH:mm:ss"
    
    // Références aux entités liées
    val allocationId: String,
    val transactionId: String? = null, // Si lié à une transaction
    val virementId: String? = null, // Si lié à un virement
    
    // Détails supplémentaires
    val details: String? = null
)
