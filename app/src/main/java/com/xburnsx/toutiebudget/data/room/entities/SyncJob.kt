package com.xburnsx.toutiebudget.data.room.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité représentant une tâche de synchronisation à effectuer.
 * C'est la "Liste de Tâches" qui permet de gérer les modifications offline.
 */
@Entity(tableName = "sync_jobs")
data class SyncJob(
    @PrimaryKey
    val id: String = "",
    
    /**
     * Type d'entité à synchroniser (ex: "TRANSACTION", "COMPTE", "CATEGORIE")
     */
    val type: String,
    
    /**
     * Action à effectuer (CREATE, UPDATE, DELETE)
     */
    val action: String,
    
    /**
     * Données JSON de l'entité à synchroniser
     */
    val dataJson: String,
    
    /**
     * Timestamp de création de la tâche
     */
    val createdAt: Long = 0,
    
    /**
     * Statut de la tâche (PENDING, IN_PROGRESS, COMPLETED, FAILED)
     */
    val status: String = "PENDING"
)
