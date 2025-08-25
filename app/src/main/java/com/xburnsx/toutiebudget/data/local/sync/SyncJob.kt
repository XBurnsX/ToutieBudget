package com.xburnsx.toutiebudget.data.local.sync

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class OperationType { CREATE, UPDATE, DELETE }
enum class EntityType { TRANSACTION, COMPTE, CATEGORIE, ENVELOPPE, TIERS, ALLOCATION, CARTE_CREDIT, PRET_PERSONNEL }

@Entity(tableName = "sync_jobs")
data class SyncJob(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entityId: String,
    val entityType: EntityType,
    val operationType: OperationType,
    val payload: String? = null // Données en format JSON pour CREATE et UPDATE
)
