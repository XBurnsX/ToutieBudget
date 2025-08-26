package com.xburnsx.toutiebudget.data.room.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
 * Entité Room pour les prêts personnels.
 * Noms de champs IDENTIQUES à 100% avec Pocketbase pour la synchronisation.
 */
@Entity(tableName = "pret_personnel")
data class PretPersonnel(
    @PrimaryKey
    val id: String = "",
    
    @ColumnInfo(name = "utilisateur_id")
    val utilisateurId: String = "",
    
    @ColumnInfo(name = "nom_tiers")
    val nomTiers: String? = null,
    
    @ColumnInfo(name = "montant_initial")
    val montantInitial: Double = 0.0,
    
    val solde: Double = 0.0,
    
    val type: String = "",
    
    @ColumnInfo(name = "archive")
    val estArchive: Boolean = false,
    
    @ColumnInfo(name = "date_creation")
    val dateCreation: String? = null,
    
    val created: String? = null,
    
    val updated: String? = null
)
