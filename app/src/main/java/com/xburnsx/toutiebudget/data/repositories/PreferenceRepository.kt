// chemin/simule: /data/repositories/PreferenceRepository.kt
package com.xburnsx.toutiebudget.data.repositories

import java.util.Date

interface PreferenceRepository {
    suspend fun sauvegarderDernierRollover(date: Date)
    suspend fun recupererDernierRollover(): Date?

    // Préférence UI: figer les bandeaux "Prêt à placer" dans Budget
    suspend fun setFigerPretAPlacer(enabled: Boolean)
    suspend fun getFigerPretAPlacer(): Boolean

    // Préférence UI: état des catégories ouvertes/fermées dans Budget (C2).
    // Enregistré sur l'appareil d'abord, puis envoyé à PocketBase quand c'est possible.
    suspend fun sauvegarderCategoriesOuvertes(categoriesOuvertes: Map<String, Boolean>)
    suspend fun recupererCategoriesOuvertes(): Map<String, Boolean>

    // Enveloppes favorites de la page Budget (A4)
    suspend fun sauvegarderEnveloppesFavorites(ids: Set<String>)
    suspend fun recupererEnveloppesFavorites(): Set<String>

    /** Vrai tant que le dernier choix local n'a pas été envoyé à PocketBase. */
    suspend fun preferencesBudgetEnAttenteEnvoi(): Boolean
    suspend fun marquerPreferencesBudgetEnvoyees()

    /** Remplace l'état local par celui du serveur (seulement quand rien n'est en attente). */
    suspend fun appliquerPreferencesBudgetServeur(categoriesOuvertes: Map<String, Boolean>?, favorites: Set<String>?)
}
