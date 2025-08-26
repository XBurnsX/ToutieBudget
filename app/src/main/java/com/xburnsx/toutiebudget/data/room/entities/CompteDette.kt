package com.xburnsx.toutiebudget.data.room.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
 * Entité Room pour les comptes de dette.
 * Noms de champs IDENTIQUES à 100% avec Pocketbase pour la synchronisation.
 */
@Entity(tableName = "comptes_dettes")
data class CompteDette(
    @PrimaryKey
    val id: String = "",
    
    @ColumnInfo(name = "utilisateur_id")
    val utilisateurId: String = "",
    
    val nom: String = "",
    
    @ColumnInfo(name = "solde_dette")
    val soldeDette: Double = 0.0,
    
    @ColumnInfo(name = "archive")
    val estArchive: Boolean = false,
    
    val ordre: Int = 0,
    
    @ColumnInfo(name = "montant_initial")
    val montantInitial: Double = 0.0,
    
    @ColumnInfo(name = "taux_interet")
    val tauxInteret: Double? = null,
    
    @ColumnInfo(name = "paiement_minimum")
    val paiementMinimum: Double? = null,
    
    @ColumnInfo(name = "duree_mois_pret")
    val dureeMoisPret: Int? = null,
    
    @ColumnInfo(name = "paiement_effectue")
    val paiementEffectue: Int = 0,
    
    @ColumnInfo(name = "prix_total")
    val prixTotal: Double? = null,
    
    @ColumnInfo(name = "collection")
    val collection: String = "comptes_dettes"
)
