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
                            // Maintenant essayer de la mettre à jour
                            val allocationMiseAJour = allocationVerif.copy(solde = 0.0)
                            allocationMensuelleRepository.mettreAJourAllocation(allocationMiseAJour)
                            
                            // 2. ✅ CRÉER une allocation d'août avec l'argent transféré (rollover transparent)
                            val nouvelleAllocationAout = allocationAncienne.copy(
                                id = "", // Nouveau ID généré par PocketBase
                                mois = nouveauMois, // Août au lieu de juillet
                                solde = montantATransferer, // Montant transféré
                                alloue = 0.0, // ← IMPORTANT: Pas d'allocation nouvelle ! C'est un rollover
                                depense = 0.0 // Pas de dépense en août pour l'instant
                            )
                            
                            allocationMensuelleRepository.creerNouvelleAllocation(nouvelleAllocationAout)
                            
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



