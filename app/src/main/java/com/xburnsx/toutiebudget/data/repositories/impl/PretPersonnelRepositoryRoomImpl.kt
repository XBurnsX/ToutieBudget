package com.xburnsx.toutiebudget.data.repositories.impl

import com.xburnsx.toutiebudget.data.modeles.PretPersonnel
import com.xburnsx.toutiebudget.data.repositories.PretPersonnelRepository
import com.xburnsx.toutiebudget.data.room.ToutieBudgetDatabase
import com.xburnsx.toutiebudget.data.room.entities.PretPersonnel as PretPersonnelEntity
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Implémentation Room-first du repository des prêts personnels.
 * 
 * LOGIQUE ROOM-FIRST :
 * 1. Toutes les opérations se font d'abord en local (Room)
 * 2. Les modifications sont ajoutées à la liste de tâches (SyncJob)
 * 3. Le Worker synchronise en arrière-plan avec Pocketbase
 * 
 * INTERFACE IDENTIQUE : Même interface que PretPersonnelRepository pour compatibilité
 */
class PretPersonnelRepositoryRoomImpl(
    private val database: ToutieBudgetDatabase
) : PretPersonnelRepository {
    
    private val pretPersonnelDao = database.pretPersonnelDao()
    private val syncJobDao = database.syncJobDao()
    private val client = PocketBaseClient

    override suspend fun lister(): Result<List<PretPersonnel>> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.success(emptyList())

            // Récupérer depuis Room (PRIMARY)
            val pretsEntities = pretPersonnelDao.getPretsByUtilisateur(utilisateurId).first()
            
            // Convertir les entités en modèles
            val prets = pretsEntities.map { entity ->
                entity.toPretPersonnelModel()
            }
            
            Result.success(prets)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun creer(pret: PretPersonnel): Result<PretPersonnel> = withContext(Dispatchers.IO) {
        try {
            // 1. Convertir le modèle en entité Room
            val pretEntity = PretPersonnelEntity(
                id = pret.id.ifBlank { UUID.randomUUID().toString() },
                utilisateurId = pret.utilisateurId.ifBlank { 
                    client.obtenirUtilisateurConnecte()?.id ?: "" 
                },
                nomTiers = pret.nomTiers,
                montantInitial = pret.montantInitial,
                solde = pret.solde,
                type = pret.type.name.lowercase(),
                estArchive = pret.estArchive,
                dateCreation = pret.dateCreation,
                created = pret.created,
                updated = pret.updated
            )

            // 2. Sauvegarder en Room (PRIMARY)
            val id = pretPersonnelDao.insertPret(pretEntity)
            
            // 3. Ajouter à la liste de tâches pour synchronisation
            val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                id = UUID.randomUUID().toString(),
                type = "PRET_PERSONNEL",
                action = "CREATE",
                dataJson = com.google.gson.Gson().toJson(pretEntity),
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            // 4. Retourner le succès immédiatement (offline-first)
            Result.success(pret.copy(id = pretEntity.id))
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mettreAJour(pret: PretPersonnel): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Convertir le modèle en entité Room
            val pretEntity = PretPersonnelEntity(
                id = pret.id,
                utilisateurId = pret.utilisateurId,
                nomTiers = pret.nomTiers,
                montantInitial = pret.montantInitial,
                solde = pret.solde,
                type = pret.type.name.lowercase(),
                estArchive = pret.estArchive,
                dateCreation = pret.dateCreation,
                created = pret.created,
                updated = pret.updated
            )

            // 2. Mettre à jour en Room (PRIMARY)
            pretPersonnelDao.updatePret(pretEntity)
            
            // 3. Ajouter à la liste de tâches pour synchronisation
            val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                id = UUID.randomUUID().toString(),
                type = "PRET_PERSONNEL",
                action = "UPDATE",
                dataJson = com.google.gson.Gson().toJson(pretEntity),
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

    override suspend fun supprimer(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Supprimer de Room (PRIMARY)
            pretPersonnelDao.deletePretById(id)
            
            // 2. Ajouter à la liste de tâches pour synchronisation
            val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                id = UUID.randomUUID().toString(),
                type = "PRET_PERSONNEL",
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
     * Extension function pour convertir une entité Room en modèle PretPersonnel
     */
    private fun PretPersonnelEntity.toPretPersonnelModel(): PretPersonnel {
        return PretPersonnel(
            id = id,
            utilisateurId = utilisateurId,
            nomTiers = nomTiers,
            montantInitial = montantInitial,
            solde = solde,
            type = when (type.lowercase()) {
                "pret" -> com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel.PRET
                "dette" -> com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel.DETTE
                else -> com.xburnsx.toutiebudget.data.modeles.TypePretPersonnel.PRET
            },
            estArchive = estArchive,
            dateCreation = dateCreation,
            created = created,
            updated = updated
        )
    }
}
