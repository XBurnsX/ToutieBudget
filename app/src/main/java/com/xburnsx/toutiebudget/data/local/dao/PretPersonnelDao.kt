package com.xburnsx.toutiebudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xburnsx.toutiebudget.data.modeles.PretPersonnel

@Dao
interface PretPersonnelDao {
    @Query("SELECT * FROM prets_personnels ORDER BY nom ASC")
    suspend fun getAll(): List<PretPersonnel>

    @Upsert
    suspend fun saveAll(prets: List<PretPersonnel>)
      
    @Query("DELETE FROM prets_personnels WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM prets_personnels")
    suspend fun clearAll()
}
