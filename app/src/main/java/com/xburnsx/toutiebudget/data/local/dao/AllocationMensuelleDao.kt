package com.xburnsx.toutiebudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle

@Dao
interface AllocationMensuelleDao {
    @Query("SELECT * FROM allocations_mensuelles ORDER BY annee DESC, mois DESC")
    suspend fun getAll(): List<AllocationMensuelle>

    @Upsert
    suspend fun saveAll(allocations: List<AllocationMensuelle>)
      
    @Query("DELETE FROM allocations_mensuelles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM allocations_mensuelles")
    suspend fun clearAll()
}
