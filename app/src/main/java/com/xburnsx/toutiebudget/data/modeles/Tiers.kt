package com.xburnsx.toutiebudget.data.modeles

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.util.*

@Entity(tableName = "tiers")
data class Tiers(
    @PrimaryKey @SerializedName("id") override var id: String = "",
    @SerializedName("created") override var created: Date? = null,
    @SerializedName("updated") override var updated: Date? = null,
    @SerializedName("nom") var nom: String = "",
    @SerializedName("user_id") var userId: String? = null
) : BaseModel()
