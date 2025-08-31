package com.xburnsx.toutiebudget.data.room.daos

import androidx.room.*
import com.xburnsx.toutiebudget.data.room.entities.Transaction
import kotlinx.coroutines.flow.Flow
@Dao
interface TransactionDao {
    
    @Query("SELECT * FROM transactions WHERE utilisateur_id = :utilisateurId ORDER BY date DESC")
    fun getTransactionsByUtilisateur(utilisateurId: String): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): Transaction?
    
    @Query("SELECT * FROM transactions WHERE utilisateur_id = :utilisateurId AND compte_id = :compteId ORDER BY date DESC")
    fun getTransactionsByUtilisateurAndCompte(utilisateurId: String, compteId: String): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE compte_id = :compteId AND collection_compte = :collectionCompte ORDER BY date DESC")
    fun getTransactionsByCompte(compteId: String, collectionCompte: String): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE utilisateur_id = :utilisateurId AND date BETWEEN :dateDebut AND :dateFin ORDER BY date DESC")
    fun getTransactionsByUtilisateurAndPeriod(utilisateurId: String, dateDebut: String, dateFin: String): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE utilisateur_id = :utilisateurId AND date BETWEEN :dateDebut AND :dateFin ORDER BY date DESC LIMIT :limit OFFSET :offset")
    fun getTransactionsByUtilisateurAndPeriodWithPagination(utilisateurId: String, dateDebut: String, dateFin: String, limit: Int, offset: Int): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE utilisateur_id = :utilisateurId AND allocation_mensuelle_id = :allocationId ORDER BY date DESC")
    fun getTransactionsByAllocation(utilisateurId: String, allocationId: String): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE allocation_mensuelle_id = :allocationId ORDER BY date DESC")
    fun getTransactionsByAllocation(allocationId: String): Flow<List<Transaction>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<Transaction>)
    
    @Update
    suspend fun updateTransaction(transaction: Transaction)
    
    @Delete
    suspend fun deleteTransaction(transaction: Transaction)
    
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)
    
    @Query("SELECT COUNT(*) FROM transactions WHERE utilisateur_id = :utilisateurId")
    suspend fun getTransactionsCount(utilisateurId: String): Int
}
