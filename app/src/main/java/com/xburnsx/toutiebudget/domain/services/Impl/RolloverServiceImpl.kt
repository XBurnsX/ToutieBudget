// chemin/simule: /domain/services/impl/RolloverServiceImpl.kt


package com.xburnsx.toutiebudget.domain.services.Impl

import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.domain.services.RolloverService
import java.util.Date

class RolloverServiceImpl(
    private val enveloppeRepository: EnveloppeRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository
) : RolloverService {
    override suspend fun effectuerRolloverMensuel(moisPrecedent: Date, nouveauMois: Date): Result<Unit> {
        return try {
            val allocationsPrecedentes = enveloppeRepository.recupererAllocationsPourMois(moisPrecedent).getOrThrow()

            for (allocationAncienne in allocationsPrecedentes) {
                // ⚠️ IGNORER les erreurs d'arrondi microscopiques (moins de 1 centime)
                if (kotlin.math.abs(allocationAncienne.solde) < 0.01) {
                    continue
                }
                
                if (allocationAncienne.solde > 0) {
                    val montantATransferer = allocationAncienne.solde
                    
                    // Test : D'abord récupérer l'allocation par ID pour vérifier qu'elle existe
                    try {
                        val allocationVerif = allocationMensuelleRepository.getAllocationById(allocationAncienne.id)
                        if (allocationVerif != null) {
                            // 1. ✅ Mettre à jour août : solde à 0 ET alloué déduit
                            val allocationAoutMiseAJour = allocationVerif.copy(
                                solde = 0.0, // Solde remis à zéro
                                alloue = allocationVerif.alloue - montantATransferer // Alloué déduit du montant transféré
                            )
                            allocationMensuelleRepository.mettreAJourAllocation(allocationAoutMiseAJour)
                            
                            // 2. ✅ Créer septembre : solde ET alloué augmentés du montant transféré
                            val nouvelleAllocationSeptembre = allocationAncienne.copy(
                                id = "", // Nouveau ID généré
                                mois = nouveauMois, // Septembre
                                solde = montantATransferer, // Montant transféré d'août
                                alloue = montantATransferer, // Montant transféré = alloué pour septembre
                                depense = 0.0 // Pas de dépense en septembre pour l'instant
                            )
                            
                            allocationMensuelleRepository.creerNouvelleAllocation(nouvelleAllocationSeptembre)
                            
                        } else {
                            continue // Passer à l'allocation suivante
                        }
                    } catch (_: Exception) {
                        continue // Passer à l'allocation suivante au lieu de planter
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}



