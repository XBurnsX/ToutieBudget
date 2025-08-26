package com.xburnsx.toutiebudget.data.room.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif

/**
 * Entité Room pour les enveloppes.
 * Noms de champs IDENTIQUES à 100% avec Pocketbase pour la synchronisation.
 */
@Entity(tableName = "enveloppes")
data class Enveloppe(
    @PrimaryKey
    val id: String = "",
    
    @ColumnInfo(name = "utilisateur_id")
    val utilisateurId: String = "",
    
    val nom: String = "",
    
    @ColumnInfo(name = "categorie_Id")
    val categorieId: String = "",
    
    @ColumnInfo(name = "est_archive")
    val estArchive: Boolean = false,
    
    val ordre: Int = 0,
    
    @ColumnInfo(name = "frequence_objectif")
    val typeObjectif: TypeObjectif = TypeObjectif.Aucun,
    
    @ColumnInfo(name = "montant_objectif")
    val objectifMontant: Double = 0.0,
    
    @ColumnInfo(name = "date_objectif")
    val dateObjectif: String? = null,
    
    @ColumnInfo(name = "date_debut_objectif")
    val dateDebutObjectif: String? = null,
    
    @ColumnInfo(name = "objectif_jour")
    val objectifJour: Int? = null,
    
    @ColumnInfo(name = "reset_apres_echeance")
    val resetApresEcheance: Boolean = false
)
