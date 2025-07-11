// chemin/simule: /data/modeles/Enveloppe.kt
package com.xburnsx.toutiebudget.data.modeles

import com.google.gson.annotations.SerializedName
import java.util.Date

enum class TypeObjectif {
    AUCUN,
    MENSUEL,
    BIHEBDOMADAIRE,
    ECHEANCE,
    ANNUEL
}

data class Enveloppe(
    val id: String,
    @SerializedName("utilisateur_id")
    val utilisateurId: String,
    val nom: String,
    @SerializedName("categorieId")
    val categorieId: String, // <-- référence à la catégorie
    @SerializedName("est_archive")
    val estArchive: Boolean,
    val ordre: Int,
    @SerializedName("objectif_type")
    val objectifType: TypeObjectif = TypeObjectif.AUCUN,
    @SerializedName("objectif_montant")
    val objectifMontant: Double = 0.0,
    @SerializedName("objectif_date")
    val objectifDate: Date? = null,
    @SerializedName("objectif_jour")
    val objectifJour: Int? = null
)
