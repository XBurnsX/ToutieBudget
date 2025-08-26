package com.xburnsx.toutiebudget.data.room.daos

import androidx.room.*
import com.xburnsx.toutiebudget.data.room.entities.CompteCredit
import kotlinx.coroutines.flow.Flow

@Dao
interface CompteCreditDao {
    
    @Query("SELECT * FROM comptes_credits WHERE utilisateur_id = :utilisateurId ORDER BY ordre ASC")
    fun getComptesByUtilisateur(utilisateurId: String): Flow<List<CompteCredit>>
    
    @Query("SELECT * FROM comptes_credits WHERE id = :id")
    suspend fun getCompteById(id: String): CompteCredit?
    
    @Query("SELECT * FROM comptes_credits WHERE utilisateur_id = :utilisateurId AND archive = 0 ORDER BY ordre ASC")
    fun getComptesActifsByUtilisateur(utilisateurId: String): Flow<List<CompteCredit>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompte(compte: CompteCredit): Long
    
    @Update
    suspend fun updateCompte(compte: CompteCredit)
    
    @Delete
    suspend fun deleteCompte(compte: CompteCredit)
    
    @Query("DELETE FROM comptes_credits WHERE id = :id")
    suspend fun deleteCompteById(id: String)
    
    @Query("SELECT COUNT(*) FROM comptes_credits WHERE utilisateur_id = :utilisateurId")
    suspend fun getComptesCount(utilisateurId: String): Int
}
