package com.xburnsx.toutiebudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xburnsx.toutiebudget.data.modeles.Transaction

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY created DESC")
    suspend fun getAll(): List<Transaction>

    @Upsert
    suspend fun saveAll(transactions: List<Transaction>)
      
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}
