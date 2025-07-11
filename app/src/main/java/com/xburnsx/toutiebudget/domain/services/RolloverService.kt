// chemin/simule: /domain/services/RolloverService.kt
package com.xburnsx.toutiebudget.domain.services

import java.util.Date

interface RolloverService {
    suspend fun effectuerRolloverMensuel(moisPrecedent: Date, nouveauMois: Date): Result<Unit>
}
