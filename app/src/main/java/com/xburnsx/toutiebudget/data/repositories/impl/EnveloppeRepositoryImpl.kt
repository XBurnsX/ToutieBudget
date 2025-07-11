// chemin/simule: /data/repositories/impl/EnveloppeRepositoryImpl.kt
package com.xburnsx.toutiebudget.data.repositories.impl

import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import java.util.Date
import java.util.UUID

class EnveloppeRepositoryImpl : EnveloppeRepository {
    // Données simulées
    private val enveloppesSimulees = mutableListOf(
        Enveloppe("env1", "user1", "Loyer", "Dépense obligatoire", false, 1, TypeObjectif.MENSUEL, 1200.0, null, 1),
        Enveloppe("env2", "user1", "Épicerie", "Dépense obligatoire", false, 2, TypeObjectif.MENSUEL, 500.0, null, 1),
        Enveloppe("env3", "user1", "Gaz", "Dépense non obligatoire", false, 3)
    )
    private val allocationsSimulees = mutableListOf(
        AllocationMensuelle("alloc1", "user1", "env1", Date(), 1200.0, 1200.0, 0.0, "cheque1", "comptes_cheque"),
        AllocationMensuelle("alloc2", "user1", "env2", Date(), 350.25, 500.0, 149.75, "cheque1", "comptes_cheque")
    )

    override suspend fun recupererToutesLesEnveloppes(): Result<List<Enveloppe>> {
        return Result.success(enveloppesSimulees.filter { !it.estArchive }.sortedBy { it.ordre })
    }

    override suspend fun recupererAllocationsPourMois(mois: Date): Result<List<AllocationMensuelle>> {
        // Pour la simulation, on retourne toutes les allocations
        return Result.success(allocationsSimulees)
    }

    override suspend fun mettreAJourAllocation(allocation: AllocationMensuelle): Result<Unit> {
        val index = allocationsSimulees.indexOfFirst { it.id == allocation.id }
        if (index != -1) {
            allocationsSimulees[index] = allocation
        }
        return Result.success(Unit)
    }

    override suspend fun creerEnveloppe(enveloppe: Enveloppe): Result<Unit> {
        enveloppesSimulees.add(enveloppe)
        return Result.success(Unit)
    }

    override suspend fun recupererOuCreerAllocation(enveloppeId: String, mois: Date): Result<AllocationMensuelle> {
        val allocationExistante = allocationsSimulees.find { it.enveloppeId == enveloppeId }
        if (allocationExistante != null) {
            return Result.success(allocationExistante)
        }
        val nouvelleAllocation = AllocationMensuelle(
            id = UUID.randomUUID().toString(),
            utilisateurId = "user1",
            enveloppeId = enveloppeId,
            mois = mois,
            solde = 0.0,
            alloue = 0.0,
            depense = 0.0,
            compteSourceId = null,
            collectionCompteSource = null
        )
        allocationsSimulees.add(nouvelleAllocation)
        return Result.success(nouvelleAllocation)
    }
}
