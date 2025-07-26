package com.xburnsx.toutiebudget.data.modeles

data class Categorie(
    val id: String,
    val utilisateurId: String,
    val nom: String,
    val ordre: Int = 0
)
