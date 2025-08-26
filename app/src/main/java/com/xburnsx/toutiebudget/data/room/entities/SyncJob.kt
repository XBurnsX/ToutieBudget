package com.xburnsx.toutiebudget.data.room.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entité représentant une tâche de synchronisation à effectuer.
 * C'est la "Liste de Tâches" qui permet de gérer les modifications offline.
 */
@Entity(tableName = "sync_jobs")
data class SyncJob(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Type d'entité à synchroniser (ex: "compte", "transaction", "categorie")
     */
    val entityType: String,
    
    /**
     * Action à effectuer (CREATE, UPDATE, DELETE)
     */
    val action: String,
    
    /**
     * ID de l'entité concernée
     */
    val entityId: String,
    
    /**
     * Collection Pocketbase cible (ex: "comptes_cheques", "transactions")
     */
    val collection: String,
    
    /**
     * Données JSON de l'entité à synchroniser
     */
    val dataJson: String,
    
    /**
     * Statut de la tâche (PENDING, IN_PROGRESS, COMPLETED, FAILED)
     */
    val status: String = "PENDING",
    
    /**
     * Nombre de tentatives de synchronisation
     */
    val retryCount: Int = 0,
    
    /**
     * Message d'erreur en cas d'échec
     */
    val errorMessage: String? = null,
    
    /**
     * Date de création de la tâche
     */
    val createdAt: Date = Date(),
    
    /**
     * Date de dernière tentative
     */
    val lastAttemptAt: Date? = null,
    
    /**
     * Date de complétion
     */
    val completedAt: Date? = null
)
