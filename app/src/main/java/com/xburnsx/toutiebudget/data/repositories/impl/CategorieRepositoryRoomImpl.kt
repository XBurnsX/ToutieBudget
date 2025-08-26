package com.xburnsx.toutiebudget.data.repositories.impl

import com.xburnsx.toutiebudget.data.modeles.Categorie
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.data.room.ToutieBudgetDatabase
import com.xburnsx.toutiebudget.data.room.entities.Categorie as CategorieEntity
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Implémentation Room-first du repository des catégories.
 * 
 * LOGIQUE ROOM-FIRST :
 * 1. Toutes les opérations se font d'abord en local (Room)
 * 2. Les modifications sont ajoutées à la liste de tâches (SyncJob)
 * 3. Le Worker synchronise en arrière-plan avec Pocketbase
 * 
 * INTERFACE IDENTIQUE : Même interface que CategorieRepository pour compatibilité
 */
class CategorieRepositoryRoomImpl(
    private val database: ToutieBudgetDatabase
) : CategorieRepository {
    
    private val categorieDao = database.categorieDao()
    private val syncJobDao = database.syncJobDao()
    private val client = PocketBaseClient

    override suspend fun recupererToutesLesCategories(): Result<List<Categorie>> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.success(emptyList())

            // Récupérer depuis Room (PRIMARY)
            val categoriesEntities = categorieDao.getCategoriesByUtilisateur(utilisateurId).first()
            
            // Convertir les entités en modèles
            val categories = categoriesEntities.map { entity ->
                entity.toCategorieModel()
            }
            
            Result.success(categories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun creerCategorie(categorie: Categorie): Result<Categorie> = withContext(Dispatchers.IO) {
        try {
            // 1. Convertir le modèle en entité Room
            val categorieEntity = CategorieEntity(
                id = categorie.id.ifBlank { UUID.randomUUID().toString() },
                utilisateurId = categorie.utilisateurId.ifBlank { 
                    client.obtenirUtilisateurConnecte()?.id ?: "" 
                },
                nom = categorie.nom,
                ordre = categorie.ordre
            )

            // 2. Sauvegarder en Room (PRIMARY)
            val id = categorieDao.insertCategorie(categorieEntity)
            
            // 3. Ajouter à la liste de tâches pour synchronisation
            val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                id = UUID.randomUUID().toString(),
                type = "CATEGORIE",
                action = "CREATE",
                dataJson = com.google.gson.Gson().toJson(categorieEntity),
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            // 4. Retourner le succès immédiatement (offline-first)
            Result.success(categorie.copy(id = categorieEntity.id))
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mettreAJourCategorie(categorie: Categorie): Result<Categorie> = withContext(Dispatchers.IO) {
        try {
            // 1. Convertir le modèle en entité Room
            val categorieEntity = CategorieEntity(
                id = categorie.id,
                utilisateurId = categorie.utilisateurId,
                nom = categorie.nom,
                ordre = categorie.ordre
            )

            // 2. Mettre à jour en Room (PRIMARY)
            categorieDao.updateCategorie(categorieEntity)
            
            // 3. Ajouter à la liste de tâches pour synchronisation
            val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                id = UUID.randomUUID().toString(),
                type = "CATEGORIE",
                action = "UPDATE",
                dataJson = com.google.gson.Gson().toJson(categorieEntity),
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            // 4. Retourner le succès immédiatement (offline-first)
            Result.success(categorie)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun supprimerCategorie(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Supprimer de Room (PRIMARY)
            categorieDao.deleteCategorieById(id)
            
            // 2. Ajouter à la liste de tâches pour synchronisation
            val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                id = UUID.randomUUID().toString(),
                type = "CATEGORIE",
                action = "DELETE",
                dataJson = com.google.gson.Gson().toJson(mapOf("id" to id)),
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
     * Extension function pour convertir une entité Room en modèle Categorie
     */
    private fun CategorieEntity.toCategorieModel(): Categorie {
        return Categorie(
            id = id,
            utilisateurId = utilisateurId,
            nom = nom,
            ordre = ordre
        )
    }
}
