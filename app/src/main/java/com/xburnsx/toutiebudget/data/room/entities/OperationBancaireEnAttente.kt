package com.xburnsx.toutiebudget.data.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * BANQ-03 : opération bancaire dans la boîte d'attente, distincte des transactions
 * comptables. Une insertion ici NE modifie ni compte ni enveloppe.
 */
@Entity(
    tableName = "operations_bancaires_attente",
    indices = [
        Index(value = ["utilisateur_id", "compte_id", "source", "id_externe"], unique = true),
        Index(value = ["utilisateur_id", "decision"])
    ]
)
data class OperationBancaireEnAttente(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "utilisateur_id") val utilisateurId: String,
    @ColumnInfo(name = "compte_id") val compteId: String,
    @ColumnInfo(name = "collection_compte") val collectionCompte: String,
    val source: String,
    @ColumnInfo(name = "id_externe") val idExterne: String,
    @ColumnInfo(name = "date_operation") val dateOperation: String,
    @ColumnInfo(name = "montant_centimes") val montantCentimes: Long,
    @ColumnInfo(name = "libelle_brut") val libelleBrut: String,
    @ColumnInfo(name = "statut_bancaire") val statutBancaire: String = "EN_ATTENTE",
    /** A_CONFIRMER / IGNOREE ; d'autres états exigent un lien comptable vérifié. */
    val decision: String = "A_CONFIRMER",
    @ColumnInfo(name = "tiers_id") val tiersId: String? = null,
    @ColumnInfo(name = "verification_requise") val verificationRequise: Boolean = false,
    @ColumnInfo(name = "montant_final_centimes") val montantFinalCentimes: Long? = null
)
