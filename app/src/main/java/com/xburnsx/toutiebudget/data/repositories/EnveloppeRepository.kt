// chemin/simule: /data/repositories/EnveloppeRepository.kt
package com.xburnsx.toutiebudget.data.repositories

import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import java.util.Date

interface EnveloppeRepository {
    suspend fun recupererToutesLesEnveloppes(): Result<List<Enveloppe>>
    suspend fun recupererAllocationsPourMois(mois: Date): Result<List<AllocationMensuelle>>
    suspend fun mettreAJourAllocation(allocation: AllocationMensuelle): Result<Unit>
    suspend fun creerEnveloppe(enveloppe: Enveloppe): Result<Unit>
    suspend fun recupererOuCreerAllocation(enveloppeId: String, mois: Date): Result<AllocationMensuelle>
    suspend fun mettreAJourEnveloppe(enveloppe: Enveloppe): Result<Unit>
    suspend fun supprimerEnveloppe(id: String): Result<Unit>
}
