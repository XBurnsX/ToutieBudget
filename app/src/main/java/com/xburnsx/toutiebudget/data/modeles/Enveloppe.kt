// chemin/simule: /data/modeles/Enveloppe.kt
package com.xburnsx.toutiebudget.data.modeles

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.util.*

@Entity(tableName = "enveloppes")
data class Enveloppe(
    @PrimaryKey @SerializedName("id") override var id: String = "",
    @SerializedName("created") override var created: Date? = null,
    @SerializedName("updated") override var updated: Date? = null,
    @SerializedName("nom") var nom: String = "",
    @SerializedName("categorie") var categorie: String = "",
    @SerializedName("budget") var budget: Double = 0.0,
    @SerializedName("solde") var solde: Double = 0.0,
    @SerializedName("couleur") var couleur: String = "#000000",
    @SerializedName("emoji") var emoji: String = "✉️",
    @SerializedName("ordre") var ordre: Int = 0,
    @SerializedName("type_objectif") var typeObjectif: String = TypeObjectif.Aucun.name,
    @SerializedName("montant_objectif") var montantObjectif: Double = 0.0,
    @SerializedName("date_objectif") var dateObjectif: Date? = null,
    @SerializedName("notes") var notes: String = "",
    @SerializedName("user_id") var userId: String? = null
) : BaseModel()
