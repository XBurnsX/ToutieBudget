package com.xburnsx.toutiebudget.data.room.daos

import androidx.room.*
import com.xburnsx.toutiebudget.data.room.entities.AllocationMensuelle
import kotlinx.coroutines.flow.Flow

@Dao
interface AllocationMensuelleDao {
    
    @Query("SELECT * FROM allocation_mensuelle WHERE utilisateur_id = :utilisateurId ORDER BY mois DESC")
    fun getAllocationsByUtilisateur(utilisateurId: String): Flow<List<AllocationMensuelle>>
    
    @Query("SELECT * FROM allocation_mensuelle WHERE id = :id")
    suspend fun getAllocationById(id: String): AllocationMensuelle?
    
    @Query("SELECT * FROM allocation_mensuelle WHERE utilisateur_id = :utilisateurId ORDER BY mois DESC")
    fun getAllocationsActivesByUtilisateur(utilisateurId: String): Flow<List<AllocationMensuelle>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllocation(allocation: AllocationMensuelle): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(allocations: List<AllocationMensuelle>)
    
    @Update
    suspend fun updateAllocation(allocation: AllocationMensuelle)
    
    @Delete
    suspend fun deleteAllocation(allocation: AllocationMensuelle)
    
    @Query("DELETE FROM allocation_mensuelle WHERE id = :id")
    suspend fun deleteAllocationById(id: String)
    
    @Query("SELECT COUNT(*) FROM allocation_mensuelle WHERE utilisateur_id = :utilisateurId")
    suspend fun getAllocationsCount(utilisateurId: String): Int
}
