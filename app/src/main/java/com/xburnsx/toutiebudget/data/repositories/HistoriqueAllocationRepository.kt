package com.xburnsx.toutiebudget.data.repositories

import com.xburnsx.toutiebudget.data.room.entities.HistoriqueAllocation
import kotlinx.coroutines.flow.Flow

/**
 * Interface du repository pour l'historique des allocations.
 * Permet de gérer l'historique des modifications d'allocations.
 */
interface HistoriqueAllocationRepository {
    
    /**
     * Récupère l'historique des allocations pour un compte donné.
     */
    fun getHistoriqueByCompte(compteId: String): Flow<List<HistoriqueAllocation>>
    
    /**
     * Récupère l'historique des allocations pour un compte avec une limite.
     */
    fun getHistoriqueByCompteWithLimit(compteId: String, limit: Int): Flow<List<HistoriqueAllocation>>
    
    /**
     * Récupère l'historique des allocations pour une enveloppe donnée.
     */
    fun getHistoriqueByEnveloppe(enveloppeId: String): Flow<List<HistoriqueAllocation>>
    
    /**
     * Récupère l'historique des allocations pour une allocation donnée.
     */
    fun getHistoriqueByAllocation(allocationId: String): Flow<List<HistoriqueAllocation>>
    
    /**
     * Insère un nouvel enregistrement d'historique.
     */
    suspend fun insertHistorique(historique: HistoriqueAllocation)
    
    /**
     * Insère plusieurs enregistrements d'historique.
     */
    suspend fun insertAllHistorique(historique: List<HistoriqueAllocation>)
    
    /**
     * Supprime l'historique pour un compte donné.
     */
    suspend fun deleteHistoriqueByCompte(compteId: String)
    
    /**
     * Supprime l'historique pour une allocation donnée.
     */
    suspend fun deleteHistoriqueByAllocation(allocationId: String)
    
    /**
     * Supprime tout l'historique.
     */
    suspend fun deleteAllHistorique()
    
    /**
     * Récupère le nombre total d'enregistrements d'historique pour un compte.
     */
    suspend fun getCountByCompte(compteId: String): Int
    
    /**
     * Récupère l'historique récent (dernières 24h) pour un compte.
     */
    fun getHistoriqueRecentByCompte(compteId: String): Flow<List<HistoriqueAllocation>>
}
