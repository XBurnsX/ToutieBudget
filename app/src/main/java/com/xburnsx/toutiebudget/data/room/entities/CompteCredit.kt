package com.xburnsx.toutiebudget.data.room.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.google.gson.annotations.SerializedName

/**
 * Entité Room pour les comptes de crédit.
 * Noms de champs IDENTIQUES à 100% avec Pocketbase pour la synchronisation.
 */
@Entity(tableName = "comptes_credits")
data class CompteCredit(
    @PrimaryKey
    val id: String = "",
    
    @ColumnInfo(name = "utilisateur_id")
    @SerializedName("utilisateur_id")
    val utilisateurId: String = "",
    
    val nom: String = "",
    
    @ColumnInfo(name = "solde_utilise")
    @SerializedName("solde_utilise")
    val soldeUtilise: Double = 0.0,
    
    val couleur: String = "",
    
    @ColumnInfo(name = "archive")
    @SerializedName("archive")
    val estArchive: Boolean = false,
    
    val ordre: Int = 0,
    
    @ColumnInfo(name = "limite_credit")
    @SerializedName("limite_credit")
    val limiteCredit: Double = 0.0,
    
    @ColumnInfo(name = "taux_interet")
    @SerializedName("taux_interet")
    val tauxInteret: Double? = null,
    
    @ColumnInfo(name = "paiement_minimum")
    @SerializedName("paiement_minimum")
    val paiementMinimum: Double? = null,
    
    @ColumnInfo(name = "frais_mensuels_json")
    @SerializedName("frais_mensuels_json")
    val fraisMensuelsJson: String? = null, // On garde String pour Room, on convertira en JSON
    
    val collection: String = "comptes_credits"
)
