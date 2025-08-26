package com.xburnsx.toutiebudget.data.repositories.impl

import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.data.room.ToutieBudgetDatabase
import com.xburnsx.toutiebudget.data.room.entities.Transaction as TransactionEntity
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.utils.IdGenerator
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
    private val database: ToutieBudgetDatabase
) : TransactionRepository {
    
    private val transactionDao = database.transactionDao()
    private val syncJobDao = database.syncJobDao()
    private val client = PocketBaseClient
    
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
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
            
            // 3. Ajouter à la liste de tâches pour synchronisation
            val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                id = IdGenerator.generateId(),
                type = "TRANSACTION",
                action = "CREATE",
                dataJson = com.google.gson.Gson().toJson(transactionEntity),
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            syncJobDao.insertSyncJob(syncJob)

            // 4. Retourner le succès immédiatement (offline-first)
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

    override suspend fun recupererTransactionsPourCompte(compteId: String, collectionCompte: String): Result<List<Transaction>> = withContext(Dispatchers.IO) {
        try {
            // Récupérer depuis Room (PRIMARY)
            val transactionsEntities = transactionDao.getTransactionsByCompte(compteId, collectionCompte).first()
            
            // Convertir les entités en modèles
            val transactions = transactionsEntities.map { entity ->
                entity.toTransactionModel()
            }
            
            Result.success(transactions)
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
            
            // 3. Ajouter à la liste de tâches pour synchronisation
            val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                id = IdGenerator.generateId(),
                type = "TRANSACTION",
                action = "UPDATE",
                dataJson = com.google.gson.Gson().toJson(transactionEntity),
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

    override suspend fun supprimerTransaction(transactionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Supprimer de Room (PRIMARY)
            transactionDao.deleteTransactionById(transactionId)
            
            // 2. Ajouter à la liste de tâches pour synchronisation
            val syncJob = com.xburnsx.toutiebudget.data.room.entities.SyncJob(
                id = IdGenerator.generateId(),
                type = "TRANSACTION",
                action = "DELETE",
                dataJson = com.google.gson.Gson().toJson(mapOf("id" to transactionId)),
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
     * Extension function pour convertir une entité Room en modèle Transaction
     */
    private fun TransactionEntity.toTransactionModel(): Transaction {
        return Transaction(
            id = id,
            utilisateurId = utilisateurId,
            type = com.xburnsx.toutiebudget.data.modeles.TypeTransaction.depuisValeurPocketBase(type) ?: com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Depense,
            montant = montant,
            date = try { dateFormatter.parse(date) ?: Date() } catch (e: Exception) { Date() },
            note = note,
            compteId = compteId,
            collectionCompte = collectionCompte,
            allocationMensuelleId = allocationMensuelleId,
            estFractionnee = estFractionnee,
            sousItems = sousItems,
            tiersUtiliser = tiersUtiliser,
            created = created?.let { try { dateFormatter.parse(it) } catch (e: Exception) { null } },
            updated = updated?.let { try { dateFormatter.parse(it) } catch (e: Exception) { null } }
        )
    }
}
