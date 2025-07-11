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
    val utilisateurId: String,
    val nom: String,
    @SerializedName("categorie_id")
    val categorieId: String, // <-- référence à la catégorie
    val estArchive: Boolean,
    val ordre: Int,
    val objectifType: TypeObjectif = TypeObjectif.AUCUN,
    val objectifMontant: Double = 0.0,
    val objectifDate: Date? = null,
    val objectifJour: Int? = null
)
