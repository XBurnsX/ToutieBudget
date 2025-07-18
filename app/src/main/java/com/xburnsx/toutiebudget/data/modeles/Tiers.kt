// filepath: c:\Users\XBurnsX\Desktop\Project\Kotlin\ToutieBudget2\app\src\main\java\com\xburnsx\toutiebudget\data\modeles\Tiers.kt
// chemin/simule: /data/modeles/Tiers.kt
// Dépendances: PocketBase pour la persistance

package com.xburnsx.toutiebudget.data.modeles

/**
 * Modèle représentant un tiers dans le système.
 * Un tiers est une entité externe avec laquelle on effectue des transactions.
 */
data class Tiers(
    val id: String = "",
    val nom: String = "",
    val dateCreation: String = "",
    val dateModification: String = ""
)
