// chemin/simule: /data/modeles/Compte.kt
package com.xburnsx.toutiebudget.data.modeles

sealed interface Compte {
    val id: String
    val utilisateurId: String
    val nom: String
    val solde: Double
    val couleur: String
    val estArchive: Boolean
    val ordre: Int
}

data class CompteCheque(
    override val id: String,
    override val utilisateurId: String,
    override val nom: String,
    override val solde: Double,
    override val couleur: String,
    override val estArchive: Boolean,
    override val ordre: Int
) : Compte

data class CompteCredit(
    override val id: String,
    override val utilisateurId: String,
    override val nom: String,
    override val solde: Double,
    override val couleur: String,
    override val estArchive: Boolean,
    override val ordre: Int,
    val limiteCredit: Double
) : Compte
