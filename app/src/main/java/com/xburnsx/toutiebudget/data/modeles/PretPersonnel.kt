package com.xburnsx.toutiebudget.data.modeles

import com.google.gson.annotations.SerializedName

data class PretPersonnel(
    val id: String = "",
    @SerializedName("utilisateur_id") val utilisateurId: String = "",
    @SerializedName("nom_tiers") val nomTiers: String? = null,
    @SerializedName("montant_initial") val montantInitial: Double = 0.0,
    val solde: Double = 0.0,
    val type: TypePretPersonnel = TypePretPersonnel.PRET,
    @SerializedName("archive") val estArchive: Boolean = false,
    @SerializedName("date_creation") val dateCreation: String? = null,
    val created: String? = null,
    val updated: String? = null
)

enum class TypePretPersonnel {
    @SerializedName("pret") PRET,
    @SerializedName("dette") DETTE
}


