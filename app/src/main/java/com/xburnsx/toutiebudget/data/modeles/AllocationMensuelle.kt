package com.xburnsx.toutiebudget.data.modeles

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.util.*

@Entity(tableName = "allocations_mensuelles")
data class AllocationMensuelle(
    @PrimaryKey @SerializedName("id") override var id: String = "",
    @SerializedName("created") override var created: Date? = null,
    @SerializedName("updated") override var updated: Date? = null,
    @SerializedName("mois") var mois: String = "",
    @SerializedName("annee") var annee: Int = 0,
    @SerializedName("pret_a_placer") var pretAPlacer: Double = 0.0,
    @SerializedName("user_id") var userId: String? = null
) : BaseModel()
