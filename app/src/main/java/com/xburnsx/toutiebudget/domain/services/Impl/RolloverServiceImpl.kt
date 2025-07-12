// chemin/simule: /domain/services/impl/RolloverServiceImpl.kt


package com.xburnsx.toutiebudget.domain.services.Impl

import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.domain.services.RolloverService
import java.util.Date

class RolloverServiceImpl(
    private val enveloppeRepository: EnveloppeRepository
) : RolloverService {
    override suspend fun effectuerRolloverMensuel(moisPrecedent: Date, nouveauMois: Date): Result<Unit> {
        return try {
            val allocationsPrecedentes = enveloppeRepository.recupererAllocationsPourMois(moisPrecedent).getOrThrow()

            for (allocationAncienne in allocationsPrecedentes) {
                if (allocationAncienne.solde > 0) {
                    val allocationNouvelleResult = enveloppeRepository.recupererOuCreerAllocation(
                        enveloppeId = allocationAncienne.enveloppeId,
                        mois = nouveauMois
                    )
                    val allocationNouvelle = allocationNouvelleResult.getOrThrow()

                    val estCompatible = allocationNouvelle.compteSourceId == null || allocationNouvelle.compteSourceId == allocationAncienne.compteSourceId
                    if (!estCompatible) {
                        continue
                    }

                    val allocationMaj = allocationNouvelle.copy(
                        solde = allocationNouvelle.solde + allocationAncienne.solde,
                        compteSourceId = allocationAncienne.compteSourceId,
                        collectionCompteSource = allocationAncienne.collectionCompteSource
                    )
                    enveloppeRepository.mettreAJourAllocation(allocationMaj).getOrThrow()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
