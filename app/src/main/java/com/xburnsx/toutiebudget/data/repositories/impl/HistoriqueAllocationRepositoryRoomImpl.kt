package com.xburnsx.toutiebudget.data.repositories.impl

import com.xburnsx.toutiebudget.data.repositories.HistoriqueAllocationRepository
import com.xburnsx.toutiebudget.data.room.daos.HistoriqueAllocationDao
import com.xburnsx.toutiebudget.data.room.entities.HistoriqueAllocation
import kotlinx.coroutines.flow.Flow

/**
 * Implémentation Room du repository pour l'historique des allocations.
 * Utilise Room comme source de données principale.
 */
class HistoriqueAllocationRepositoryRoomImpl(
    private val historiqueAllocationDao: HistoriqueAllocationDao
) : HistoriqueAllocationRepository {
    
    override fun getHistoriqueByCompte(compteId: String): Flow<List<HistoriqueAllocation>> {
        return historiqueAllocationDao.getHistoriqueByCompte(compteId)
    }
    
    override fun getHistoriqueByCompteWithLimit(compteId: String, limit: Int): Flow<List<HistoriqueAllocation>> {
        return historiqueAllocationDao.getHistoriqueByCompteWithLimit(compteId, limit)
    }
    
    override fun getHistoriqueByEnveloppe(enveloppeId: String): Flow<List<HistoriqueAllocation>> {
        return historiqueAllocationDao.getHistoriqueByEnveloppe(enveloppeId)
    }
    
    override fun getHistoriqueByAllocation(allocationId: String): Flow<List<HistoriqueAllocation>> {
        return historiqueAllocationDao.getHistoriqueByAllocation(allocationId)
    }
    
    override suspend fun insertHistorique(historique: HistoriqueAllocation) {
        try {
            android.util.Log.d("ToutieBudget", "🔄 REPOSITORY : Tentative d'insertion dans l'historique - ${historique.enveloppeNom} - ${historique.montant}$")
            historiqueAllocationDao.insertHistorique(historique)
            android.util.Log.d("ToutieBudget", "✅ REPOSITORY : Insertion réussie dans l'historique")
        } catch (e: Exception) {
            android.util.Log.e("ToutieBudget", "❌ REPOSITORY : Erreur lors de l'insertion dans l'historique: ${e.message}")
            throw e
        }
    }
    
    override suspend fun insertAllHistorique(historique: List<HistoriqueAllocation>) {
        historiqueAllocationDao.insertAllHistorique(historique)
    }
    
    override suspend fun deleteHistoriqueByCompte(compteId: String) {
        historiqueAllocationDao.deleteHistoriqueByCompte(compteId)
    }
    
    override suspend fun deleteHistoriqueByAllocation(allocationId: String) {
        historiqueAllocationDao.deleteHistoriqueByAllocation(allocationId)
    }
    
    override suspend fun deleteAllHistorique() {
        historiqueAllocationDao.deleteAllHistorique()
    }
    
    override suspend fun getCountByCompte(compteId: String): Int {
        return historiqueAllocationDao.getCountByCompte(compteId)
    }
    
    override fun getHistoriqueRecentByCompte(compteId: String): Flow<List<HistoriqueAllocation>> {
        return historiqueAllocationDao.getHistoriqueRecentByCompte(compteId)
    }
}
