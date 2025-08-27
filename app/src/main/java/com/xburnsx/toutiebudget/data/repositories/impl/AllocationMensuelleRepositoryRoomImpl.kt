package com.xburnsx.toutiebudget.data.repositories.impl

import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.data.room.daos.AllocationMensuelleDao
import com.xburnsx.toutiebudget.data.room.daos.SyncJobDao
import com.xburnsx.toutiebudget.data.room.entities.AllocationMensuelle as AllocationMensuelleEntity
import com.xburnsx.toutiebudget.data.room.entities.SyncJob
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.utils.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * Implémentation Room-first du repository des allocations mensuelles.
 * 
 * LOGIQUE ROOM-FIRST :
 * 1. Toutes les opérations se font d'abord en local (Room)
 * 2. Les modifications sont ajoutées à la liste de tâches (SyncJob)
 * 3. Le Worker synchronise en arrière-plan avec Pocketbase
 * 
 * INTERFACE IDENTIQUE : Même interface que AllocationMensuelleRepository pour compatibilité
 */
class AllocationMensuelleRepositoryRoomImpl(
    private val allocationMensuelleDao: AllocationMensuelleDao,
    private val syncJobDao: SyncJobDao
) : AllocationMensuelleRepository {
    private val client = PocketBaseClient
    private val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
    
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override suspend fun getAllocationById(id: String): AllocationMensuelle? = withContext(Dispatchers.IO) {
        try {
            // Récupérer depuis Room (PRIMARY)
            val allocationEntity = allocationMensuelleDao.getAllocationById(id)
            
            // Convertir l'entité en modèle
            allocationEntity?.toAllocationMensuelleModel()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun mettreAJourAllocation(
        id: String,
        nouveauSolde: Double,
        nouvelleDepense: Double
    ) = withContext(Dispatchers.IO) {
        try {
            // 1. Récupérer l'allocation existante
            val allocationEntity = allocationMensuelleDao.getAllocationById(id)
                ?: return@withContext

            // 2. Mettre à jour les montants
            val nouvelleAllocation = allocationEntity.copy(
                solde = nouveauSolde,
                depense = nouvelleDepense
            )

            // 3. Sauvegarder en Room
            allocationMensuelleDao.updateAllocation(nouvelleAllocation)
            
            // 4. Ajouter à la liste de tâches pour synchronisation
            val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                id = IdGenerator.generateId(),
                type = "ALLOCATION_MENSUELLE",
                action = "UPDATE",
                dataJson = gson.toJson(nouvelleAllocation),
                recordId = nouvelleAllocation.id, // 🆕 CORRECTION : Ajouter l'ID de l'enregistrement
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)
        } catch (e: Exception) {
            // Gérer l'erreur silencieusement
        }
    }

    override suspend fun recupererOuCreerAllocation(enveloppeId: String, mois: Date): AllocationMensuelle = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: throw Exception("Utilisateur non connecté")

            val moisStr = dateFormatter.format(mois)

            // 1. Essayer de récupérer l'allocation existante depuis Room
            val allocationsEntities = allocationMensuelleDao.getAllocationsByUtilisateur(utilisateurId).first()
            val allocationExistante = allocationsEntities.find { entity -> 
                entity.enveloppeId == enveloppeId && entity.mois == moisStr 
            }

            if (allocationExistante != null) {
                // 2. Retourner l'allocation existante
                return@withContext allocationExistante.toAllocationMensuelleModel()
            }

            // 3. Créer une nouvelle allocation
            val nouvelleAllocation = AllocationMensuelle(
                id = "",
                utilisateurId = utilisateurId,
                enveloppeId = enveloppeId,
                mois = mois,
                solde = 0.0,
                alloue = 0.0,
                depense = 0.0,
                compteSourceId = null,
                collectionCompteSource = null
            )

            // 4. Sauvegarder en Room
            val allocationEntity = AllocationMensuelleEntity(
                id = IdGenerator.generateId(),
                utilisateurId = utilisateurId,
                enveloppeId = enveloppeId,
                mois = moisStr,
                solde = 0.0,
                alloue = 0.0,
                depense = 0.0,
                compteSourceId = null,
                collectionCompteSource = null
            )

            allocationMensuelleDao.insertAllocation(allocationEntity)
            
            // 5. Ajouter à la liste de tâches pour synchronisation
            val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                id = IdGenerator.generateId(),
                type = "ALLOCATION_MENSUELLE",
                action = "CREATE",
                dataJson = gson.toJson(allocationEntity),
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            // 6. Retourner la nouvelle allocation
            nouvelleAllocation.copy(id = allocationEntity.id)
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun mettreAJourAllocation(allocation: AllocationMensuelle): Unit = withContext(Dispatchers.IO) {
        try {
            // 1. Convertir le modèle en entité Room
            val allocationEntity = AllocationMensuelleEntity(
                id = allocation.id,
                utilisateurId = allocation.utilisateurId,
                enveloppeId = allocation.enveloppeId,
                mois = dateFormatter.format(allocation.mois),
                solde = allocation.solde,
                alloue = allocation.alloue,
                depense = allocation.depense,
                compteSourceId = allocation.compteSourceId,
                collectionCompteSource = allocation.collectionCompteSource
            )

            // 2. Mettre à jour en Room (PRIMARY)
            allocationMensuelleDao.updateAllocation(allocationEntity)
            
            // 3. Ajouter à la liste de tâches pour synchronisation
            val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                id = IdGenerator.generateId(),
                type = "ALLOCATION_MENSUELLE",
                action = "UPDATE",
                dataJson = gson.toJson(allocationEntity),
                recordId = allocationEntity.id, // 🆕 CORRECTION : Ajouter l'ID de l'enregistrement
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun mettreAJourCompteSource(
        id: String,
        compteSourceId: String,
        collectionCompteSource: String
    ) = withContext(Dispatchers.IO) {
        try {
            // 1. Récupérer l'allocation existante
            val allocationEntity = allocationMensuelleDao.getAllocationById(id)
                ?: return@withContext

            // 2. Mettre à jour le compte source
            val nouvelleAllocation = allocationEntity.copy(
                compteSourceId = compteSourceId,
                collectionCompteSource = collectionCompteSource
            )

            // 3. Sauvegarder en Room
            allocationMensuelleDao.updateAllocation(nouvelleAllocation)
            
            // 4. Ajouter à la liste de tâches pour synchronisation
            val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                id = IdGenerator.generateId(),
                type = "ALLOCATION_MENSUELLE",
                action = "UPDATE",
                dataJson = gson.toJson(nouvelleAllocation),
                recordId = nouvelleAllocation.id, // 🆕 CORRECTION : Ajouter l'ID de l'enregistrement
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)
        } catch (e: Exception) {
            // Gérer l'erreur silencieusement
        }
    }

    override suspend fun creerNouvelleAllocation(allocation: AllocationMensuelle): AllocationMensuelle = withContext(Dispatchers.IO) {
        try {
            // 1. Convertir le modèle en entité Room
            val allocationEntity = AllocationMensuelleEntity(
                id = allocation.id.ifBlank { IdGenerator.generateId() },
                utilisateurId = allocation.utilisateurId.ifBlank { 
                    client.obtenirUtilisateurConnecte()?.id ?: "" 
                },
                enveloppeId = allocation.enveloppeId,
                mois = dateFormatter.format(allocation.mois),
                solde = allocation.solde,
                alloue = allocation.alloue,
                depense = allocation.depense,
                compteSourceId = allocation.compteSourceId,
                collectionCompteSource = allocation.collectionCompteSource
            )

            // 2. Sauvegarder en Room (PRIMARY)
            val id = allocationMensuelleDao.insertAllocation(allocationEntity)
            
            // 3. Ajouter à la liste de tâches pour synchronisation
            val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                id = IdGenerator.generateId(),
                type = "ALLOCATION_MENSUELLE",
                action = "CREATE",
                dataJson = gson.toJson(allocationEntity),
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            // 4. Retourner l'allocation créée
            allocation.copy(id = allocationEntity.id)
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun recupererAllocationsEnveloppe(enveloppeId: String): List<AllocationMensuelle> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext emptyList()

            // Récupérer depuis Room (PRIMARY)
            val allocationsEntities = allocationMensuelleDao.getAllocationsByUtilisateur(utilisateurId).first()
            
            // Filtrer par enveloppe et convertir
            val allocations = allocationsEntities
                .filter { entity -> entity.enveloppeId == enveloppeId }
                .map { entity -> entity.toAllocationMensuelleModel() }
            
            allocations
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Extension function pour convertir une entité Room en modèle AllocationMensuelle
     */
    private fun AllocationMensuelleEntity.toAllocationMensuelleModel(): AllocationMensuelle {
        return AllocationMensuelle(
            id = id,
            utilisateurId = utilisateurId,
            enveloppeId = enveloppeId,
            mois = try { dateFormatter.parse(mois) ?: Date() } catch (e: Exception) { Date() },
            solde = solde,
            alloue = alloue,
            depense = depense,
            compteSourceId = compteSourceId,
            collectionCompteSource = collectionCompteSource
        )
    }
}
