// chemin/simule: /data/repositories/impl/PreferenceRepositoryImpl.kt
package com.xburnsx.toutiebudget.data.repositories.impl

import com.xburnsx.toutiebudget.data.repositories.PreferenceRepository
import java.util.Date

class PreferenceRepositoryImpl : PreferenceRepository {
    private var dernierRollover: Date? = null
    private var figerPretAPlacer: Boolean = false

    override suspend fun sauvegarderDernierRollover(date: Date) {
        dernierRollover = date
    }

    override suspend fun recupererDernierRollover(): Date? {
        return dernierRollover
    }

    override suspend fun setFigerPretAPlacer(enabled: Boolean) {
        figerPretAPlacer = enabled
    }

    override suspend fun getFigerPretAPlacer(): Boolean {
        return figerPretAPlacer
    }
}
