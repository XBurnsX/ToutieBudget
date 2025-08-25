package com.xburnsx.toutiebudget.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.xburnsx.toutiebudget.data.modeles.Categorie

@Dao
interface CategorieDao {
    @Query("SELECT * FROM categories ORDER BY ordre ASC")
    suspend fun getAll(): List<Categorie>

    @Upsert
    suspend fun saveAll(categories: List<Categorie>)
      
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM categories")
    suspend fun clearAll()
}
