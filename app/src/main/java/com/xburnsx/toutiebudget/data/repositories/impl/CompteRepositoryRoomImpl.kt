package com.xburnsx.toutiebudget.data.repositories.impl

// import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.room.daos.*
import com.xburnsx.toutiebudget.data.room.entities.*
import com.xburnsx.toutiebudget.data.room.entities.SyncJob
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.utils.IdGenerator
import com.xburnsx.toutiebudget.data.services.SyncJobAutoTriggerService
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
        .serializeNulls()
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
            // 🚨 CORRECTION : Spécifier le type exact et la collection !
            val (syncJobType, dataJson, collectionName) = when (compteAvecId) {
                is CompteCheque -> {
                    // 🚨 COMPTE_CHÈQUE DÉTECTÉ - GÉNÉRATION JSON MANUEL
                    val entity = compteAvecId.toCompteChequeEntity()
                    val json = genererJsonCompteChequeManuel(entity)
                    // 🚨 JSON MANUEL GÉNÉRÉ:
                    //   $json
                    Triple("COMPTE_CHEQUE", json, "comptes_cheques")
                }
                is CompteCredit -> {
                    // 🚨 COMPTE_CRÉDIT DÉTECTÉ - GÉNÉRATION JSON MANUEL
                    val entity = compteAvecId.toCompteCreditEntity()
                    val json = genererJsonCompteCreditManuel(entity)
                    // 🚨 JSON MANUEL GÉNÉRÉ:
                    //   $json
                    Triple("COMPTE_CREDIT", json, "comptes_credits")
                }
                is CompteDette -> {
                    // 🚨 COMPTE_DETTE DÉTECTÉ - GÉNÉRATION JSON MANUEL
                    val entity = compteAvecId.toCompteDetteEntity()
                    val json = genererJsonCompteDetteManuel(entity)
                    // 🚨 JSON MANUEL GÉNÉRÉ:
                    //   $json
                    Triple("COMPTE_DETTE", json, "comptes_dettes")
                }
                is CompteInvestissement -> {
                    // 🚨 COMPTE_INVESTISSEMENT DÉTECTÉ - GÉNÉRATION JSON MANUEL
                    val entity = compteAvecId.toCompteInvestissementEntity()
                    val json = genererJsonCompteInvestissementManuel(entity)
                    // 🚨 JSON MANUEL GÉNÉRÉ:
                    //   $json
                    Triple("COMPTE_INVESTISSEMENT", json, "comptes_investissement")
                }
                else -> throw IllegalArgumentException("Type de compte non supporté")
            }
            
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = syncJobType,
                action = "CREATE",
                dataJson = dataJson,
                recordId = compteAvecId.id,
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            // 🚀 DÉCLENCHER IMMÉDIATEMENT LA SYNCHRONISATION !
            SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 🚨 NOUVELLE MÉTHODE : Mise à jour SANS SyncJob automatique
     * Utilisée par mettreAJourPretAPlacerSeulement pour éviter les SyncJobs en double
     */
    private suspend fun mettreAJourCompteSansSyncJob(compte: Compte): Result<Unit> = withContext(Dispatchers.IO) {
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

            // Mettre à jour dans Room selon le type (SANS SyncJob)
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
            // 🚨 CORRECTION : Utiliser les méthodes manuelles pour le JSON snake_case !
            val (syncJobType, dataJson, collectionName) = when (compteAvecUtilisateurId) {
                is CompteCheque -> {
                    // 🚨 COMPTE_CHÈQUE UPDATE DÉTECTÉ - GÉNÉRATION JSON MANUEL
                    val entity = compteAvecUtilisateurId.toCompteChequeEntity()
                    val json = genererJsonCompteChequeManuel(entity)
                    // 🚨 JSON MANUEL GÉNÉRÉ (UPDATE):
                    //   $json
                    Triple("COMPTE_CHEQUE", json, "comptes_cheques")
                }
                is CompteCredit -> {
                    // 🚨 COMPTE_CRÉDIT UPDATE DÉTECTÉ - GÉNÉRATION JSON MANUEL
                    val entity = compteAvecUtilisateurId.toCompteCreditEntity()
                    val json = genererJsonCompteCreditManuel(entity)
                    // 🚨 JSON MANUEL GÉNÉRÉ (UPDATE):
                    //   $json
                    Triple("COMPTE_CREDIT", json, "comptes_credits")
                }
                is CompteDette -> {
                    // 🚨 COMPTE_DETTE UPDATE DÉTECTÉ - GÉNÉRATION JSON MANUEL
                    val entity = compteAvecUtilisateurId.toCompteDetteEntity()
                    val json = genererJsonCompteDetteManuel(entity)
                    // 🚨 JSON MANUEL GÉNÉRÉ (UPDATE):
                    //   $json
                    Triple("COMPTE_DETTE", json, "comptes_dettes")
                }
                is CompteInvestissement -> {
                    // 🚨 COMPTE_INVESTISSEMENT UPDATE DÉTECTÉ - GÉNÉRATION JSON MANUEL
                    val entity = compteAvecUtilisateurId.toCompteInvestissementEntity()
                    val json = genererJsonCompteInvestissementManuel(entity)
                    // 🚨 JSON MANUEL GÉNÉRÉ (UPDATE):
                    //   $json
                    Triple("COMPTE_INVESTISSEMENT", json, "comptes_investissement")
                }
                else -> throw IllegalArgumentException("Type de compte non supporté")
            }
            
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = syncJobType,
                action = "UPDATE",
                dataJson = dataJson,
                recordId = compteAvecUtilisateurId.id,
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            // 🚀 DÉCLENCHER IMMÉDIATEMENT LA SYNCHRONISATION !
            SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()

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
            // 🚨 CORRECTION : Spécifier le type exact selon la collection !
            val syncJobType = when (collection) {
                "comptes_cheques" -> "COMPTE_CHEQUE"
                "comptes_credits" -> "COMPTE_CREDIT"
                "comptes_dettes" -> "COMPTE_DETTE"
                "comptes_investissement" -> "COMPTE_INVESTISSEMENT"
                else -> throw IllegalArgumentException("Collection inconnue: $collection")
            }
            
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = syncJobType,
                action = "DELETE",
                dataJson = gson.toJson(mapOf("id" to compteId, "collection" to collection)),
                recordId = compteId,
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            // 🚀 DÉCLENCHER IMMÉDIATEMENT LA SYNCHRONISATION !
            SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()

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

            val resultat = mettreAJourCompte(compteMisAJour)
            
            // 🚀 DÉCLENCHER IMMÉDIATEMENT LA SYNCHRONISATION !
            // CRITIQUE : Sans ça, les suppressions de transactions ne sont pas synchronisées !
            if (resultat.isSuccess) {
                SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()
            }
            
            resultat
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
                    
                    // ✅ 1. Mettre à jour Room SANS créer de SyncJob automatique
                    val resultRoom = mettreAJourCompteSansSyncJob(compteMisAJour)
                    if (resultRoom.isFailure) {
                        return@withContext resultRoom
                    }
                    
                    // ✅ 2. RÉCUPÉRER LE COMPTE MIS À JOUR POUR AVOIR LE BON PRÊT À PLACER
                    val compteMisAJourRecupere = getCompteById(compteId, collection)
                    if (compteMisAJourRecupere == null) {
                        return@withContext Result.failure(Exception("Impossible de récupérer le compte mis à jour"))
                    }
                    
                    // ✅ 3. CRÉER UN SYNCJOB POUR POCKETBASE AVEC LE BON PRÊT À PLACER
                    // 🚨 CORRECTION CRITIQUE : Créer directement l'entité Room avec le bon prêt à placer !
                    if (compteMisAJourRecupere is CompteCheque) {
                        val compteEntity = CompteChequeEntity(
                            id = compteMisAJourRecupere.id,
                            utilisateurId = compteMisAJourRecupere.utilisateurId,
                            nom = compteMisAJourRecupere.nom,
                            solde = compteMisAJourRecupere.solde,
                            pretAPlacerRaw = compteMisAJourRecupere.pretAPlacerRaw,
                            couleur = compteMisAJourRecupere.couleur,
                            estArchive = compteMisAJourRecupere.estArchive,
                            ordre = compteMisAJourRecupere.ordre,
                            collection = compteMisAJourRecupere.collection
                        )
                        
                        val syncJob = SyncJob(
                            id = IdGenerator.generateId(),
                            type = "COMPTE_CHEQUE",
                            action = "UPDATE",
                            dataJson = gson.toJson(compteEntity),
                            recordId = compteId,
                            createdAt = System.currentTimeMillis(),
                            status = "PENDING"
                        )
                        syncJobDao.insertSyncJob(syncJob)
                        
                        // 🚨 DEBUG CRITIQUE : Vérifier que le SyncJob est bien créé
                        // 🚨 SYNCJOB CRÉÉ POUR PRÊT À PLACER:
                        //   ID: ${syncJob.id}
                        //   Type: ${syncJob.type}
                        //   Action: ${syncJob.action}
                        //   RecordId: ${syncJob.recordId}
                        //   DataJson: ${syncJob.dataJson}
                        
                        // 🚀 DÉCLENCHER IMMÉDIATEMENT LA SYNCHRONISATION !
                        SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()
                        
                        return@withContext Result.success(Unit)
                    }
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
    
    /**
     * Génère manuellement le JSON pour un compte chèque avec les bons noms de champs (snake_case)
     */
    private fun genererJsonCompteChequeManuel(entity: CompteChequeEntity): String {
        val data = mapOf(
            "id" to entity.id,
            "utilisateur_id" to entity.utilisateurId,
            "nom" to entity.nom,
            "solde" to entity.solde,
            "pret_a_placer" to entity.pretAPlacerRaw,
            "couleur" to entity.couleur,
            "archive" to entity.estArchive,
            "ordre" to entity.ordre,
            "collection" to entity.collection
        )
        return gson.toJson(data)
    }
    
    /**
     * Génère manuellement le JSON pour un compte crédit avec les bons noms de champs (snake_case)
     */
    private fun genererJsonCompteCreditManuel(entity: CompteCreditEntity): String {
        val data = mapOf(
            "id" to entity.id,
            "utilisateur_id" to entity.utilisateurId,
            "nom" to entity.nom,
            "solde_utilise" to entity.soldeUtilise,
            "couleur" to entity.couleur,
            "archive" to entity.estArchive,
            "ordre" to entity.ordre,
            "limite_credit" to entity.limiteCredit,
            "taux_interet" to entity.tauxInteret,
            "paiement_minimum" to entity.paiementMinimum,
            "frais_mensuels_json" to entity.fraisMensuelsJson,
            "collection" to entity.collection
        )
        return gson.toJson(data)
    }
    
    /**
     * Génère manuellement le JSON pour un compte dette avec les bons noms de champs (snake_case)
     */
    private fun genererJsonCompteDetteManuel(entity: CompteDetteEntity): String {
        val data = mapOf(
            "id" to entity.id,
            "utilisateur_id" to entity.utilisateurId,
            "nom" to entity.nom,
            "solde_dette" to entity.soldeDette,
            "archive" to entity.estArchive,
            "ordre" to entity.ordre,
            "montant_initial" to entity.montantInitial,
            "taux_interet" to entity.tauxInteret,
            "paiement_minimum" to entity.paiementMinimum,
            "duree_mois_pret" to entity.dureeMoisPret,
            "paiement_effectue" to entity.paiementEffectue,
            "prix_total" to entity.prixTotal,
            "collection" to entity.collection
        )
        return gson.toJson(data)
    }
    
    /**
     * Génère manuellement le JSON pour un compte investissement avec les bons noms de champs (snake_case)
     */
    private fun genererJsonCompteInvestissementManuel(entity: CompteInvestissementEntity): String {
        val data = mapOf(
            "id" to entity.id,
            "utilisateur_id" to entity.utilisateurId,
            "nom" to entity.nom,
            "solde" to entity.solde,
            "couleur" to entity.couleur,
            "archive" to entity.estArchive,
            "ordre" to entity.ordre,
            "collection" to entity.collection
        )
        return gson.toJson(data)
    }
}
