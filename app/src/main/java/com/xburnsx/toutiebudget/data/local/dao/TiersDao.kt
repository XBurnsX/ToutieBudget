package com.xburnsx.toutiebudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xburnsx.toutiebudget.data.modeles.Tiers

@Dao
interface TiersDao {
    @Query("SELECT * FROM tiers ORDER BY nom ASC")
    suspend fun getAll(): List<Tiers>

    @Upsert
    suspend fun saveAll(tiers: List<Tiers>)
      
    @Query("DELETE FROM tiers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tiers")
    suspend fun clearAll()
}
