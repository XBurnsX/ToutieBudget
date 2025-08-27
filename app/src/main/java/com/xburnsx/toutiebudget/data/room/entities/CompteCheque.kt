package com.xburnsx.toutiebudget.data.room.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.google.gson.annotations.SerializedName

/**
 * Entité Room pour les comptes chèques.
 * Noms de champs IDENTIQUES à 100% avec Pocketbase pour la synchronisation.
 */
@Entity(tableName = "comptes_cheques")
data class CompteCheque(
    @PrimaryKey
    val id: String = "",
    
    @ColumnInfo(name = "utilisateur_id")
    val utilisateurId: String = "",
    
    val nom: String = "",
    
    val solde: Double = 0.0,
    
    @ColumnInfo(name = "pret_a_placer")
    @SerializedName("pret_a_placer")
    val pretAPlacerRaw: Double? = null,
    
    val couleur: String = "",
    
    @ColumnInfo(name = "archive")
    val estArchive: Boolean = false,
    
    val ordre: Int = 0,
    
    val collection: String = "comptes_cheques"
)
