package com.xburnsx.toutiebudget.data.modeles

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.util.*

@Entity(tableName = "prets_personnels")
data class PretPersonnel(
    @PrimaryKey @SerializedName("id") override var id: String = "",
    @SerializedName("created") override var created: Date? = null,
    @SerializedName("updated") override var updated: Date? = null,
    @SerializedName("nom") var nom: String = "",
    @SerializedName("montant_initial") var montantInitial: Double = 0.0,
    @SerializedName("solde_restant") var soldeRestant: Double = 0.0,
    @SerializedName("taux_interet") var tauxInteret: Double = 0.0,
    @SerializedName("duree_mois") var dureeMois: Int = 0,
    @SerializedName("date_debut") var dateDebut: Date = Date(),
    @SerializedName("user_id") var userId: String? = null
) : BaseModel()


