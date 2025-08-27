package com.xburnsx.toutiebudget.data.room.daos

import androidx.room.*
import com.xburnsx.toutiebudget.data.room.entities.Tiers
import kotlinx.coroutines.flow.Flow

@Dao
interface TiersDao {
    
    @Query("SELECT * FROM tiers WHERE utilisateur_id = :utilisateurId ORDER BY nom ASC")
    fun getTiersByUtilisateur(utilisateurId: String): Flow<List<Tiers>>
    
    @Query("SELECT * FROM tiers WHERE id = :id")
    suspend fun getTiersById(id: String): Tiers?
    
    @Query("SELECT * FROM tiers WHERE utilisateur_id = :utilisateurId ORDER BY nom ASC")
    fun getTiersActifsByUtilisateur(utilisateurId: String): Flow<List<Tiers>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTiers(tiers: Tiers): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tiersList: List<Tiers>)
    
    @Update
    suspend fun updateTiers(tiers: Tiers)
    
    @Delete
    suspend fun deleteTiers(tiers: Tiers)
    
    @Query("DELETE FROM tiers WHERE id = :id")
    suspend fun deleteTiersById(id: String)
    
    @Query("SELECT COUNT(*) FROM tiers WHERE utilisateur_id = :utilisateurId")
    suspend fun getTiersCount(utilisateurId: String): Int
}
