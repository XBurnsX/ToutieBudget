package com.xburnsx.toutiebudget.domaine.modele

import androidx.compose.ui.graphics.Color
import java.util.Date

/**
 * Représente un compte bancaire. C'est un simple conteneur de données.
 * Les `data class` en Kotlin nous donnent automatiquement des fonctions utiles
 * (equals, hashCode, toString).
 */
data class Compte(
    val id: String,
    val nom: String,
    val solde: Double,
    val pretAPlacer: Double,
    val couleur: Long,
    val type: String,
    val estArchive: Boolean,
    val ordre: Int?
) {
    // Propriété calculée pour convertir facilement le Long en objet Color de Compose.
    val couleurCompose: Color
        get() = Color(couleur)
}
