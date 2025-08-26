package com.xburnsx.toutiebudget.data.room.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
/**
 * Entité Room pour les allocations mensuelles.
 * Noms de champs IDENTIQUES à 100% avec Pocketbase pour la synchronisation.
 */
@Entity(tableName = "allocation_mensuelle")
data class AllocationMensuelle(
    @PrimaryKey
    val id: String = "",
    
    @ColumnInfo(name = "utilisateur_id")
    val utilisateurId: String = "",
    
    val enveloppeId: String = "",
    
    val mois: String = "",
    
    val solde: Double = 0.0,
    
    val alloue: Double = 0.0,
    
    val depense: Double = 0.0,
    
    val compteSourceId: String? = null,
    
    val collectionCompteSource: String? = null
)
