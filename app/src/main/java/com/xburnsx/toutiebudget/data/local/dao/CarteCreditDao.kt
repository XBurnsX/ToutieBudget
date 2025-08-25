package com.xburnsx.toutiebudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xburnsx.toutiebudget.data.modeles.CarteCredit

@Dao
interface CarteCreditDao {
    @Query("SELECT * FROM cartes_credit ORDER BY nom ASC")
    suspend fun getAll(): List<CarteCredit>

    @Upsert
    suspend fun saveAll(cartes: List<CarteCredit>)
      
    @Query("DELETE FROM cartes_credit WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cartes_credit")
    suspend fun clearAll()
}
