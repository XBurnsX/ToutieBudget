// chemin/simule: /data/modeles/Transaction.kt
package com.xburnsx.toutiebudget.data.modeles

import java.util.Date

enum class TypeTransaction {
    Depense,
    Revenu,
    Pret,
    Emprunt
}

data class Transaction(
    val id: String,
    val utilisateurId: String,
    val type: TypeTransaction,
    val montant: Double,
    val date: Date,
    val note: String?,
    val compteId: String,
    val collectionCompte: String,
    val allocationMensuelleId: String?
)
