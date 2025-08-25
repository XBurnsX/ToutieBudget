// chemin/simule: /data/modeles/Transaction.kt
// Dépendances: TypeTransaction.kt, java.util.Date

package com.xburnsx.toutiebudget.data.modeles

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.util.Date

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey @SerializedName("id") override var id: String = "",
    @SerializedName("created") override var created: Date? = null,
    @SerializedName("updated") override var updated: Date? = null,
    @SerializedName("montant") var montant: Double = 0.0,
    @SerializedName("type") var type: String = "",
    @SerializedName("compte") var compte: String = "",
    @SerializedName("compte_couleur") var compteCouleur: String? = null,
    @SerializedName("compte_nom") var compteNom: String? = null,
    @SerializedName("compte_emoji") var compteEmoji: String? = null,
    @SerializedName("vers_compte") var versCompte: String? = null,
    @SerializedName("vers_compte_couleur") var versCompteCouleur: String? = null,
    @SerializedName("vers_compte_nom") var versCompteNom: String? = null,
    @SerializedName("vers_compte_emoji") var versCompteEmoji: String? = null,
    @SerializedName("categorie") var categorie: String? = null,
    @SerializedName("enveloppe") var enveloppe: String? = null,
    @SerializedName("enveloppe_couleur") var enveloppeCouleur: String? = null,
    @SerializedName("enveloppe_nom") var enveloppeNom: String? = null,
    @SerializedName("enveloppe_emoji") var enveloppeEmoji: String? = null,
    @SerializedName("tiers") var tiers: String? = null,
    @SerializedName("note") var note: String? = null,
    @SerializedName("date") var date: Date = Date(),
    @SerializedName("statut") var statut: String? = null,
    @SerializedName("provenance") var provenance: String? = null,
    @SerializedName("mode_operation") var modeOperation: String? = null
) : BaseModel()