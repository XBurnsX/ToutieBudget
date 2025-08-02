// chemin/simule: /domain/services/impl/RolloverServiceImpl.kt


package com.xburnsx.toutiebudget.domain.services.Impl

import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.domain.services.RolloverService
import java.util.Calendar
import java.util.Date

class RolloverServiceImpl(
    private val enveloppeRepository: EnveloppeRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository
) : RolloverService {
    override suspend fun effectuerRolloverMensuel(moisPrecedent: Date, nouveauMois: Date): Result<Unit> {
        return try {
            println("[ROLLOVER_SERVICE] 🔍 Début du rollover: $moisPrecedent -> $nouveauMois")
            val allocationsPrecedentes = enveloppeRepository.recupererAllocationsPourMois(moisPrecedent).getOrThrow()
            println("[ROLLOVER_SERVICE] 📊 Trouvé ${allocationsPrecedentes.size} allocations du mois précédent")

            for (allocationAncienne in allocationsPrecedentes) {
                println("[ROLLOVER_SERVICE] 💰 Allocation: enveloppe=${allocationAncienne.enveloppeId}, solde=${allocationAncienne.solde}, alloué=${allocationAncienne.alloue}, dépense=${allocationAncienne.depense}")
                
                // ⚠️ IGNORER les erreurs d'arrondi microscopiques (moins de 1 centime)
                if (kotlin.math.abs(allocationAncienne.solde) < 0.01) {
                    println("[ROLLOVER_SERVICE] 🗑️ Ignoré: montant microscopique (erreur d'arrondi)")
                    continue
                }
                
                if (allocationAncienne.solde > 0) {
                    println("[ROLLOVER_SERVICE] ✅ Rollover de ${allocationAncienne.solde}$ pour enveloppe ${allocationAncienne.enveloppeId}")
                    
                    val montantATransferer = allocationAncienne.solde
                    
                    // 1. ✅ DÉBUGGER pourquoi mettreAJourAllocation ne marche pas
                    println("[ROLLOVER_SERVICE] 🔍 Debug de l'allocation existante:")
                    println("[ROLLOVER_SERVICE] 📋 ID: '${allocationAncienne.id}'")
                    println("[ROLLOVER_SERVICE] 📋 Enveloppe: '${allocationAncienne.enveloppeId}'")
                    println("[ROLLOVER_SERVICE] 📋 Utilisateur: '${allocationAncienne.utilisateurId}'")
                    println("[ROLLOVER_SERVICE] 📋 Mois: ${allocationAncienne.mois}")
                    println("[ROLLOVER_SERVICE] 📋 Solde actuel: ${allocationAncienne.solde}")
                    
                    // Test : D'abord récupérer l'allocation par ID pour vérifier qu'elle existe
                    try {
                        val allocationVerif = allocationMensuelleRepository.getAllocationById(allocationAncienne.id)
                        if (allocationVerif != null) {
                            println("[ROLLOVER_SERVICE] ✅ Allocation trouvée par ID: solde=${allocationVerif.solde}")
                            
                            // Maintenant essayer de la mettre à jour
                            val allocationMiseAJour = allocationVerif.copy(solde = 0.0)
                            allocationMensuelleRepository.mettreAJourAllocation(allocationMiseAJour)
                            println("[ROLLOVER_SERVICE] ✅ Allocation mise à jour à 0$")
                            
                            // 2. ✅ CRÉER une allocation d'août avec l'argent transféré (rollover transparent)
                            println("[ROLLOVER_SERVICE] 📥 Création allocation d'août: +${montantATransferer}$ pour ${allocationAncienne.enveloppeId}")
                            
                            val nouvelleAllocationAout = allocationAncienne.copy(
                                id = "", // Nouveau ID généré par PocketBase
                                mois = nouveauMois, // Août au lieu de juillet
                                solde = montantATransferer, // Montant transféré
                                alloue = 0.0, // ← IMPORTANT: Pas d'allocation nouvelle ! C'est un rollover
                                depense = 0.0 // Pas de dépense en août pour l'instant
                            )
                            
                            val allocationCreee = allocationMensuelleRepository.creerNouvelleAllocation(nouvelleAllocationAout)
                            println("[ROLLOVER_SERVICE] ✅ Allocation août créée: ID=${allocationCreee.id}, solde=${allocationCreee.solde}, alloué=${allocationCreee.alloue}")
                            
                        } else {
                            println("[ROLLOVER_SERVICE] ⚠️ Allocation ${allocationAncienne.id} n'existe plus (déjà traitée ?)")
                            println("[ROLLOVER_SERVICE] ➡️ On ignore cette allocation et on continue...")
                            continue // Passer à l'allocation suivante
                        }
                    } catch (e: Exception) {
                        println("[ROLLOVER_SERVICE] ❌ Erreur lors de la mise à jour de ${allocationAncienne.id}: ${e.message}")
                        println("[ROLLOVER_SERVICE] ➡️ On ignore cette allocation et on continue...")
                        continue // Passer à l'allocation suivante au lieu de planter
                    }
                } else {
                    println("[ROLLOVER_SERVICE] ⏭️ Pas de rollover pour enveloppe ${allocationAncienne.enveloppeId} (solde=${allocationAncienne.solde})")
                }
            }
            println("[ROLLOVER_SERVICE] 🎉 Rollover terminé avec succès")
            Result.success(Unit)
        } catch (e: Exception) {
            println("[ROLLOVER_SERVICE] ❌ ERREUR durant le rollover: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
}



