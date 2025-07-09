package com.xburnsx.toutiebudget.ui.ecrans.connexion

data class EtatConnexion(
    val isLoading: Boolean = false,
    val connexionReussie: Boolean = false,
    val erreurConnexion: String? = null
)