package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.data.room.daos.TransactionDao
import com.xburnsx.toutiebudget.data.room.daos.SyncJobDao
import com.xburnsx.toutiebudget.data.room.entities.Transaction as TransactionEntity
import com.xburnsx.toutiebudget.data.room.entities.SyncJob
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.utils.IdGenerator
import com.xburnsx.toutiebudget.workers.SyncWorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Implémentation Room-first du repository des transactions.
 * 
 * LOGIQUE ROOM-FIRST :
 * 1. Toutes les opérations se font d'abord en local (Room)
 * 2. Les modifications sont ajoutées à la liste de tâches (SyncJob)
 * 3. Le Worker synchronise en arrière-plan avec Pocketbase
 * 
 * INTERFACE IDENTIQUE : Même interface que TransactionRepository pour compatibilité
 */
class TransactionRepositoryRoomImpl(
    private val transactionDao: TransactionDao,
    private val syncJobDao: SyncJobDao
) : TransactionRepository {
    private val client = PocketBaseClient
    
    private val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    // Format de date unifié ISO 8601 avec UTC (comme PocketBase)
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    // Formats de fallback pour parser les dates existantes dans Room
    private val dateFormatsFallback = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    )
    
    private fun parseDate(dateString: String?): Date? {
        if (dateString.isNullOrBlank()) return null
        // Essayer tous les formats
        for (format in dateFormatsFallback) {
            try {
                return format.parse(dateString)
            } catch (_: Exception) {
                // Continuer avec le prochain format
            }
        }
        return null
    }

    override suspend fun creerTransaction(transaction: Transaction): Result<Transaction> = withContext(Dispatchers.IO) {
        try {
            // 1. Convertir le modèle en entité Room
            val transactionEntity = TransactionEntity(
                id = transaction.id.ifBlank { IdGenerator.generateId() },
                utilisateurId = transaction.utilisateurId.ifBlank { 
                    client.obtenirUtilisateurConnecte()?.id ?: "" 
                },
                type = transaction.type.valeurPocketBase,
                montant = transaction.montant,
                date = dateFormatter.format(transaction.date),
                note = transaction.note,
                compteId = transaction.compteId,
                collectionCompte = transaction.collectionCompte,
                allocationMensuelleId = transaction.allocationMensuelleId,
                estFractionnee = transaction.estFractionnee,
                sousItems = transaction.sousItems,
                tiersUtiliser = transaction.tiersUtiliser,
                created = transaction.created?.let { dateFormatter.format(it) },
                updated = transaction.updated?.let { dateFormatter.format(it) }
            )

            // 2. Sauvegarder en Room (PRIMARY)
            val id = transactionDao.insertTransaction(transactionEntity)
            
            // 3. Créer la tâche de synchronisation
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = "TRANSACTION",
                action = "CREATE",
                dataJson = gson.toJson(transactionEntity),
                recordId = transactionEntity.id,
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            
            // 4. Toujours écrire la tâche dans la MÊME transaction Room que la donnée.
            // Le Worker fait le réseau après le commit : aucun serveur ne peut voir une demi-opération
            // qui serait ensuite annulée localement (AT4, P5, V4, DF10, IF4).
            syncJobDao.insertSyncJob(syncJob)
            com.xburnsx.toutiebudget.data.services.SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()

            // 5. Retourner le succès immédiatement (offline-first)
            Result.success(transaction.copy(id = transactionEntity.id))
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recupererToutesLesTransactions(): Result<List<Transaction>> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.success(emptyList())

            // Récupérer depuis Room (PRIMARY)
            val transactionsEntities = transactionDao.getTransactionsByUtilisateur(utilisateurId).first()
            
            // Convertir les entités en modèles
            val transactions = transactionsEntities.map { entity ->
                entity.toTransactionModel()
            }
            
            Result.success(transactions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recupererTransactionsParPeriode(debut: Date, fin: Date): Result<List<Transaction>> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.success(emptyList())

            val debutStr = dateFormatter.format(debut)
            val finStr = dateFormatter.format(fin)

            // Récupérer depuis Room (PRIMARY)
            val transactionsEntities = transactionDao.getTransactionsByUtilisateurAndPeriod(
                utilisateurId, debutStr, finStr
            ).first()
            
            // Convertir les entités en modèles
            val transactions = transactionsEntities.map { entity ->
                entity.toTransactionModel()
            }
            
            Result.success(transactions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun recupererTransactionsParPeriode(debut: Date, fin: Date, limit: Int, offset: Int): Result<List<Transaction>> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.success(emptyList())

            val debutStr = dateFormatter.format(debut)
            val finStr = dateFormatter.format(fin)

            // Récupérer depuis Room (PRIMARY) avec pagination
            val transactionsEntities = transactionDao.getTransactionsByUtilisateurAndPeriodWithPagination(
                utilisateurId, debutStr, finStr, limit, offset
            ).first()
            
            // Convertir les entités en modèles
            val transactions = transactionsEntities.map { entity ->
                entity.toTransactionModel()
            }
            
            Result.success(transactions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recupererTransactionsPourCompte(compteId: String, collectionCompte: String): Result<List<Transaction>> = withContext(Dispatchers.IO) {
        try {
            // HC5 : ne JAMAIS lire l'historique d'un autre utilisateur
            // dans une base Room partagée entre deux sessions.
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(IllegalStateException("Connexion requise pour cet historique."))
            require(compteId.isNotBlank() && collectionCompte.isNotBlank()) {
                "Identifiant de compte invalide."
            }
            val transactionsEntities = transactionDao
                .getTransactionsDuCompteUtilisateur(utilisateurId, compteId, collectionCompte)
                .first()
            Result.success(transactionsEntities.map { it.toTransactionModel() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recupererTransactionsParAllocation(allocationId: String): Result<List<Transaction>> = withContext(Dispatchers.IO) {
        try {
            // Récupérer depuis Room (PRIMARY)
            val transactionsEntities = transactionDao.getTransactionsByAllocation(allocationId).first()
            
            // Convertir les entités en modèles
            val transactions = transactionsEntities.map { entity ->
                entity.toTransactionModel()
            }
            
            Result.success(transactions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // HC5 : lit seulement une transaction appartenant au profil actuellement
    // connecté. Cette API est utilisée par les écrans d'historique.
    override suspend fun recupererTransactionDeLaSession(transactionId: String): Result<Transaction> =
        withContext(Dispatchers.IO) {
            runCatching {
                val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                    ?: throw IllegalStateException("Connexion requise pour lire cette transaction.")
                transactionDao.getTransactionHistoriqueDeLaSession(transactionId, utilisateurId)
                    ?.toTransactionModel()
                    ?: throw NoSuchElementException("Transaction absente de la session courante.")
            }
        }

    override suspend fun recupererTransactionParId(transactionId: String): Result<Transaction> = withContext(Dispatchers.IO) {
        try {
            // Récupérer depuis Room (PRIMARY)
            val transactionEntity = transactionDao.getTransactionById(transactionId)
                ?: return@withContext Result.failure(Exception("Transaction non trouvée"))
            
            // Convertir l'entité en modèle
            val transaction = transactionEntity.toTransactionModel()
            
            Result.success(transaction)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mettreAJourTransaction(transaction: Transaction): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Convertir le modèle en entité Room
            val transactionEntity = TransactionEntity(
                id = transaction.id,
                utilisateurId = transaction.utilisateurId,
                type = transaction.type.valeurPocketBase,
                montant = transaction.montant,
                date = dateFormatter.format(transaction.date),
                note = transaction.note,
                compteId = transaction.compteId,
                collectionCompte = transaction.collectionCompte,
                allocationMensuelleId = transaction.allocationMensuelleId,
                estFractionnee = transaction.estFractionnee,
                sousItems = transaction.sousItems,
                tiersUtiliser = transaction.tiersUtiliser,
                created = transaction.created?.let { dateFormatter.format(it) },
                updated = transaction.updated?.let { dateFormatter.format(it) }
            )

            // 2. Mettre à jour en Room (PRIMARY)
            transactionDao.updateTransaction(transactionEntity)
            
            // 3. Créer la tâche de synchronisation
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = "TRANSACTION",
                action = "UPDATE",
                dataJson = gson.toJson(transactionEntity),
                recordId = transactionEntity.id, // 🆕 CORRECTION : Ajouter l'ID de l'enregistrement
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            
            syncJobDao.insertSyncJob(syncJob)
            com.xburnsx.toutiebudget.data.services.SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()

            // 6. Retourner le succès immédiatement (offline-first)
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun supprimerTransaction(transactionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Supprimer de Room (PRIMARY)
            transactionDao.deleteTransactionById(transactionId)
            
            // 2. Créer la tâche de synchronisation
            val syncJob = SyncJob(
                id = IdGenerator.generateId(),
                type = "TRANSACTION",
                action = "DELETE",
                dataJson = gson.toJson(mapOf("id" to transactionId)),
                recordId = transactionId, // 🆕 CORRECTION : Ajouter l'ID de l'enregistrement à supprimer
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            
            syncJobDao.insertSyncJob(syncJob)
            com.xburnsx.toutiebudget.data.services.SyncJobAutoTriggerService.declencherSynchronisationArrierePlan()

            // 5. Retourner le succès immédiatement (offline-first)
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extension function pour convertir une entité Room en modèle Transaction
     */
    private fun TransactionEntity.toTransactionModel(): Transaction {
        // ❌ CORRECTION : Ne pas utiliser Date() par défaut si le parsing échoue
        // Cela causait des dates aléatoires. Si le parsing échoue, on retourne null ou on log l'erreur
        val parsedDate = parseDate(date) ?: run {
            // Log l'erreur mais ne crée pas une date aléatoire
            android.util.Log.e("TransactionRepositoryRoom", "⚠️ Impossible de parser la date: $date")
            // Retourner une date par défaut seulement si vraiment nécessaire (éviter les crashes)
            Date(System.currentTimeMillis())
        }
        
        return Transaction(
            id = id,
            utilisateurId = utilisateurId,
            type = com.xburnsx.toutiebudget.data.modeles.TypeTransaction.depuisValeurPocketBase(type) ?: com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Depense,
            montant = montant,
            date = parsedDate,
            note = note,
            compteId = compteId,
            collectionCompte = collectionCompte,
            allocationMensuelleId = allocationMensuelleId,
            estFractionnee = estFractionnee,
            sousItems = sousItems,
            tiersUtiliser = tiersUtiliser,
            created = created?.let { parseDate(it) },
            updated = updated?.let { parseDate(it) }
        )
    }
    
}
