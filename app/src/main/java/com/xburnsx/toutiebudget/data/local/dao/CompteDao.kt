package com.xburnsx.toutiebudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xburnsx.toutiebudget.data.modeles.Compte

@Dao
interface CompteDao {
    @Query("SELECT * FROM comptes WHERE estArchive = 0 ORDER BY ordre ASC")
    suspend fun getAll(): List<Compte>

    @Upsert
    suspend fun saveAll(comptes: List<Compte>)
      
    @Query("DELETE FROM comptes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM comptes")
    suspend fun clearAll()
}
