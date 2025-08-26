package com.xburnsx.toutiebudget.data.room.daos

import androidx.room.*
import com.xburnsx.toutiebudget.data.room.entities.CompteDette
import kotlinx.coroutines.flow.Flow

@Dao
interface CompteDetteDao {
    
    @Query("SELECT * FROM comptes_dettes WHERE utilisateur_id = :utilisateurId ORDER BY ordre ASC")
    fun getComptesByUtilisateur(utilisateurId: String): Flow<List<CompteDette>>
    
    @Query("SELECT * FROM comptes_dettes WHERE id = :id")
    suspend fun getCompteById(id: String): CompteDette?
    
    @Query("SELECT * FROM comptes_dettes WHERE utilisateur_id = :utilisateurId AND archive = 0 ORDER BY ordre ASC")
    fun getComptesActifsByUtilisateur(utilisateurId: String): Flow<List<CompteDette>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompte(compte: CompteDette): Long
    
    @Update
    suspend fun updateCompte(compte: CompteDette)
    
    @Delete
    suspend fun deleteCompte(compte: CompteDette)
    
    @Query("DELETE FROM comptes_dettes WHERE id = :id")
    suspend fun deleteCompteById(id: String)
    
    @Query("SELECT COUNT(*) FROM comptes_dettes WHERE utilisateur_id = :utilisateurId")
    suspend fun getComptesCount(utilisateurId: String): Int
}
