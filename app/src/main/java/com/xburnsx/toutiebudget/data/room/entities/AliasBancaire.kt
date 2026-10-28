package com.xburnsx.toutiebudget.data.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Une association de libellé vers un ID de tiers, jamais une nouvelle fiche marchande. */
@Entity(
    tableName = "alias_bancaires",
    indices = [Index(value = ["utilisateur_id", "compte_id"])]
)
data class AliasBancaire(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "utilisateur_id") val utilisateurId: String,
    @ColumnInfo(name = "compte_id") val compteId: String?,
    @ColumnInfo(name = "libelle_brut") val libelleBrut: String,
    @ColumnInfo(name = "tiers_id") val tiersId: String,
    val active: Boolean = true
)
