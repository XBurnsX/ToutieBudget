package com.xburnsx.toutiebudget.data.room.daos

import androidx.room.*
import com.xburnsx.toutiebudget.data.room.entities.PretPersonnel
import kotlinx.coroutines.flow.Flow

@Dao
interface PretPersonnelDao {
    
    @Query("SELECT * FROM pret_personnel WHERE utilisateur_id = :utilisateurId ORDER BY nom_tiers ASC")
    fun getPretsByUtilisateur(utilisateurId: String): Flow<List<PretPersonnel>>
    
    @Query("SELECT * FROM pret_personnel WHERE id = :id")
    suspend fun getPretById(id: String): PretPersonnel?
    
    @Query("SELECT * FROM pret_personnel WHERE utilisateur_id = :utilisateurId AND archive = 0 ORDER BY nom_tiers ASC")
    fun getPretsActifsByUtilisateur(utilisateurId: String): Flow<List<PretPersonnel>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPret(pret: PretPersonnel): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pretsPersonnels: List<PretPersonnel>)
    
    @Update
    suspend fun updatePret(pret: PretPersonnel)
    
    @Delete
    suspend fun deletePret(pret: PretPersonnel)
    
    @Query("DELETE FROM pret_personnel WHERE id = :id")
    suspend fun deletePretById(id: String)
    
    @Query("SELECT COUNT(*) FROM pret_personnel WHERE utilisateur_id = :utilisateurId")
    suspend fun getPretsCount(utilisateurId: String): Int
}
