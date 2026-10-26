package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.room.daos.EnveloppeDao
import com.xburnsx.toutiebudget.data.room.daos.AllocationMensuelleDao
import com.xburnsx.toutiebudget.data.room.daos.SyncJobDao
import com.xburnsx.toutiebudget.data.room.entities.Enveloppe as EnveloppeEntity
import com.xburnsx.toutiebudget.data.room.entities.AllocationMensuelle as AllocationMensuelleEntity
import com.xburnsx.toutiebudget.data.room.entities.SyncJob
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.utils.IdGenerator
import com.xburnsx.toutiebudget.data.services.SyncJobAutoTriggerService
import com.xburnsx.toutiebudget.data.services.SyncPayloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
// import android.util.Log

/**
 * Implémentation Room-first du repository des enveloppes.
 * 
 * LOGIQUE ROOM-FIRST :
 * 1. Toutes les opérations se font d'abord en local (Room)
 * 2. Les modifications sont ajoutées à la liste de tâches (SyncJob)
 * 3. Le Worker synchronise en arrière-plan avec Pocketbase
 * 
 * INTERFACE IDENTIQUE : Même interface que EnveloppeRepository pour compatibilité
 */
class EnveloppeRepositoryRoomImpl(
    private val enveloppeDao: EnveloppeDao,
    private val allocationMensuelleDao: AllocationMensuelleDao,
    private val syncJobDao: SyncJobDao
) : EnveloppeRepository {
    private val client = PocketBaseClient
    
    private val gson: Gson = GsonBuilder()
        .create() // 🎯 CORRECTION : Supprimer la politique snake_case pour éviter les conflits avec @ColumnInfo
    
    // Format JSON PocketBase : source unique dans SyncPayloads (partagé avec la gestion de la base)
    private fun genererJsonEnveloppeManuel(entity: EnveloppeEntity): String = SyncPayloads.jsonPour(entity)

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override suspend fun recupererToutesLesEnveloppes(): Result<List<Enveloppe>> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.success(emptyList())

            // Récupérer depuis Room (PRIMARY)
            val enveloppesEntities = enveloppeDao.getEnveloppesByUtilisateur(utilisateurId).first()
            
            // Convertir les entités en modèles
            val enveloppes = enveloppesEntities.map { entity ->
                entity.toEnveloppeModel()
            }
            
            Result.success(enveloppes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recupererAllocationsPourMois(mois: Date): Result<List<AllocationMensuelle>> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.success(emptyList())

            val moisStr = dateFormatter.format(mois)

            // 🔍 LOGS DEBUG : Vérifier les dates
            // DEBUG: recupererAllocationsPourMois - mois demandé = $mois
            // DEBUG: recupererAllocationsPourMois - moisStr formaté = $moisStr

            // Récupérer depuis Room (PRIMARY)
            val allocationsEntities = allocationMensuelleDao.getAllocationsByUtilisateur(utilisateurId).first()
            
            // 🔍 LOGS DEBUG : Vérifier les allocations trouvées
            // DEBUG: recupererAllocationsPourMois - nombre d'allocations trouvées = ${allocationsEntities.size}
            allocationsEntities.forEach { entity ->
                // DEBUG: recupererAllocationsPourMois - allocation ${entity.id} - mois = ${entity.mois}
            }
            
            // Filtrer par mois et convertir (seulement mois et année, pas la date complète)
            val allocations = allocationsEntities
                .filter { entity -> 
                    // Extraire seulement le mois et l'année de entity.mois (format: 2025-08-01 00:00:00)
                    val entityMoisAnnee = entity.mois.substring(0, 7) // "2025-08"
                    val moisAnneeDemande = moisStr.substring(0, 7)    // "2025-08"
                    entityMoisAnnee == moisAnneeDemande
                }
                .map { entity -> entity.toAllocationMensuelleModel() }
            
            // 🔍 LOGS DEBUG : Vérifier le filtrage
            // DEBUG: recupererAllocationsPourMois - allocations après filtrage = ${allocations.size}
            
            Result.success(allocations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mettreAJourAllocation(allocation: AllocationMensuelle): Result<Unit> = withContext(Dispatchers.IO) {
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
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = "ALLOCATION_MENSUELLE",
                action = "UPDATE",
                dataJson = gson.toJson(allocationEntity),
                recordId = allocationEntity.id, // 🆕 CORRECTION : Ajouter l'ID de l'enregistrement
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

    override suspend fun creerEnveloppe(enveloppe: Enveloppe): Result<Enveloppe> = withContext(Dispatchers.IO) {
        try {
            // 1. Convertir le modèle en entité Room
            val enveloppeEntity = EnveloppeEntity(
                id = enveloppe.id.ifBlank { IdGenerator.generateId() },
                utilisateurId = enveloppe.utilisateurId.ifBlank { 
                    client.obtenirUtilisateurConnecte()?.id ?: "" 
                },
                nom = enveloppe.nom,
                categorieId = enveloppe.categorieId,
                estArchive = enveloppe.estArchive,
                ordre = enveloppe.ordre,
                typeObjectif = enveloppe.typeObjectif,
                objectifMontant = enveloppe.objectifMontant,
                dateObjectif = enveloppe.dateObjectif?.let { dateFormatter.format(it) },
                dateDebutObjectif = enveloppe.dateDebutObjectif?.let { dateFormatter.format(it) },
                objectifJour = enveloppe.objectifJour,
                resetApresEcheance = enveloppe.resetApresEcheance
            )

            // 2. Sauvegarder en Room (PRIMARY)
            val id = enveloppeDao.insertEnveloppe(enveloppeEntity)
            
            // 3. Ajouter à la liste de tâches pour synchronisation
            // 🎯 CORRECTION : Utiliser la génération manuelle du JSON pour les bons noms de champs !
            val dataJson = genererJsonEnveloppeManuel(enveloppeEntity)
            // 🚨 ENVELOPPE CREATE - JSON MANUEL GÉNÉRÉ:
            //   $dataJson
            
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = "ENVELOPPE",
                action = "CREATE",
                dataJson = dataJson,
                recordId = enveloppeEntity.id,
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            // 🚀 DÉCLENCHER IMMÉDIATEMENT LA SYNCHRONISATION !
            com.xburnsx.toutiebudget.data.services.SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()

            // 4. Retourner le succès immédiatement (offline-first)
            Result.success(enveloppe.copy(id = enveloppeEntity.id))
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mettreAJourEnveloppe(enveloppe: Enveloppe): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Convertir le modèle en entité Room
            val enveloppeEntity = EnveloppeEntity(
                id = enveloppe.id,
                utilisateurId = enveloppe.utilisateurId,
                nom = enveloppe.nom,
                categorieId = enveloppe.categorieId,
                estArchive = enveloppe.estArchive,
                ordre = enveloppe.ordre,
                typeObjectif = enveloppe.typeObjectif,
                objectifMontant = enveloppe.objectifMontant,
                dateObjectif = enveloppe.dateObjectif?.let { dateFormatter.format(it) },
                dateDebutObjectif = enveloppe.dateDebutObjectif?.let { dateFormatter.format(it) },
                objectifJour = enveloppe.objectifJour,
                resetApresEcheance = enveloppe.resetApresEcheance
            )

            // 2. Mettre à jour en Room (PRIMARY)
            enveloppeDao.updateEnveloppe(enveloppeEntity)
            
            // 3. Ajouter à la liste de tâches pour synchronisation
            // 🎯 CORRECTION : Utiliser la génération manuelle du JSON pour les bons noms de champs !
            val dataJson = genererJsonEnveloppeManuel(enveloppeEntity)
            // 🚨 ENVELOPPE UPDATE - JSON MANUEL GÉNÉRÉ:
            //   $dataJson
            
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = "ENVELOPPE",
                action = "UPDATE",
                dataJson = dataJson,
                recordId = enveloppeEntity.id, // 🆕 CORRECTION : Ajouter l'ID de l'enregistrement
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

    override suspend fun supprimerEnveloppe(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Supprimer de Room (PRIMARY)
            enveloppeDao.deleteEnveloppeById(id)
            
            // 2. Ajouter à la liste de tâches pour synchronisation
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = "ENVELOPPE",
                action = "DELETE",
                dataJson = gson.toJson(mapOf("id" to id)),
                recordId = id, // 🆕 CORRECTION : Ajouter l'ID de l'enregistrement à supprimer
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

    override suspend fun ajouterDepenseAllocation(allocationMensuelleId: String, montantDepense: Double): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 🔍 DEBUG - ajouterDepenseAllocation appelé avec:
            // 🔍 DEBUG - allocationMensuelleId: $allocationMensuelleId
            // 🔍 DEBUG - montantDepense: $montantDepense

            // 1. Récupérer l'allocation depuis Room
            val allocationEntity = allocationMensuelleDao.getAllocationById(allocationMensuelleId)
                ?: return@withContext Result.failure(Exception("Allocation non trouvée"))

            // 🔍 DEBUG - Allocation trouvée en Room:
            // 🔍 DEBUG - ID: ${allocationEntity.id}
            // 🔍 DEBUG - EnveloppeId: ${allocationEntity.enveloppeId}
            // 🔍 DEBUG - Ancien solde: ${allocationEntity.solde}
            // 🔍 DEBUG - Ancienne depense: ${allocationEntity.depense}

            // 2. Mettre à jour les montants
            val nouvelleAllocation = allocationEntity.copy(
                solde = allocationEntity.solde - montantDepense,
                depense = allocationEntity.depense + montantDepense
            )

            // 🔍 DEBUG - Nouvelle allocation calculée:
            // 🔍 DEBUG - Nouveau solde: ${nouvelleAllocation.solde}
            // 🔍 DEBUG - Nouvelle depense: ${nouvelleAllocation.depense}

            // 3. Sauvegarder en Room
            allocationMensuelleDao.updateAllocation(nouvelleAllocation)
            // 🔍 DEBUG - Allocation mise à jour en Room avec succès
            
            // 4. Ajouter à la liste de tâches pour synchronisation
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = "ALLOCATION_MENSUELLE",
                action = "UPDATE",
                dataJson = gson.toJson(nouvelleAllocation),
                recordId = nouvelleAllocation.id, // 🆕 CORRECTION : Ajouter l'ID de l'enregistrement
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)
            // 🔍 DEBUG - SyncJob créé pour la synchronisation

            // 5. Retourner le succès immédiatement (offline-first)
            Result.success(Unit)
            
        } catch (e: Exception) {
            // 🔍 DEBUG - Erreur dans ajouterDepenseAllocation: ${e.message}
            Result.failure(e)
        }
    }

    override suspend fun annulerDepenseAllocation(allocationMensuelleId: String, montantDepense: Double): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Récupérer l'allocation depuis Room
            val allocationEntity = allocationMensuelleDao.getAllocationById(allocationMensuelleId)
                ?: return@withContext Result.failure(Exception("Allocation non trouvée"))

            // 2. Mettre à jour les montants
            val nouvelleAllocation = allocationEntity.copy(
                solde = allocationEntity.solde + montantDepense,
                depense = allocationEntity.depense - montantDepense
            )

            // 3. Sauvegarder en Room
            allocationMensuelleDao.updateAllocation(nouvelleAllocation)
            
            // 4. Ajouter à la liste de tâches pour synchronisation
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = "ALLOCATION_MENSUELLE",
                action = "UPDATE",
                dataJson = gson.toJson(nouvelleAllocation),
                recordId = nouvelleAllocation.id, // 🆕 CORRECTION : Ajouter l'ID de l'enregistrement
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            // 5. Retourner le succès immédiatement (offline-first)
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recupererAllocationMensuelle(enveloppeId: String, mois: Date): Result<AllocationMensuelle?> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.success(null)

            val moisStr = dateFormatter.format(mois)

            // Récupérer depuis Room (PRIMARY)
            val allocationsEntities = allocationMensuelleDao.getAllocationsByUtilisateur(utilisateurId).first()
            
            // Trouver l'allocation correspondante
            val allocationEntity = allocationsEntities.find { entity -> 
                entity.enveloppeId == enveloppeId && entity.mois == moisStr 
            }
            
            val allocation = allocationEntity?.toAllocationMensuelleModel()
            
            Result.success(allocation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun creerAllocationMensuelle(allocation: AllocationMensuelle): Result<AllocationMensuelle> = withContext(Dispatchers.IO) {
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
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = "ALLOCATION_MENSUELLE",
                action = "CREATE",
                dataJson = gson.toJson(allocationEntity),
                recordId = allocationEntity.id,
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            // 4. Retourner le succès immédiatement (offline-first)
            Result.success(allocation.copy(id = allocationEntity.id))
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recupererAllocationsEnveloppe(enveloppeId: String): Result<List<AllocationMensuelle>> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.success(emptyList())

            // Récupérer depuis Room (PRIMARY)
            val allocationsEntities = allocationMensuelleDao.getAllocationsByUtilisateur(utilisateurId).first()
            
            // Filtrer par enveloppe et convertir
            val allocations = allocationsEntities
                .filter { entity -> entity.enveloppeId == enveloppeId }
                .map { entity -> entity.toAllocationMensuelleModel() }
            
            Result.success(allocations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recupererAllocationParId(id: String): Result<AllocationMensuelle> = withContext(Dispatchers.IO) {
        try {
            // Récupérer depuis Room (PRIMARY)
            val allocationEntity = allocationMensuelleDao.getAllocationById(id)
                ?: return@withContext Result.failure(Exception("Allocation non trouvée"))
            
            // Convertir l'entité en modèle
            val allocation = allocationEntity.toAllocationMensuelleModel()
            
            Result.success(allocation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extension function pour convertir une entité Room en modèle Enveloppe
     */
    private fun EnveloppeEntity.toEnveloppeModel(): Enveloppe {
        val enveloppe = Enveloppe(
            id = id,
            utilisateurId = utilisateurId,
            nom = nom,
            categorieId = categorieId,
            estArchive = estArchive,
            ordre = ordre,
            typeObjectif = typeObjectif,
            objectifMontant = objectifMontant,
            dateObjectif = dateObjectif?.let { try { dateFormatter.parse(it) } catch (e: Exception) { null } },
            dateDebutObjectif = dateDebutObjectif?.let { try { dateFormatter.parse(it) } catch (e: Exception) { null } },
            objectifJour = objectifJour,
            resetApresEcheance = resetApresEcheance
        )
        
        // 🎯 LOG DÉTAILLÉ POUR DÉBUGGER LA CONVERSION ROOM → MODÈLE !
        // 🎯 CONVERSION ROOM → MODÈLE: ${enveloppe.nom}
        //   - Type objectif: ${enveloppe.typeObjectif}
        //   - Montant objectif: ${enveloppe.objectifMontant}
        //   - Date objectif: ${enveloppe.dateObjectif}
        //   - Date début: ${enveloppe.dateDebutObjectif}
        //   - Objectif jour: ${enveloppe.objectifJour}
        //   - Reset après échéance: ${enveloppe.resetApresEcheance}
        
        return enveloppe
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
