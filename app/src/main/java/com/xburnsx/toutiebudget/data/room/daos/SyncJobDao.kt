package com.xburnsx.toutiebudget.data.room.daos

import androidx.room.*
import com.xburnsx.toutiebudget.data.room.entities.SyncJob
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour gérer les tâches de synchronisation.
 * Permet d'ajouter, récupérer, mettre à jour et supprimer les tâches de la "Liste de Tâches".
 */
@Dao
interface SyncJobDao {
    
    /**
     * Récupère toutes les tâches de synchronisation
     */
    @Query("SELECT * FROM sync_jobs ORDER BY createdAt ASC")
    fun getAllSyncJobs(): Flow<List<SyncJob>>
    
    /**
     * Récupère toutes les tâches en attente (PENDING)
     */
    @Query("SELECT * FROM sync_jobs WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingSyncJobs(): List<SyncJob>
    
    /**
     * Récupère les tâches par type d'entité
     */
    @Query("SELECT * FROM sync_jobs WHERE entityType = :entityType ORDER BY createdAt ASC")
    suspend fun getSyncJobsByEntityType(entityType: String): List<SyncJob>
    
    /**
     * Récupère les tâches par collection Pocketbase
     */
    @Query("SELECT * FROM sync_jobs WHERE collection = :collection ORDER BY createdAt ASC")
    suspend fun getSyncJobsByCollection(collection: String): List<SyncJob>
    
    /**
     * Récupère une tâche par son ID
     */
    @Query("SELECT * FROM sync_jobs WHERE id = :id")
    suspend fun getSyncJobById(id: Long): SyncJob?
    
    /**
     * Récupère une tâche par l'ID de l'entité et l'action
     */
    @Query("SELECT * FROM sync_jobs WHERE entityId = :entityId AND action = :action AND status = 'PENDING' LIMIT 1")
    suspend fun getPendingSyncJobByEntityAndAction(entityId: String, action: String): SyncJob?
    
    /**
     * Insère une nouvelle tâche de synchronisation
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncJob(syncJob: SyncJob): Long
    
    /**
     * Met à jour une tâche existante
     */
    @Update
    suspend fun updateSyncJob(syncJob: SyncJob)
    
    /**
     * Supprime une tâche
     */
    @Delete
    suspend fun deleteSyncJob(syncJob: SyncJob)
    
    /**
     * Supprime toutes les tâches terminées (COMPLETED ou FAILED)
     */
    @Query("DELETE FROM sync_jobs WHERE status IN ('COMPLETED', 'FAILED')")
    suspend fun deleteCompletedSyncJobs()
    
    /**
     * Supprime toutes les tâches d'une entité spécifique
     */
    @Query("DELETE FROM sync_jobs WHERE entityId = :entityId")
    suspend fun deleteSyncJobsByEntityId(entityId: String)
    
    /**
     * Marque une tâche comme en cours
     */
    @Query("UPDATE sync_jobs SET status = 'IN_PROGRESS', lastAttemptAt = :timestamp WHERE id = :id")
    suspend fun markSyncJobInProgress(id: Long, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Marque une tâche comme terminée avec succès
     */
    @Query("UPDATE sync_jobs SET status = 'COMPLETED', completedAt = :timestamp WHERE id = :id")
    suspend fun markSyncJobCompleted(id: Long, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Marque une tâche comme échouée
     */
    @Query("UPDATE sync_jobs SET status = 'FAILED', errorMessage = :errorMessage, lastAttemptAt = :timestamp, retryCount = retryCount + 1 WHERE id = :id")
    suspend fun markSyncJobFailed(id: Long, errorMessage: String, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Remet une tâche en attente pour nouvelle tentative
     */
    @Query("UPDATE sync_jobs SET status = 'PENDING', lastAttemptAt = :timestamp WHERE id = :id")
    suspend fun resetSyncJobToPending(id: Long, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Compte le nombre de tâches en attente
     */
    @Query("SELECT COUNT(*) FROM sync_jobs WHERE status = 'PENDING'")
    suspend fun getPendingSyncJobsCount(): Int
    
    // Méthode temporairement supprimée pour simplifier la compilation
    // TODO: Réimplémenter avec une approche plus simple
}
