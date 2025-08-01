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
                    // 1. Créer une NOUVELLE allocation négative dans le mois précédent pour "sortir" l'argent
                    val allocationSortie = com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle(
                        id = "",
                        utilisateurId = allocationAncienne.utilisateurId,
                        enveloppeId = allocationAncienne.enveloppeId,
                        mois = moisPrecedent,
                        solde = -allocationAncienne.solde, // Négatif pour "sortir" l'argent
                        alloue = 0.0,
                        depense = 0.0, // PAS DE DÉPENSE - C'est un rollover !
                        compteSourceId = allocationAncienne.compteSourceId, // GARDER LA PROVENANCE !
                        collectionCompteSource = allocationAncienne.collectionCompteSource
                    )
                    enveloppeRepository.creerAllocationMensuelle(allocationSortie).getOrThrow()

                    // 2. Créer une NOUVELLE allocation positive dans le nouveau mois pour "faire entrer" l'argent
                    val allocationEntree = com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle(
                        id = "",
                        utilisateurId = allocationAncienne.utilisateurId,
                        enveloppeId = allocationAncienne.enveloppeId,
                        mois = nouveauMois,
                        solde = allocationAncienne.solde, // Positif pour "faire entrer" l'argent
                        alloue = allocationAncienne.solde, // C'est de l'argent alloué (transféré)
                        depense = 0.0,
                        compteSourceId = allocationAncienne.compteSourceId, // GARDER LA PROVENANCE !
                        collectionCompteSource = allocationAncienne.collectionCompteSource
                    )
                    enveloppeRepository.creerAllocationMensuelle(allocationEntree).getOrThrow()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}



