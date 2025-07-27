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
    val compteId: String = "",
    val collectionCompte: String = "",
    val allocationMensuelleId: String? = null,
    val tiersId: String? = null, // ID du tiers associé à cette transaction
    val tiers: String? = null, // Nom du tiers (peut être utilisé sans tiersId pour les virements)
    val created: Date? = null,
    val updated: Date? = null
)