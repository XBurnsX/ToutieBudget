package com.xburnsx.toutiebudget.data.repositories.impl

import com.xburnsx.toutiebudget.data.modeles.Tiers
import com.xburnsx.toutiebudget.data.repositories.TiersRepository
import com.xburnsx.toutiebudget.data.room.ToutieBudgetDatabase
import com.xburnsx.toutiebudget.data.room.entities.Tiers as TiersEntity
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Implémentation Room-first du repository des tiers.
 * 
 * LOGIQUE ROOM-FIRST :
 * 1. Toutes les opérations se font d'abord en local (Room)
 * 2. Les modifications sont ajoutées à la liste de tâches (SyncJob)
 * 3. Le Worker synchronise en arrière-plan avec Pocketbase
 * 
 * INTERFACE IDENTIQUE : Même interface que TiersRepository pour compatibilité
 */
class TiersRepositoryRoomImpl(
    private val database: ToutieBudgetDatabase
) : TiersRepository {
    
    private val tiersDao = database.tiersDao()
    private val syncJobDao = database.syncJobDao()
    private val client = PocketBaseClient

    override suspend fun recupererTousLesTiers(): Result<List<Tiers>> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.success(emptyList())

            // Récupérer depuis Room (PRIMARY)
            val tiersEntities = tiersDao.getTiersByUtilisateur(utilisateurId).first()
            
            // Convertir les entités en modèles
            val tiers = tiersEntities.map { entity ->
                entity.toTiersModel()
            }
            
            Result.success(tiers)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun creerTiers(tiers: Tiers): Result<Tiers> = withContext(Dispatchers.IO) {
        try {
            // 1. Convertir le modèle en entité Room
            val tiersEntity = TiersEntity(
                id = tiers.id.ifBlank { UUID.randomUUID().toString() },
                utilisateurId = tiers.utilisateur_id.ifBlank { 
                    client.obtenirUtilisateurConnecte()?.id ?: "" 
                },
                nom = tiers.nom,
                created = tiers.created,
                updated = tiers.updated,
                collectionId = tiers.collectionId,
                collectionName = tiers.collectionName
            )

            // 2. Sauvegarder en Room (PRIMARY)
            val id = tiersDao.insertTiers(tiersEntity)
            
            // 3. Ajouter à la liste de tâches pour synchronisation
            val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                id = UUID.randomUUID().toString(),
                type = "TIERS",
                action = "CREATE",
                dataJson = com.google.gson.Gson().toJson(tiersEntity),
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            // 4. Retourner le succès immédiatement (offline-first)
            Result.success(tiers.copy(id = tiersEntity.id))
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun rechercherTiersParNom(recherche: String): Result<List<Tiers>> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.success(emptyList())

            // Récupérer depuis Room (PRIMARY)
            val tiersEntities = tiersDao.getTiersByUtilisateur(utilisateurId).first()
            
            // Filtrer par nom (insensible à la casse) et convertir
            val tiers = tiersEntities
                .filter { entity -> entity.nom.contains(recherche, ignoreCase = true) }
                .map { entity -> entity.toTiersModel() }
            
            Result.success(tiers)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mettreAJourTiers(tiers: Tiers): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Convertir le modèle en entité Room
            val tiersEntity = TiersEntity(
                id = tiers.id,
                utilisateurId = tiers.utilisateur_id,
                nom = tiers.nom,
                created = tiers.created,
                updated = tiers.updated,
                collectionId = tiers.collectionId,
                collectionName = tiers.collectionName
            )

            // 2. Mettre à jour en Room (PRIMARY)
            tiersDao.updateTiers(tiersEntity)
            
            // 3. Ajouter à la liste de tâches pour synchronisation
            val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                id = UUID.randomUUID().toString(),
                type = "TIERS",
                action = "UPDATE",
                dataJson = com.google.gson.Gson().toJson(tiersEntity),
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            // 4. Retourner le succès immédiatement (offline-first)
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun supprimerTiers(tiersId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Supprimer de Room (PRIMARY)
            tiersDao.deleteTiersById(tiersId)
            
            // 2. Ajouter à la liste de tâches pour synchronisation
            val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                id = UUID.randomUUID().toString(),
                type = "TIERS",
                action = "DELETE",
                dataJson = com.google.gson.Gson().toJson(mapOf("id" to tiersId)),
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            // 3. Retourner le succès immédiatement (offline-first)
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extension function pour convertir une entité Room en modèle Tiers
     */
    private fun TiersEntity.toTiersModel(): Tiers {
        return Tiers(
            id = id,
            nom = nom,
            utilisateur_id = utilisateurId,
            created = created,
            updated = updated,
            collectionId = collectionId,
            collectionName = collectionName
        )
    }
}
