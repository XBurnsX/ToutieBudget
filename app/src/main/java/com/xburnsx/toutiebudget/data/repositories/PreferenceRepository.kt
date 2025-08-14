// chemin/simule: /data/repositories/PreferenceRepository.kt
package com.xburnsx.toutiebudget.data.repositories

import java.util.Date

interface PreferenceRepository {
    suspend fun sauvegarderDernierRollover(date: Date)
    suspend fun recupererDernierRollover(): Date?

    // Préférence UI: figer les bandeaux "Prêt à placer" dans Budget
    suspend fun setFigerPretAPlacer(enabled: Boolean)
    suspend fun getFigerPretAPlacer(): Boolean

    // Préférence UI: état des catégories ouvertes/fermées dans Budget
    suspend fun sauvegarderCategoriesOuvertes(categoriesOuvertes: Map<String, Boolean>)
    suspend fun recupererCategoriesOuvertes(): Map<String, Boolean>
}
