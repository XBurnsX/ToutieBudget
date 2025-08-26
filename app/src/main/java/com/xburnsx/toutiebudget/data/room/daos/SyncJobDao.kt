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
    @Query("SELECT * FROM sync_jobs WHERE type = :type ORDER BY createdAt ASC")
    suspend fun getSyncJobsByType(type: String): List<SyncJob>
    
    /**
     * Récupère une tâche par son ID
     */
    @Query("SELECT * FROM sync_jobs WHERE id = :id")
    suspend fun getSyncJobById(id: String): SyncJob?
    
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
     * Marque une tâche comme terminée avec succès
     */
    @Query("UPDATE sync_jobs SET status = 'COMPLETED' WHERE id = :id")
    suspend fun markSyncJobCompleted(id: String)
    
    /**
     * Marque une tâche comme échouée
     */
    @Query("UPDATE sync_jobs SET status = 'FAILED' WHERE id = :id")
    suspend fun markSyncJobFailed(id: String)
    
    /**
     * Compte le nombre de tâches en attente
     */
    @Query("SELECT COUNT(*) FROM sync_jobs WHERE status = 'PENDING'")
    suspend fun getPendingSyncJobsCount(): Int
}
