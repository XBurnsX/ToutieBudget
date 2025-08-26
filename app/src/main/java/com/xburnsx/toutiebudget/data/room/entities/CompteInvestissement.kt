package com.xburnsx.toutiebudget.data.room.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
 * Entité Room pour les comptes d'investissement.
 * Noms de champs IDENTIQUES à 100% avec Pocketbase pour la synchronisation.
 */
@Entity(tableName = "comptes_investissement")
data class CompteInvestissement(
    @PrimaryKey
    val id: String = "",
    
    @ColumnInfo(name = "utilisateur_id")
    val utilisateurId: String = "",
    
    val nom: String = "",
    
    val solde: Double = 0.0,
    
    val couleur: String = "",
    
    @ColumnInfo(name = "archive")
    val estArchive: Boolean = false,
    
    val ordre: Int = 0,
    
    @ColumnInfo(name = "collection")
    val collection: String = "comptes_investissement"
)
