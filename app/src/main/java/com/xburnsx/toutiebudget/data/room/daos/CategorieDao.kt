package com.xburnsx.toutiebudget.data.room.daos

import androidx.room.*
import com.xburnsx.toutiebudget.data.room.entities.Categorie
import kotlinx.coroutines.flow.Flow

@Dao
interface CategorieDao {
    
    @Query("SELECT * FROM categories WHERE utilisateur_id = :utilisateurId ORDER BY ordre ASC")
    fun getCategoriesByUtilisateur(utilisateurId: String): Flow<List<Categorie>>
    
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategorieById(id: String): Categorie?
    
    @Query("SELECT * FROM categories WHERE utilisateur_id = :utilisateurId ORDER BY ordre ASC")
    fun getCategoriesActivesByUtilisateur(utilisateurId: String): Flow<List<Categorie>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategorie(categorie: Categorie): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<Categorie>)
    
    @Update
    suspend fun updateCategorie(categorie: Categorie)
    
    @Delete
    suspend fun deleteCategorie(categorie: Categorie)
    
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategorieById(id: String)
    
    @Query("SELECT COUNT(*) FROM categories WHERE utilisateur_id = :utilisateurId")
    suspend fun getCategoriesCount(utilisateurId: String): Int
}
