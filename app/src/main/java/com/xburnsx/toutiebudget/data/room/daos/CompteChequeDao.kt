package com.xburnsx.toutiebudget.data.room.daos

import androidx.room.*
import com.xburnsx.toutiebudget.data.room.entities.CompteCheque
import kotlinx.coroutines.flow.Flow

@Dao
interface CompteChequeDao {
    
    @Query("SELECT * FROM comptes_cheques WHERE utilisateur_id = :utilisateurId ORDER BY ordre ASC")
    fun getComptesByUtilisateur(utilisateurId: String): Flow<List<CompteCheque>>
    
    @Query("SELECT * FROM comptes_cheques WHERE id = :id")
    suspend fun getCompteById(id: String): CompteCheque?
    
    @Query("SELECT * FROM comptes_cheques WHERE utilisateur_id = :utilisateurId AND archive = 0 ORDER BY ordre ASC")
    fun getComptesActifsByUtilisateur(utilisateurId: String): Flow<List<CompteCheque>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompte(compte: CompteCheque): Long
    
    @Update
    suspend fun updateCompte(compte: CompteCheque)
    
    @Delete
    suspend fun deleteCompte(compte: CompteCheque)
    
    @Query("DELETE FROM comptes_cheques WHERE id = :id")
    suspend fun deleteCompteById(id: String)
    
    @Query("SELECT COUNT(*) FROM comptes_cheques WHERE utilisateur_id = :utilisateurId")
    suspend fun getComptesCount(utilisateurId: String): Int
}
