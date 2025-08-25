package com.xburnsx.toutiebudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xburnsx.toutiebudget.data.modeles.Enveloppe

@Dao
interface EnveloppeDao {
    @Query("SELECT * FROM enveloppes ORDER BY ordre ASC")
    suspend fun getAll(): List<Enveloppe>

    @Upsert
    suspend fun saveAll(enveloppes: List<Enveloppe>)
      
    @Query("DELETE FROM enveloppes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM enveloppes")
    suspend fun clearAll()
}
