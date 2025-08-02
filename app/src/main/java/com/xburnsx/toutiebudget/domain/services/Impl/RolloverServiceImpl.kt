// chemin/simule: /domain/services/impl/RolloverServiceImpl.kt


package com.xburnsx.toutiebudget.domain.services.Impl

import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.domain.services.RolloverService
import java.util.Calendar
import java.util.Date

class RolloverServiceImpl(
    private val enveloppeRepository: EnveloppeRepository
) : RolloverService {
    override suspend fun effectuerRolloverMensuel(moisPrecedent: Date, nouveauMois: Date): Result<Unit> {
        return try {
            // ⚠️ CORRECTION CRITIQUE: Fixer les dates au premier jour du mois à 00:00
            val moisPrecedentFixe = Calendar.getInstance().apply {
                time = moisPrecedent
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            
            val nouveauMoisFixe = Calendar.getInstance().apply {
                time = nouveauMois
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            
            println("[ROLLOVER_SERVICE] 🔍 Début du rollover: $moisPrecedentFixe -> $nouveauMoisFixe")
            val allocationsPrecedentes = enveloppeRepository.recupererAllocationsPourMois(moisPrecedentFixe).getOrThrow()
            println("[ROLLOVER_SERVICE] 📊 Trouvé ${allocationsPrecedentes.size} allocations du mois précédent")

            for (allocationAncienne in allocationsPrecedentes) {
                println("[ROLLOVER_SERVICE] 💰 Allocation: enveloppe=${allocationAncienne.enveloppeId}, solde=${allocationAncienne.solde}")
                if (allocationAncienne.solde > 0) {
                    println("[ROLLOVER_SERVICE] ✅ Rollover de ${allocationAncienne.solde}$ pour enveloppe ${allocationAncienne.enveloppeId}")
                    // 1. Créer une NOUVELLE allocation négative dans le mois précédent pour "sortir" l'argent
                    val allocationSortie = com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle(
                        id = "",
                        utilisateurId = allocationAncienne.utilisateurId,
                        enveloppeId = allocationAncienne.enveloppeId,
                        mois = moisPrecedentFixe, // ✅ Date fixée au premier jour du mois à 00:00
                        solde = -allocationAncienne.solde, // Négatif pour "sortir" l'argent
                        alloue = 0.0,
                        depense = 0.0, // PAS DE DÉPENSE - C'est un rollover !
                        compteSourceId = allocationAncienne.compteSourceId, // GARDER LA PROVENANCE !
                        collectionCompteSource = allocationAncienne.collectionCompteSource
                    )
                    println("[ROLLOVER_SERVICE] 📤 Création allocation sortie: ${allocationSortie.solde}$ le ${allocationSortie.mois}")
                    val resultatSortie = enveloppeRepository.creerAllocationMensuelle(allocationSortie).getOrThrow()
                    println("[ROLLOVER_SERVICE] ✅ Allocation sortie créée: ${resultatSortie.id}")

                    // 2. Créer une NOUVELLE allocation positive dans le nouveau mois pour "faire entrer" l'argent
                    val allocationEntree = com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle(
                        id = "",
                        utilisateurId = allocationAncienne.utilisateurId,
                        enveloppeId = allocationAncienne.enveloppeId,
                        mois = nouveauMoisFixe, // ✅ Date fixée au premier jour du mois à 00:00
                        solde = allocationAncienne.solde, // Positif pour "faire entrer" l'argent
                        alloue = allocationAncienne.solde, // C'est de l'argent alloué (transféré)
                        depense = 0.0,
                        compteSourceId = allocationAncienne.compteSourceId, // GARDER LA PROVENANCE !
                        collectionCompteSource = allocationAncienne.collectionCompteSource
                    )
                    println("[ROLLOVER_SERVICE] 📥 Création allocation entrée: ${allocationEntree.solde}$ le ${allocationEntree.mois}")
                    val resultatEntree = enveloppeRepository.creerAllocationMensuelle(allocationEntree).getOrThrow()
                    println("[ROLLOVER_SERVICE] ✅ Allocation entrée créée: ${resultatEntree.id}")
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



