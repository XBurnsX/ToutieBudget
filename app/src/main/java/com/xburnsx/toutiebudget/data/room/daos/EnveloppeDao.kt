package com.xburnsx.toutiebudget.data.room.daos

import androidx.room.*
import com.xburnsx.toutiebudget.data.room.entities.Enveloppe
import kotlinx.coroutines.flow.Flow

@Dao
interface EnveloppeDao {
    
    @Query("SELECT * FROM enveloppes WHERE utilisateur_id = :utilisateurId ORDER BY ordre ASC")
    fun getEnveloppesByUtilisateur(utilisateurId: String): Flow<List<Enveloppe>>
    
    @Query("SELECT * FROM enveloppes WHERE id = :id")
    suspend fun getEnveloppeById(id: String): Enveloppe?
    
    @Query("SELECT * FROM enveloppes WHERE utilisateur_id = :utilisateurId AND est_archive = 0 ORDER BY ordre ASC")
    fun getEnveloppesActivesByUtilisateur(utilisateurId: String): Flow<List<Enveloppe>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEnveloppe(enveloppe: Enveloppe): Long
    
    @Update
    suspend fun updateEnveloppe(enveloppe: Enveloppe)
    
    @Delete
    suspend fun deleteEnveloppe(enveloppe: Enveloppe)
    
    @Query("DELETE FROM enveloppes WHERE id = :id")
    suspend fun deleteEnveloppeById(id: String)
    
    @Query("SELECT COUNT(*) FROM enveloppes WHERE utilisateur_id = :utilisateurId")
    suspend fun getEnveloppesCount(utilisateurId: String): Int
}
