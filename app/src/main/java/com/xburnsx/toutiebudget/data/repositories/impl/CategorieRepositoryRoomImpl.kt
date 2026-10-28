package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.xburnsx.toutiebudget.data.modeles.Categorie
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.data.room.ToutieBudgetDatabase
import com.xburnsx.toutiebudget.data.room.daos.CategorieDao
import com.xburnsx.toutiebudget.data.room.daos.SyncJobDao
import com.xburnsx.toutiebudget.data.room.entities.Categorie as CategorieEntity
import com.xburnsx.toutiebudget.data.room.entities.SyncJob
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.utils.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

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
    private val categorieDao: CategorieDao,
    private val syncJobDao: SyncJobDao,
    private val operationAtomique: com.xburnsx.toutiebudget.utils.OperationAtomique =
        com.xburnsx.toutiebudget.utils.OperationAtomiqueDirecte,
    private val enveloppeDao: com.xburnsx.toutiebudget.data.room.daos.EnveloppeDao? = null
) : CategorieRepository {
    
    private val client = PocketBaseClient
    
    private val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

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
            operationAtomique.executer {
            // CE4/CE13 : verrou de session et ID vérifiés avant REPLACE.
            val utilisateur = client.obtenirUtilisateurConnecte()?.id
                ?: throw SecurityException("Connexion requise.")
            require(categorie.utilisateurId.isBlank() || categorie.utilisateurId == utilisateur) {
                "Impossible de créer un élément appartenant à un autre profil."
            }
            if (categorie.id.isNotBlank()) {
                require(categorieDao.getCategorieById(categorie.id) == null) {
                    "Identifiant déjà existant : création refusée."
                }
            }
            // 1. Convertir le modèle en entité Room
            val categorieEntity = CategorieEntity(
                id = categorie.id.ifBlank { IdGenerator.generateId() },
                utilisateurId = categorie.utilisateurId.ifBlank { 
                    utilisateur 
                },
                nom = categorie.nom,
                ordre = categorie.ordre
            )

            // 2. Sauvegarder en Room (PRIMARY)
            val id = categorieDao.insertCategorie(categorieEntity)
            
            // 3. Ajouter à la liste de tâches pour synchronisation
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = "CATEGORIE",
                action = "CREATE",
                dataJson = gson.toJson(categorieEntity),
                recordId = categorieEntity.id,
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            // 🚀 DÉCLENCHER IMMÉDIATEMENT LA SYNCHRONISATION !
            com.xburnsx.toutiebudget.data.services.SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()

            // 4. Retourner le succès immédiatement (offline-first)
            Result.success(categorie.copy(id = categorieEntity.id))
            
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mettreAJourCategorie(categorie: Categorie): Result<Categorie> = withContext(Dispatchers.IO) {
        try {
            operationAtomique.executer {
            // CE13 : empêcher qu'une ancienne session écrase une ligne d'un autre utilisateur.
            val utilisateur = client.obtenirUtilisateurConnecte()?.id
                ?: throw SecurityException("Connexion requise.")
            val courante = categorieDao.getCategorieById(categorie.id)
                ?: throw NoSuchElementException("Élément introuvable : modification refusée.")
            require(courante.utilisateurId == utilisateur &&
                categorie.utilisateurId == utilisateur) {
                "Élément hors session : écriture refusée."
            }
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
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = "CATEGORIE",
                action = "UPDATE",
                dataJson = gson.toJson(categorieEntity),
                recordId = categorieEntity.id,
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            // 🚀 DÉCLENCHER IMMÉDIATEMENT LA SYNCHRONISATION !
            com.xburnsx.toutiebudget.data.services.SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()

            // 4. Retourner le succès immédiatement (offline-first)
            Result.success(categorie)
            
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reorganiserCategories(
        avant: List<Categorie>, apres: List<Categorie>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            operationAtomique.executer {
                val utilisateur = client.obtenirUtilisateurConnecte()?.id
                    ?: error("Session requise pour réorganiser.")
                require(avant.map { it.id }.toSet() == apres.map { it.id }.toSet())
                require(avant.all { it.utilisateurId == utilisateur } &&
                    apres.all { it.utilisateurId == utilisateur })
                // Comparaison et écritures dans LA MÊME transaction Room.
                val actuelles = recupererToutesLesCategories().getOrThrow().associateBy { it.id }
                require(avant.all { actuelles[it.id] == it }) {
                    "Classement modifié ailleurs. Recharge les catégories."
                }
                apres.filter { avant.first { a -> a.id == it.id }.ordre != it.ordre }
                    .forEach { mettreAJourCategorie(it).getOrThrow() }
            }
        }
    }

    override suspend fun supprimerCategorie(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            operationAtomique.executer {
            // CE3 : y compris les enveloppes archivées. Le test et le
            // DELETE appartiennent à la même transaction Room.
            val utilisateur = client.obtenirUtilisateurConnecte()?.id
                ?: error("Connexion requise pour supprimer une catégorie.")
            val categorie = categorieDao.getCategorieById(id)
                ?: error("Catégorie introuvable.")
            require(categorie.utilisateurId == utilisateur) { "Catégorie hors session." }
            val enveloppes = enveloppeDao
                ?: error("Vérification des enveloppes indisponible.")
            val liees = enveloppes.getEnveloppesByUtilisateur(utilisateur).first()
                .count { it.categorieId == id }
            require(liees == 0) {
                "Impossible de supprimer cette catégorie : $liees enveloppe(s) active(s) ou archivée(s) y sont encore liées."
            }
            // 1. Supprimer de Room (PRIMARY)
            categorieDao.deleteCategorieById(id)
            
            // 2. Ajouter à la liste de tâches pour synchronisation
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = "CATEGORIE",
                action = "DELETE",
                dataJson = gson.toJson(mapOf("id" to id)),
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            // 🚀 DÉCLENCHER IMMÉDIATEMENT LA SYNCHRONISATION !
            com.xburnsx.toutiebudget.data.services.SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()

            // 3. Retourner le succès immédiatement (offline-first)
            Result.success(Unit)
            
            }
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
