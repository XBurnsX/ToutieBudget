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
     * ID de l'enregistrement Pocketbase à modifier (pour UPDATE et DELETE)
     * Vide pour CREATE
     */
    val recordId: String = "",
    
    /**
     * Timestamp de création de la tâche
     */
    val createdAt: Long = 0,
    
    /**
     * Statut de la tâche (PENDING, IN_PROGRESS, COMPLETED, FAILED)
     */
    val status: String = "PENDING",

    /**
     * Utilisateur qui a créé la tâche : le worker ne l'envoie qu'avec la session de CET utilisateur (SJ7, LD7).
     * Par défaut, l'utilisateur connecté au moment de la création.
     */
    val utilisateurId: String = com.xburnsx.toutiebudget.di.PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: "",

    /** Diagnostic (SJ11) : nombre d'essais, dernière erreur nettoyée (jamais le JSON), code HTTP, date. */
    val tentatives: Int = 0,
    val derniereErreur: String? = null,
    val codeHttp: Int? = null,
    val derniereTentative: Long = 0,

    /**
     * Erreur permanente (400/403/404...) : la tâche n'est plus réessayée automatiquement (SJ10)
     * jusqu'à un « Réessayer » manuel.
     */
    val bloque: Boolean = false
)
