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
    @Query("SELECT * FROM sync_jobs ORDER BY createdAt ASC, id ASC")
    fun getAllSyncJobs(): Flow<List<SyncJob>>
    
    /**
     * Récupère toutes les tâches en attente (PENDING)
     */
    @Query("SELECT * FROM sync_jobs WHERE status = 'PENDING' ORDER BY createdAt ASC, id ASC")
    suspend fun getPendingSyncJobs(): List<SyncJob>
    
    /**
     * Récupère toutes les tâches en attente ET échouées pour retry
     */
    @Query("SELECT * FROM sync_jobs WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC, id ASC")
    suspend fun getPendingAndFailedSyncJobs(): List<SyncJob>

    /**
     * Tâches à envoyer pour un utilisateur : en attente ou en échec réessayable (pas bloquées),
     * dans l'ordre de création (SJ5). Les anciennes tâches sans utilisateur sont incluses.
     */
    @Query(
        "SELECT * FROM sync_jobs WHERE (status = 'PENDING' OR (status = 'FAILED' AND bloque = 0)) " +
        "AND (utilisateurId = :utilisateurId OR utilisateurId = '') ORDER BY createdAt ASC, id ASC"
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
    suspend fun insertSyncJobBrut(syncJob: SyncJob): Long

    @Query(
        "SELECT * FROM sync_jobs WHERE type = :type AND recordId = :recordId " +
            "AND utilisateurId = :utilisateurId " +
            "AND status IN ('PENDING', 'FAILED') AND bloque = 0 ORDER BY createdAt ASC, id ASC"
    )
    suspend fun getModificationsCompactables(
        type: String, recordId: String, utilisateurId: String
    ): List<SyncJob>

    @Query("DELETE FROM sync_jobs WHERE id IN (:ids)")
    suspend fun supprimerParIds(ids: List<String>)

    /**
     * SJ4/SJ5 : compacte la file sans changer l'état final attendu.
     * CREATE+UPDATE devient un CREATE avec le dernier contenu; CREATE+DELETE disparaît;
     * plusieurs UPDATE gardent seulement le dernier; UPDATE+DELETE devient DELETE.
     */
    @Transaction
    suspend fun insertSyncJob(syncJob: SyncJob): Long {
        if (syncJob.recordId.isBlank()) return insertSyncJobBrut(syncJob)
        // CE4 : ne jamais compacter des tâches provenant de deux profils.
        // Une ancienne tâche sans utilisateur reste séparée par prudence.
        val precedents = getModificationsCompactables(
            syncJob.type, syncJob.recordId, syncJob.utilisateurId
        )
        val creation = precedents.firstOrNull { it.action == "CREATE" }
        return when {
            creation != null && syncJob.action == "DELETE" -> {
                supprimerParIds(precedents.map { it.id })
                -1L
            }
            creation != null && syncJob.action in setOf("CREATE", "UPDATE") -> {
                supprimerParIds(precedents.map { it.id })
                insertSyncJobBrut(
                    syncJob.copy(id = creation.id, action = "CREATE", createdAt = creation.createdAt)
                )
            }
            syncJob.action == "UPDATE" && precedents.any { it.action == "UPDATE" } -> {
                supprimerParIds(precedents.filter { it.action == "UPDATE" }.map { it.id })
                insertSyncJobBrut(syncJob)
            }
            syncJob.action == "DELETE" && precedents.isNotEmpty() -> {
                supprimerParIds(precedents.map { it.id })
                insertSyncJobBrut(syncJob)
            }
            else -> insertSyncJobBrut(syncJob)
        }
    }

    @Query("UPDATE sync_jobs SET status = 'PENDING' WHERE status = 'IN_PROGRESS'")
    suspend fun recupererTachesInterrompues()
    
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
    /** SJ8 : une suppression de cette fiche attend encore (autre que la tâche [saufId]) ? */
    @Query("SELECT COUNT(*) FROM sync_jobs WHERE recordId = :recordId AND recordId != '' AND action = 'DELETE' AND status IN ('PENDING', 'FAILED', 'IN_PROGRESS') AND id != :saufId")
    suspend fun compterSuppressionsEnAttente(recordId: String, saufId: String): Int

    /** SJ6 : « garder la version du serveur » abandonne la tâche locale en conflit. */
    @Query("DELETE FROM sync_jobs WHERE id = :id")
    suspend fun supprimerTache(id: String)

    /** PR4 : vider toute la file avant un effacement complet (aucune résurrection de données). */
    @Query("DELETE FROM sync_jobs")
    suspend fun supprimerToutesLesTaches()

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
     * Compte les tâches non confirmées (PENDING/FAILED/IN_PROGRESS) liées à cette fiche.
     * Un upload en cours interdit également de remplacer sa version locale.
     * Certaines anciennes tâches CREATE n'ont pas de recordId : on cherche aussi "id":"<id>"
     * dans le JSON (les clés étrangères sont en snake_case, ex. "compte_id", donc pas de faux positif).
     */
    @Query(
        "SELECT COUNT(*) FROM sync_jobs WHERE status IN ('PENDING', 'FAILED', 'IN_PROGRESS') " +
        "AND (recordId = :recordId OR dataJson LIKE '%\"id\":\"' || :recordId || '\"%')"
    )
    suspend fun compterTachesEnAttentePourFiche(recordId: String): Int
}
