package com.xburnsx.toutiebudget.data.room.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
/**
 * Entité Room pour les transactions.
 * Noms de champs IDENTIQUES à 100% avec Pocketbase pour la synchronisation.
 */
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey
    val id: String = "",
    
    @ColumnInfo(name = "utilisateur_id")
    val utilisateurId: String = "",
    
    val type: String = "",
    
    val montant: Double = 0.0,
    
    val date: String = "",
    
    val note: String? = null,
    
    @ColumnInfo(name = "compte_id")
    val compteId: String = "",
    
    @ColumnInfo(name = "collection_compte")
    val collectionCompte: String = "",
    
    @ColumnInfo(name = "allocation_mensuelle_id")
    val allocationMensuelleId: String? = null,
    
    @ColumnInfo(name = "est_fractionnee")
    val estFractionnee: Boolean = false,
    
    @ColumnInfo(name = "sous_items")
    val sousItems: String? = null,
    
    @ColumnInfo(name = "tiers_utiliser")
    val tiersUtiliser: String? = null,
    
    val created: String? = null,
    
    val updated: String? = null
)
