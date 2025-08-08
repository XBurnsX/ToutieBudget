// chemin/simule: /domain/usecases/UseCases.kt
package com.xburnsx.toutiebudget.domain.usecases

import com.xburnsx.toutiebudget.domain.services.RolloverService
import com.xburnsx.toutiebudget.data.repositories.PreferenceRepository
import java.util.*

// --- Interfaces ---

// --- Implémentations ---

// --- Autres Use Cases ---

class VerifierEtExecuterRolloverUseCase(
    private val rolloverService: RolloverService,
    private val preferenceRepository: PreferenceRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return try {
            val aujourdhui = Calendar.getInstance()
            val dernierRolloverCal = Calendar.getInstance()
            val dernierRolloverDate = preferenceRepository.recupererDernierRollover()

            if (dernierRolloverDate != null) {
                dernierRolloverCal.time = dernierRolloverDate
            } else {
                dernierRolloverCal.add(Calendar.MONTH, -1)
            }

            val anneeActuelle = aujourdhui.get(Calendar.YEAR)
            val moisActuel = aujourdhui.get(Calendar.MONTH)
            val anneeDernierRollover = dernierRolloverCal.get(Calendar.YEAR)
            val moisDernierRollover = dernierRolloverCal.get(Calendar.MONTH)

            val doitFaireRollover = anneeActuelle > anneeDernierRollover || (anneeActuelle == anneeDernierRollover && moisActuel > moisDernierRollover)

            if (doitFaireRollover) {
                val moisPrecedentCal = Calendar.getInstance().apply { time = dernierRolloverCal.time; set(Calendar.DAY_OF_MONTH, 1) }
                rolloverService.effectuerRolloverMensuel(moisPrecedent = moisPrecedentCal.time, nouveauMois = aujourdhui.time).getOrThrow()
                preferenceRepository.sauvegarderDernierRollover(aujourdhui.time)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
