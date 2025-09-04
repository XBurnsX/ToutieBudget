package com.xburnsx.toutiebudget.data.room.daos

import androidx.room.*
import com.xburnsx.toutiebudget.data.room.entities.HistoriqueAllocation
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour la gestion de l'historique des allocations.
 * Permet de récupérer l'historique complet des modifications d'allocations
 * pour un compte donné, trié par date.
 */
@Dao
interface HistoriqueAllocationDao {
    
    /**
     * Récupère tout l'historique des allocations pour un compte donné,
     * trié par date décroissante (plus récent en premier).
     */
    @Query("""
        SELECT * FROM historique_allocation 
        WHERE compteId = :compteId 
        ORDER BY dateAction DESC
    """)
    fun getHistoriqueByCompte(compteId: String): Flow<List<HistoriqueAllocation>>
    
    /**
     * Récupère l'historique des allocations pour un compte donné avec une limite,
     * trié par date décroissante.
     */
    @Query("""
        SELECT * FROM historique_allocation 
        WHERE compteId = :compteId 
        ORDER BY dateAction DESC 
        LIMIT :limit
    """)
    fun getHistoriqueByCompteWithLimit(compteId: String, limit: Int): Flow<List<HistoriqueAllocation>>
    
    /**
     * Récupère l'historique des allocations pour une enveloppe donnée,
     * trié par date décroissante.
     */
    @Query("""
        SELECT * FROM historique_allocation 
        WHERE enveloppeId = :enveloppeId 
        ORDER BY dateAction DESC
    """)
    fun getHistoriqueByEnveloppe(enveloppeId: String): Flow<List<HistoriqueAllocation>>
    
    /**
     * Récupère l'historique des allocations pour une allocation donnée,
     * trié par date décroissante.
     */
    @Query("""
        SELECT * FROM historique_allocation 
        WHERE allocationId = :allocationId 
        ORDER BY dateAction DESC
    """)
    fun getHistoriqueByAllocation(allocationId: String): Flow<List<HistoriqueAllocation>>
    
    /**
     * Insère un nouvel enregistrement d'historique.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistorique(historique: HistoriqueAllocation)
    
    /**
     * Insère plusieurs enregistrements d'historique en une seule opération.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllHistorique(historique: List<HistoriqueAllocation>)
    
    /**
     * Supprime l'historique pour un compte donné.
     */
    @Query("DELETE FROM historique_allocation WHERE compteId = :compteId")
    suspend fun deleteHistoriqueByCompte(compteId: String)
    
    /**
     * Supprime l'historique pour une allocation donnée.
     */
    @Query("DELETE FROM historique_allocation WHERE allocationId = :allocationId")
    suspend fun deleteHistoriqueByAllocation(allocationId: String)
    
    /**
     * Supprime tout l'historique.
     */
    @Query("DELETE FROM historique_allocation")
    suspend fun deleteAllHistorique()
    
    /**
     * Récupère le nombre total d'enregistrements d'historique pour un compte.
     */
    @Query("SELECT COUNT(*) FROM historique_allocation WHERE compteId = :compteId")
    suspend fun getCountByCompte(compteId: String): Int
    
    /**
     * Récupère l'historique récent (dernières 24h) pour un compte.
     */
    @Query("""
        SELECT * FROM historique_allocation 
        WHERE compteId = :compteId 
        AND dateAction >= datetime('now', '-1 day')
        ORDER BY dateAction DESC
    """)
    fun getHistoriqueRecentByCompte(compteId: String): Flow<List<HistoriqueAllocation>>
}
