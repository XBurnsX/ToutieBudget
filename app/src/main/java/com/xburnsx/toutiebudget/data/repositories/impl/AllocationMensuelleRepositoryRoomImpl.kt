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
import java.util.Calendar
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
            
            // 🔍 DEBUG - recupererOuCreerAllocation appelé avec:
            // 🔍 DEBUG - enveloppeId: $enveloppeId
            // 🔍 DEBUG - mois demandé: $moisStr
            // 🔍 DEBUG - mois Date object: $mois

            // 1. 🔥 FUSION RÉELLE : Récupérer TOUTES les allocations pour cette enveloppe et ce mois
            val allocationsEntities = allocationMensuelleDao.getAllocationsByUtilisateur(utilisateurId).first()
            
            // 🔍 DEBUG - Toutes les allocations de l'utilisateur: ${allocationsEntities.size}
            allocationsEntities.filter { it.enveloppeId == enveloppeId }.forEach { entity ->
                // 🔍 DEBUG - Allocation trouvée pour cette enveloppe: id=${entity.id}, mois=${entity.mois}, solde=${entity.solde}, alloue=${entity.alloue}
            }
            
            // 🔥 CORRECTION : Fusionner par MOIS complet, pas par date exacte !
            val moisCalendrier = Calendar.getInstance().apply { time = mois }
            val annee = moisCalendrier.get(Calendar.YEAR)
            val moisNumero = moisCalendrier.get(Calendar.MONTH)
            
            // 🔍 DEBUG - Recherche pour année: $annee, mois: $moisNumero
            
            val allocationsPourEnveloppeEtMois = allocationsEntities.filter { entity -> 
                try {
                    val dateEntity = dateFormatter.parse(entity.mois)
                    val calendrierEntity = Calendar.getInstance().apply { time = dateEntity }
                    val anneeEntity = calendrierEntity.get(Calendar.YEAR)
                    val moisEntity = calendrierEntity.get(Calendar.MONTH)
                    
                    val match = entity.enveloppeId == enveloppeId && 
                    anneeEntity == annee && 
                    moisEntity == moisNumero
                    
                    if (entity.enveloppeId == enveloppeId) {
                        // 🔍 DEBUG - Vérification allocation: enveloppeId=${entity.enveloppeId}, moisEntity=${entity.mois} -> anneeEntity=$anneeEntity, moisEntity=$moisEntity, match=$match
                    }
                    
                    match
                } catch (e: Exception) {
                    // Fallback : comparaison exacte si parsing échoue
                    val match = entity.enveloppeId == enveloppeId && entity.mois == moisStr
                    if (entity.enveloppeId == enveloppeId) {
                        // 🔍 DEBUG - Fallback parsing: enveloppeId=${entity.enveloppeId}, moisEntity=${entity.mois}, match=$match
                    }
                    match
                }
            }
            
            // 🔍 DEBUG - Allocations trouvées pour ce mois: ${allocationsPourEnveloppeEtMois.size}

            when {
                // Cas 1: Aucune allocation trouvée -> Créer une nouvelle
                allocationsPourEnveloppeEtMois.isEmpty() -> {
                    // 🔍 DEBUG - Aucune allocation trouvée, création d'une nouvelle
                    // 🔥 CORRECTION : Vérifier s'il y a déjà une allocation pour ce mois (peu importe la date exacte)
                    val allocationsPourEnveloppeEtMoisComplet = allocationsEntities.filter { entity -> 
                        try {
                            val dateEntity = dateFormatter.parse(entity.mois)
                            val calendrierEntity = Calendar.getInstance().apply { time = dateEntity }
                            val anneeEntity = calendrierEntity.get(Calendar.YEAR)
                            val moisEntity = calendrierEntity.get(Calendar.MONTH)
                            
                            entity.enveloppeId == enveloppeId && 
                            anneeEntity == annee && 
                            moisEntity == moisNumero
                        } catch (e: Exception) {
                            false
                        }
                    }
                    
                    if (allocationsPourEnveloppeEtMoisComplet.isNotEmpty()) {
                        // 🔥 CORRECTION : Il y a déjà une allocation pour ce mois, la retourner au lieu d'en créer une nouvelle
                        // 🔍 DEBUG - Allocation existante trouvée pour ce mois, pas de création de doublon
                        allocationsPourEnveloppeEtMoisComplet.first().toAllocationMensuelleModel()
                    } else {
                        // Vraiment aucune allocation pour ce mois, créer une nouvelle
                        // 🔍 DEBUG - Création d'une nouvelle allocation
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
                        
                        val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                            id = IdGenerator.generateId(),
                            type = "ALLOCATION_MENSUELLE",
                            action = "CREATE",
                            dataJson = gson.toJson(allocationEntity),
                            recordId = allocationEntity.id,
                            createdAt = System.currentTimeMillis(),
                            status = "PENDING"
                        )
                        syncJobDao.insertSyncJob(syncJob)

                        nouvelleAllocation.copy(id = allocationEntity.id)
                    }
                }
                
                // Cas 2: Une seule allocation -> La retourner directement
                allocationsPourEnveloppeEtMois.size == 1 -> {
                    allocationsPourEnveloppeEtMois.first().toAllocationMensuelleModel()
                }
                
                // A2 — Plusieurs lignes de même mois : ne PAS fusionner puis effacer
                // des lignes qui peuvent être référencées par des transactions.
                // Ne PAS attribuer au compte « dominant » les fonds d'un autre.
                else -> {
                    val actives = allocationsPourEnveloppeEtMois.filter {
                        kotlin.math.round(it.solde * 100.0).toLong() != 0L ||
                        kotlin.math.round(it.alloue * 100.0).toLong() != 0L ||
                        kotlin.math.round(it.depense * 100.0).toLong() != 0L
                    }
                    when (actives.size) {
                        0 -> allocationsPourEnveloppeEtMois.first().toAllocationMensuelleModel()
                        1 -> actives.single().toAllocationMensuelleModel()
                        else -> throw IllegalStateException(
                            "Plusieurs allocations financières existent pour cette enveloppe et ce mois. " +
                            "La répartition est bloquée pour protéger la provenance et l'historique."
                        )
                    }
                }
            }
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
                recordId = allocationEntity.id,
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

    /**
     * 🔥 FUSION RÉELLE : Fusionne plusieurs allocations en une seule
     */
    private suspend fun fusionnerAllocations(
        allocations: List<AllocationMensuelleEntity>,
        mois: Date
    ): AllocationMensuelle = withContext(Dispatchers.IO) {
        
        // 1. Calculer les totaux à fusionner
        val soldeTotal = allocations.sumOf { it.solde }
        val alloueTotal = allocations.sumOf { it.alloue }
        val depenseTotal = allocations.sumOf { it.depense }
        
        // 🔥 FUSION RÉELLE - Totaux calculés: solde=$soldeTotal, alloue=$alloueTotal, depense=$depenseTotal
        
        // 2. Choisir une allocation CANONIQUE à conserver (garder l'ID pour ne PAS casser les références)
        val allocationCanonique = allocations.first()
        
        // 3. Déterminer la provenance finale (compte source et collection)
        val allocationDominante = allocations
            .filter { it.solde > 0.0 }
            .maxByOrNull { it.solde }
        val compteSourceFinal = if (soldeTotal < 0.01) null else allocationDominante?.compteSourceId
        val collectionCompteSourceFinal = if (soldeTotal < 0.01) null else allocationDominante?.collectionCompteSource
        
        // 4. Construire l'objet fusionné avec le MÊME ID (celui de l'allocation canonique)
        val allocationFusionnee = allocationCanonique.copy(
            solde = soldeTotal,
            alloue = alloueTotal,
            depense = depenseTotal,
            compteSourceId = compteSourceFinal,
            collectionCompteSource = collectionCompteSourceFinal
        )
        
        // 5. Mettre à jour l'allocation canonique avec les totaux
        allocationMensuelleDao.updateAllocation(allocationFusionnee)
        
        // 6. Ajouter à la liste de tâches pour synchronisation
        val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
            id = IdGenerator.generateId(),
            type = "ALLOCATION_MENSUELLE",
            action = "UPDATE",
            dataJson = gson.toJson(allocationFusionnee),
            recordId = allocationFusionnee.id,
            createdAt = System.currentTimeMillis(),
            status = "PENDING"
        )
        syncJobDao.insertSyncJob(syncJob)
        
        // 🔥 FUSION RÉELLE - Allocation fusionnée créée avec ID: ${allocationFusionnee.id}
        
        // 7. Retourner le modèle fusionné
        allocationFusionnee.toAllocationMensuelleModel()
    }
    
    /**
     * 🔥 NETTOYAGE : Supprime les allocations doublons après fusion
     */
    private suspend fun nettoyerDoublonsAllocations(
        allocations: List<AllocationMensuelleEntity>,
        idAllocationConservee: String
    ) = withContext(Dispatchers.IO) {
        
        // Supprimer toutes les allocations sauf celle qu'on garde
        val allocationsASupprimer = allocations.filter { it.id != idAllocationConservee }
        
        // 🔥 NETTOYAGE - Suppression de ${allocationsASupprimer.size} allocations doublons
        
        allocationsASupprimer.forEach { allocation ->
            try {
                // 1. Supprimer de Room
                allocationMensuelleDao.deleteAllocation(allocation)
                
                // 2. Ajouter à la liste de tâches pour synchronisation (DELETE)
                val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                    id = IdGenerator.generateId(),
                    type = "ALLOCATION_MENSUELLE",
                    action = "DELETE",
                    dataJson = gson.toJson(allocation),
                    recordId = allocation.id,
                    createdAt = System.currentTimeMillis(),
                    status = "PENDING"
                )
                syncJobDao.insertSyncJob(syncJob)
                
                // 🔥 NETTOYAGE - Allocation ${allocation.id} supprimée et marquée pour synchronisation
                
            } catch (e: Exception) {
                // ⚠️ Erreur lors de la suppression de l'allocation ${allocation.id}: ${e.message}
            }
        }
    }
}
