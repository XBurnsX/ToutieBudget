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
     * Récupère toutes les tâches en attente ET échouées pour retry
     */
    @Query("SELECT * FROM sync_jobs WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC")
    suspend fun getPendingAndFailedSyncJobs(): List<SyncJob>

    /**
     * Tâches à envoyer pour un utilisateur : en attente ou en échec réessayable (pas bloquées),
     * dans l'ordre de création (SJ5). Les anciennes tâches sans utilisateur sont incluses.
     */
    @Query(
        "SELECT * FROM sync_jobs WHERE (status = 'PENDING' OR (status = 'FAILED' AND bloque = 0)) " +
        "AND (utilisateurId = :utilisateurId OR utilisateurId = '') ORDER BY createdAt ASC"
    )
    suspend fun getTachesAEnvoyer(utilisateurId: String): List<SyncJob>

    /** Résultat d'un essai d'envoi (SJ10, SJ11). */
    @Query(
        "UPDATE sync_jobs SET status = :status, tentatives = tentatives + 1, derniereErreur = :erreur, " +
        "codeHttp = :codeHttp, derniereTentative = :quand, bloque = :bloque WHERE id = :id"
    )
    suspend fun enregistrerEssai(id: String, status: String, erreur: String?, codeHttp: Int?, quand: Long, bloque: Boolean)

    /** « Réessayer » manuel : remet la tâche dans la file, sans effacer son historique d'essais. */
    @Query("UPDATE sync_jobs SET status = 'PENDING', bloque = 0 WHERE id = :id")
    suspend fun reactiver(id: String)
    
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
     * Supprime uniquement les tâches terminées avec succès (COMPLETED uniquement)
     */
    @Query("DELETE FROM sync_jobs WHERE status = 'COMPLETED'")
    suspend fun deleteCompletedSyncJobs()
    
    /**
     * Supprime toutes les tâches terminées (COMPLETED ET FAILED)
     */
    @Query("DELETE FROM sync_jobs WHERE status IN ('COMPLETED', 'FAILED')")
    suspend fun deleteAllCompletedAndFailedJobs()
    
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
     * Met à jour le statut d'une tâche
     */
    @Query("UPDATE sync_jobs SET status = :status WHERE id = :id")
    suspend fun updateSyncJobStatus(id: String, status: String)
    
    /**
     * Compte le nombre de tâches en attente
     */
    @Query("SELECT COUNT(*) FROM sync_jobs WHERE status = 'PENDING'")
    suspend fun getPendingSyncJobsCount(): Int

    /**
     * Compte les tâches pas encore envoyées (PENDING/FAILED) qui touchent cette fiche.
     * Certaines anciennes tâches CREATE n'ont pas de recordId : on cherche aussi "id":"<id>"
     * dans le JSON (les clés étrangères sont en snake_case, ex. "compte_id", donc pas de faux positif).
     */
    @Query(
        "SELECT COUNT(*) FROM sync_jobs WHERE status IN ('PENDING', 'FAILED') " +
        "AND (recordId = :recordId OR dataJson LIKE '%\"id\":\"' || :recordId || '\"%')"
    )
    suspend fun compterTachesEnAttentePourFiche(recordId: String): Int
}
