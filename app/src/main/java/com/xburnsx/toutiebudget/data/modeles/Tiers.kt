// chemin/simule: /data/modeles/Tiers.kt
package com.xburnsx.toutiebudget.data.modeles

import com.google.gson.annotations.SerializedName

/**
 * Modèle de données représentant un tiers (personne ou entité).
 * Correspond à la collection "tiers" dans PocketBase.
 */
data class Tiers(
    val id: String = "",
    @SerializedName("utilisateur_id")
    val utilisateurId: String = "",
    val nom: String = ""
)