// chemin/simule: /data/repositories/PreferenceRepository.kt
package com.xburnsx.toutiebudget.data.repositories

import java.util.Date

interface PreferenceRepository {
    suspend fun sauvegarderDernierRollover(date: Date)
    suspend fun recupererDernierRollover(): Date?
}
