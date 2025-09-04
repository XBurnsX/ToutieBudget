// chemin/simule: /data/modeles/Transaction.kt
// Dépendances: TypeTransaction.kt, java.util.Date

package com.xburnsx.toutiebudget.data.modeles

import java.util.Date

/**
 * Modèle de données représentant une transaction financière.
 * Correspond à la collection "transactions" dans PocketBase.
 */
data class Transaction(
    val id: String = "",
    val utilisateurId: String = "",
    val type: TypeTransaction = TypeTransaction.Depense,
    val montant: Double = 0.0,
    val date: Date = Date(),
    val note: String? = null,
    val description: String? = null, // Description de la transaction
    val compteId: String = "",
    val collectionCompte: String = "",
    val allocationMensuelleId: String? = null,
    val estFractionnee: Boolean = false, // Si la transaction est fractionnée
    val sousItems: String? = null, // JSON des sous-items pour les transactions fractionnées
    val tiersUtiliser: String? = null, // Nom du tiers utilisé dans cette transaction
    val soldeAvant: Double = 0.0, // Solde du compte avant la transaction
    val soldeApres: Double = 0.0, // Solde du compte après la transaction
    val dateTransaction: String = "", // Date formatée pour l'affichage
    val created: Date? = null,
    val updated: Date? = null
)