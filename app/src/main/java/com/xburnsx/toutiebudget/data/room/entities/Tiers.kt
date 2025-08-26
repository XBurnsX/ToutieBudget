package com.xburnsx.toutiebudget.data.room.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
 * Entité Room pour les tiers.
 * Noms de champs IDENTIQUES à 100% avec Pocketbase pour la synchronisation.
 */
@Entity(tableName = "tiers")
data class Tiers(
    @PrimaryKey
    val id: String = "",
    
    @ColumnInfo(name = "utilisateur_id")
    val utilisateurId: String = "",
    
    val nom: String = "",
    
    val created: String = "",
    
    val updated: String = "",
    
    val collectionId: String = "",
    
    val collectionName: String = ""
)
