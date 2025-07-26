// chemin/simule: /data/modeles/Compte.kt
package com.xburnsx.toutiebudget.data.modeles

import com.google.gson.annotations.SerializedName

sealed interface Compte {
    val id: String
    @get:SerializedName("utilisateur_id")
    val utilisateurId: String
    val nom: String
    val solde: Double
    val couleur: String
    @get:SerializedName("est_archive")
    val estArchive: Boolean
    val ordre: Int
    val collection: String
}

data class CompteCheque(
    override val id: String = "",
    @SerializedName("utilisateur_id")
    override var utilisateurId: String = "",
    override val nom: String,
    override val solde: Double,
    @SerializedName("pret_a_placer")
    val pretAPlacerRaw: Double? = null,
    override val couleur: String,
    @SerializedName("est_archive")
    override val estArchive: Boolean,
    override val ordre: Int,
    override val collection: String = "comptes_cheques"
) : Compte {
    // Propriété calculée pour gérer la valeur par défaut
    val pretAPlacer: Double get() = pretAPlacerRaw ?: 0.0
}

data class CompteCredit(
    override val id: String = "",
    @SerializedName("utilisateur_id")
    override var utilisateurId: String = "",
    override val nom: String,
    override val solde: Double,
    override val couleur: String,
    @SerializedName("est_archive")
    override val estArchive: Boolean,
    override val ordre: Int,
    @SerializedName("limite_credit")
    val limiteCredit: Double,
    val interet: Double? = null,
    override val collection: String = "comptes_credits"
) : Compte

data class CompteDette(
    override val id: String = "",
    @SerializedName("utilisateur_id")
    override var utilisateurId: String = "",
    override val nom: String,
    override val solde: Double,
    @SerializedName("est_archive")
    override val estArchive: Boolean,
    override val ordre: Int,
    @SerializedName("montant_initial")
    val montantInitial: Double,
    val interet: Double? = null,
    override val collection: String = "comptes_dettes"
) : Compte {
    // La couleur est gérée dans l'UI, toujours rouge pour les dettes.
    override val couleur: String = "#FF0000"
}

data class CompteInvestissement(
    override val id: String = "",
    @SerializedName("utilisateur_id")
    override var utilisateurId: String = "",
    override val nom: String,
    override val solde: Double,
    override val couleur: String,
    @SerializedName("est_archive")
    override val estArchive: Boolean,
    override val ordre: Int,
    override val collection: String = "comptes_investissement"
) : Compte
