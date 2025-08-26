package com.xburnsx.toutiebudget.data.room.daos

import androidx.room.*
import com.xburnsx.toutiebudget.data.room.entities.CompteInvestissement
import kotlinx.coroutines.flow.Flow

@Dao
interface CompteInvestissementDao {
    
    @Query("SELECT * FROM comptes_investissement WHERE utilisateur_id = :utilisateurId ORDER BY ordre ASC")
    fun getComptesByUtilisateur(utilisateurId: String): Flow<List<CompteInvestissement>>
    
    @Query("SELECT * FROM comptes_investissement WHERE id = :id")
    suspend fun getCompteById(id: String): CompteInvestissement?
    
    @Query("SELECT * FROM comptes_investissement WHERE utilisateur_id = :utilisateurId AND archive = 0 ORDER BY ordre ASC")
    fun getComptesActifsByUtilisateur(utilisateurId: String): Flow<List<CompteInvestissement>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompte(compte: CompteInvestissement): Long
    
    @Update
    suspend fun updateCompte(compte: CompteInvestissement)
    
    @Delete
    suspend fun deleteCompte(compte: CompteInvestissement)
    
    @Query("DELETE FROM comptes_investissement WHERE id = :id")
    suspend fun deleteCompteById(id: String)
    
    @Query("SELECT COUNT(*) FROM comptes_investissement WHERE utilisateur_id = :utilisateurId")
    suspend fun getComptesCount(utilisateurId: String): Int
}
