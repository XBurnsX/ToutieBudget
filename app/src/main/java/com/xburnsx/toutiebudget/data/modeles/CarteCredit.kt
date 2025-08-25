package com.xburnsx.toutiebudget.data.modeles

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.util.*

@Entity(tableName = "cartes_credit")
data class CarteCredit(
    @PrimaryKey @SerializedName("id") override var id: String = "",
    @SerializedName("created") override var created: Date? = null,
    @SerializedName("updated") override var updated: Date? = null,
    @SerializedName("nom") var nom: String = "",
    @SerializedName("solde") var solde: Double = 0.0,
    @SerializedName("limite") var limite: Double = 0.0,
    @SerializedName("taux_interet") var tauxInteret: Double = 0.0,
    @SerializedName("date_facturation") var dateFacturation: Int = 1,
    @SerializedName("user_id") var userId: String? = null
) : BaseModel()
