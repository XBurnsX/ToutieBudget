package com.xburnsx.toutiebudget.utils

import androidx.room.withTransaction
import com.xburnsx.toutiebudget.data.room.ToutieBudgetDatabase

/**
 * Frontière transactionnelle des opérations financières composées.
 *
 * La version Room garantit que les écritures métier et leurs SyncJobs sont validés ensemble.
 * La version directe garde les services testables avec les dépôts en mémoire.
 */
interface OperationAtomique {
    suspend fun <T> executer(bloc: suspend () -> T): T
}

object OperationAtomiqueDirecte : OperationAtomique {
    override suspend fun <T> executer(bloc: suspend () -> T): T = bloc()
}

class OperationAtomiqueRoom(
    private val database: ToutieBudgetDatabase
) : OperationAtomique {
    override suspend fun <T> executer(bloc: suspend () -> T): T =
        database.withTransaction { bloc() }
}
