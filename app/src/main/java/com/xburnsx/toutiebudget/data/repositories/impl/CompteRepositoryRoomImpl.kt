package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.room.daos.*
import com.xburnsx.toutiebudget.data.room.entities.*
import com.xburnsx.toutiebudget.data.room.entities.SyncJob
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.utils.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.google.gson.JsonParser

// Alias pour éviter les conflits de noms
import com.xburnsx.toutiebudget.data.room.entities.CompteCheque as CompteChequeEntity
import com.xburnsx.toutiebudget.data.room.entities.CompteCredit as CompteCreditEntity
import com.xburnsx.toutiebudget.data.room.entities.CompteDette as CompteDetteEntity
import com.xburnsx.toutiebudget.data.room.entities.CompteInvestissement as CompteInvestissementEntity

class CompteRepositoryRoomImpl(
    private val compteChequeDao: CompteChequeDao,
    private val compteCreditDao: CompteCreditDao,
    private val compteDetteDao: CompteDetteDao,
    private val compteInvestissementDao: CompteInvestissementDao,
    private val syncJobDao: SyncJobDao
) : CompteRepository {

    private val client = PocketBaseClient
    
    private val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    override suspend fun recupererTousLesComptes(): Result<List<Compte>> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            // Récupérer tous les types de comptes en parallèle
            val comptesCheque = compteChequeDao.getComptesByUtilisateur(utilisateurId).first()
            val comptesCredit = compteCreditDao.getComptesByUtilisateur(utilisateurId).first()
            val comptesDette = compteDetteDao.getComptesByUtilisateur(utilisateurId).first()
            val comptesInvestissement = compteInvestissementDao.getComptesByUtilisateur(utilisateurId).first()

            // Convertir les entités Room en modèles Compte
            val comptesChequeModels = comptesCheque.map { it.toCompteChequeModel() }
            val comptesCreditModels = comptesCredit.map { it.toCompteCreditModel() }
            val comptesDetteModels = comptesDette.map { it.toCompteDetteModel() }
            val comptesInvestissementModels = comptesInvestissement.map { it.toCompteInvestissementModel() }

            // Combiner tous les comptes et trier par ordre
            val tousLesComptes = (comptesChequeModels + comptesCreditModels + comptesDetteModels + comptesInvestissementModels)
                .sortedBy { it.ordre }

            Result.success(tousLesComptes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun creerCompte(compte: Compte): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            // Générer un ID unique à 15 caractères si nécessaire
            val compteAvecId = if (compte.id.isBlank()) {
                when (compte) {
                    is CompteCheque -> compte.copy(id = IdGenerator.generateId(), utilisateurId = utilisateurId)
                    is CompteCredit -> compte.copy(id = IdGenerator.generateId(), utilisateurId = utilisateurId)
                    is CompteDette -> compte.copy(id = IdGenerator.generateId(), utilisateurId = utilisateurId)
                    is CompteInvestissement -> compte.copy(id = IdGenerator.generateId(), utilisateurId = utilisateurId)
                    else -> throw IllegalArgumentException("Type de compte non supporté")
                }
            } else {
                when (compte) {
                    is CompteCheque -> compte.copy(utilisateurId = utilisateurId)
                    is CompteCredit -> compte.copy(utilisateurId = utilisateurId)
                    is CompteDette -> compte.copy(utilisateurId = utilisateurId)
                    is CompteInvestissement -> compte.copy(utilisateurId = utilisateurId)
                    else -> throw IllegalArgumentException("Type de compte non supporté")
                }
            }

            // Sauvegarder dans Room selon le type
            when (compteAvecId) {
                is CompteCheque -> {
                    val entity = compteAvecId.toCompteChequeEntity()
                    compteChequeDao.insertCompte(entity)
                }
                is CompteCredit -> {
                    val entity = compteAvecId.toCompteCreditEntity()
                    compteCreditDao.insertCompte(entity)
                }
                is CompteDette -> {
                    val entity = compteAvecId.toCompteDetteEntity()
                    compteDetteDao.insertCompte(entity)
                }
                is CompteInvestissement -> {
                    val entity = compteAvecId.toCompteInvestissementEntity()
                    compteInvestissementDao.insertCompte(entity)
                }
                else -> throw IllegalArgumentException("Type de compte non supporté")
            }

            // Créer un SyncJob pour la synchronisation
            // Sérialiser l'entité Room au lieu du modèle pour éviter les problèmes avec les interfaces
            val dataJson = when (compteAvecId) {
                is CompteCheque -> gson.toJson(compteAvecId.toCompteChequeEntity())
                is CompteCredit -> gson.toJson(compteAvecId.toCompteCreditEntity())
                is CompteDette -> gson.toJson(compteAvecId.toCompteDetteEntity())
                is CompteInvestissement -> gson.toJson(compteAvecId.toCompteInvestissementEntity())
                else -> gson.toJson(compteAvecId)
            }
            
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = "COMPTE",
                action = "CREATE",
                dataJson = dataJson,
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mettreAJourCompte(compte: Compte): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            val compteAvecUtilisateurId = when (compte) {
                is CompteCheque -> compte.copy(utilisateurId = utilisateurId)
                is CompteCredit -> compte.copy(utilisateurId = utilisateurId)
                is CompteDette -> compte.copy(utilisateurId = utilisateurId)
                is CompteInvestissement -> compte.copy(utilisateurId = utilisateurId)
                else -> throw IllegalArgumentException("Type de compte non supporté")
            }

            // Mettre à jour dans Room selon le type
            when (compteAvecUtilisateurId) {
                is CompteCheque -> {
                    val entity = compteAvecUtilisateurId.toCompteChequeEntity()
                    compteChequeDao.updateCompte(entity)
                }
                is CompteCredit -> {
                    val entity = compteAvecUtilisateurId.toCompteCreditEntity()
                    compteCreditDao.updateCompte(entity)
                }
                is CompteDette -> {
                    val entity = compteAvecUtilisateurId.toCompteDetteEntity()
                    compteDetteDao.updateCompte(entity)
                }
                is CompteInvestissement -> {
                    val entity = compteAvecUtilisateurId.toCompteInvestissementEntity()
                    compteInvestissementDao.updateCompte(entity)
                }
                else -> throw IllegalArgumentException("Type de compte non supporté")
            }

            // Créer un SyncJob pour la synchronisation
            // Sérialiser l'entité Room au lieu du modèle pour éviter les problèmes avec les interfaces
            val dataJson = when (compteAvecUtilisateurId) {
                is CompteCheque -> gson.toJson(compteAvecUtilisateurId.toCompteChequeEntity())
                is CompteCredit -> gson.toJson(compteAvecUtilisateurId.toCompteCreditEntity())
                is CompteDette -> gson.toJson(compteAvecUtilisateurId.toCompteDetteEntity())
                is CompteInvestissement -> gson.toJson(compteAvecUtilisateurId.toCompteInvestissementEntity())
                else -> gson.toJson(compteAvecUtilisateurId)
            }
            
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = "COMPTE",
                action = "UPDATE",
                dataJson = dataJson,
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun supprimerCompte(compteId: String, collection: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Supprimer de Room selon la collection
            when (collection) {
                "comptes_cheques" -> compteChequeDao.deleteCompteById(compteId)
                "comptes_credits" -> compteCreditDao.deleteCompteById(compteId)
                "comptes_dettes" -> compteDetteDao.deleteCompteById(compteId)
                "comptes_investissement" -> compteInvestissementDao.deleteCompteById(compteId)
                else -> return@withContext Result.failure(Exception("Collection inconnue: $collection"))
            }

            // Créer un SyncJob pour la synchronisation
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = "COMPTE",
                action = "DELETE",
                dataJson = gson.toJson(mapOf("id" to compteId, "collection" to collection)),
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCompteById(compteId: String, collection: String): Compte? = withContext(Dispatchers.IO) {
        try {
            when (collection) {
                "comptes_cheques" -> {
                    val entity = compteChequeDao.getCompteById(compteId)
                    entity?.toCompteChequeModel()
                }
                "comptes_credits" -> {
                    val entity = compteCreditDao.getCompteById(compteId)
                    entity?.toCompteCreditModel()
                }
                "comptes_dettes" -> {
                    val entity = compteDetteDao.getCompteById(compteId)
                    entity?.toCompteDetteModel()
                }
                "comptes_investissement" -> {
                    val entity = compteInvestissementDao.getCompteById(compteId)
                    entity?.toCompteInvestissementModel()
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun mettreAJourSolde(compteId: String, collection: String, nouveauSolde: Double) {
        try {
            val compte = getCompteById(compteId, collection)
                ?: throw Exception("Compte non trouvé")

            val compteMisAJour = when (compte) {
                is CompteCheque -> compte.copy(solde = nouveauSolde)
                is CompteCredit -> compte.copy(soldeUtilise = nouveauSolde)
                is CompteDette -> compte.copy(soldeDette = nouveauSolde)
                is CompteInvestissement -> compte.copy(solde = nouveauSolde)
                else -> throw IllegalArgumentException("Type de compte non supporté")
            }

            mettreAJourCompte(compteMisAJour)
        } catch (e: Exception) {
            // Ignorer l'erreur pour cette méthode qui retourne Unit
        }
    }

    override suspend fun mettreAJourSoldeAvecVariation(compteId: String, collectionCompte: String, variationSolde: Double): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val compte = getCompteById(compteId, collectionCompte)
                ?: return@withContext Result.failure(Exception("Compte non trouvé"))

            val nouveauSolde = when (compte) {
                is CompteCheque -> compte.solde + variationSolde
                is CompteCredit -> compte.soldeUtilise + variationSolde
                is CompteDette -> compte.soldeDette + variationSolde
                is CompteInvestissement -> compte.solde + variationSolde
                else -> throw IllegalArgumentException("Type de compte non supporté")
            }

            val compteMisAJour = when (compte) {
                is CompteCheque -> compte.copy(solde = nouveauSolde)
                is CompteCredit -> compte.copy(soldeUtilise = nouveauSolde)
                is CompteDette -> compte.copy(soldeDette = nouveauSolde)
                is CompteInvestissement -> compte.copy(solde = nouveauSolde)
                else -> throw IllegalArgumentException("Type de compte non supporté")
            }

            mettreAJourCompte(compteMisAJour)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mettreAJourSoldeAvecVariationEtPretAPlacer(
        compteId: String,
        collectionCompte: String,
        variationSolde: Double,
        mettreAJourPretAPlacer: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val compte = getCompteById(compteId, collectionCompte)
                ?: return@withContext Result.failure(Exception("Compte non trouvé"))

            when (compte) {
                is CompteCheque -> {
                    val nouveauSolde = compte.solde + variationSolde
                    val nouveauPretAPlacer = if (mettreAJourPretAPlacer) {
                        compte.pretAPlacer + variationSolde
                    } else {
                        compte.pretAPlacer
                    }
                    val compteMisAJour = compte.copy(
                        solde = nouveauSolde,
                        pretAPlacerRaw = nouveauPretAPlacer
                    )
                    mettreAJourCompte(compteMisAJour)
                }
                is CompteCredit, is CompteDette, is CompteInvestissement -> {
                    // Pour les autres types de comptes, utiliser la méthode standard
                    mettreAJourSoldeAvecVariation(compteId, collectionCompte, variationSolde)
                }
                else -> throw IllegalArgumentException("Type de compte non supporté")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mettreAJourPretAPlacerSeulement(
        compteId: String,
        variationPretAPlacer: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Chercher dans toutes les collections pour trouver le compte
            val collections = listOf("comptes_cheques", "comptes_credits", "comptes_dettes", "comptes_investissement")
            
            for (collection in collections) {
                val compte = getCompteById(compteId, collection)
                if (compte is CompteCheque) {
                    val nouveauPretAPlacer = compte.pretAPlacer + variationPretAPlacer
                    val compteMisAJour = compte.copy(pretAPlacerRaw = nouveauPretAPlacer)
                    return@withContext mettreAJourCompte(compteMisAJour)
                }
            }
            
            Result.failure(Exception("Compte chèque non trouvé avec l'ID $compteId"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recupererCompteParId(compteId: String, collectionCompte: String): Result<Compte> = withContext(Dispatchers.IO) {
        try {
            val compte = getCompteById(compteId, collectionCompte)
            if (compte != null) {
                Result.success(compte)
            } else {
                Result.failure(Exception("Compte non trouvé dans la collection $collectionCompte"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recupererCompteParIdToutesCollections(compteId: String): Result<Compte> = withContext(Dispatchers.IO) {
        try {
            val collections = listOf("comptes_cheques", "comptes_credits", "comptes_dettes", "comptes_investissement")
            
            for (collection in collections) {
                val compte = getCompteById(compteId, collection)
                if (compte != null) {
                    return@withContext Result.success(compte)
                }
            }
            
            Result.failure(Exception("Aucun compte trouvé avec l'ID $compteId dans toutes les collections."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ===== MÉTHODES UTILITAIRES =====

    private fun obtenirCollectionPourCompte(compte: Compte): String {
        return when (compte) {
            is CompteCheque -> "comptes_cheques"
            is CompteCredit -> "comptes_credits"
            is CompteDette -> "comptes_dettes"
            is CompteInvestissement -> "comptes_investissement"
        }
    }

    // ===== EXTENSIONS POUR CONVERSION ENTITÉS ↔ MODÈLES =====

    private fun CompteChequeEntity.toCompteChequeModel(): CompteCheque {
        return CompteCheque(
            id = this.id,
            utilisateurId = this.utilisateurId,
            nom = this.nom,
            solde = this.solde,
            pretAPlacerRaw = this.pretAPlacerRaw,
            couleur = this.couleur,
            estArchive = this.estArchive,
            ordre = this.ordre,
            collection = this.collection
        )
    }

    private fun CompteCheque.toCompteChequeEntity(): CompteChequeEntity {
        return CompteChequeEntity(
            id = this.id,
            utilisateurId = this.utilisateurId,
            nom = this.nom,
            solde = this.solde,
            pretAPlacerRaw = this.pretAPlacerRaw,
            couleur = this.couleur,
            estArchive = this.estArchive,
            ordre = this.ordre,
            collection = this.collection
        )
    }

    private fun CompteCreditEntity.toCompteCreditModel(): CompteCredit {
        return CompteCredit(
            id = this.id,
            utilisateurId = this.utilisateurId,
            nom = this.nom,
            soldeUtilise = this.soldeUtilise,
            couleur = this.couleur,
            estArchive = this.estArchive,
            ordre = this.ordre,
            limiteCredit = this.limiteCredit,
            tauxInteret = this.tauxInteret,
            paiementMinimum = this.paiementMinimum,
            fraisMensuelsJson = this.fraisMensuelsJson?.let { JsonParser.parseString(it) },
            collection = this.collection
        )
    }

    private fun CompteCredit.toCompteCreditEntity(): CompteCreditEntity {
        return CompteCreditEntity(
            id = this.id,
            utilisateurId = this.utilisateurId,
            nom = this.nom,
            soldeUtilise = this.soldeUtilise,
            couleur = this.couleur,
            estArchive = this.estArchive,
            ordre = this.ordre,
            limiteCredit = this.limiteCredit,
            tauxInteret = this.tauxInteret,
            paiementMinimum = this.paiementMinimum,
            fraisMensuelsJson = this.fraisMensuelsJson?.toString(),
            collection = this.collection
        )
    }

    private fun CompteDetteEntity.toCompteDetteModel(): CompteDette {
        return CompteDette(
            id = this.id,
            utilisateurId = this.utilisateurId,
            nom = this.nom,
            soldeDette = this.soldeDette,
            estArchive = this.estArchive,
            ordre = this.ordre,
            montantInitial = this.montantInitial,
            tauxInteret = this.tauxInteret,
            paiementMinimum = this.paiementMinimum,
            dureeMoisPret = this.dureeMoisPret,
            paiementEffectue = this.paiementEffectue,
            prixTotal = this.prixTotal,
            collection = this.collection
        )
    }

    private fun CompteDette.toCompteDetteEntity(): CompteDetteEntity {
        return CompteDetteEntity(
            id = this.id,
            utilisateurId = this.utilisateurId,
            nom = this.nom,
            soldeDette = this.soldeDette,
            estArchive = this.estArchive,
            ordre = this.ordre,
            montantInitial = this.montantInitial,
            tauxInteret = this.tauxInteret,
            paiementMinimum = this.paiementMinimum,
            dureeMoisPret = this.dureeMoisPret,
            paiementEffectue = this.paiementEffectue,
            prixTotal = this.prixTotal,
            collection = this.collection
        )
    }

    private fun CompteInvestissementEntity.toCompteInvestissementModel(): CompteInvestissement {
        return CompteInvestissement(
            id = this.id,
            utilisateurId = this.utilisateurId,
            nom = this.nom,
            solde = this.solde,
            couleur = this.couleur,
            estArchive = this.estArchive,
            ordre = this.ordre,
            collection = this.collection
        )
    }

    private fun CompteInvestissement.toCompteInvestissementEntity(): CompteInvestissementEntity {
        return CompteInvestissementEntity(
            id = this.id,
            utilisateurId = this.utilisateurId,
            nom = this.nom,
            solde = this.solde,
            couleur = this.couleur,
            estArchive = this.estArchive,
            ordre = this.ordre,
            collection = this.collection
        )
    }
}
