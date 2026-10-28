package com.xburnsx.toutiebudget.data.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xburnsx.toutiebudget.data.room.entities.AliasBancaire
import kotlinx.coroutines.flow.Flow

@Dao
interface AliasBancaireDao {
    @Query("SELECT * FROM alias_bancaires WHERE utilisateur_id = :utilisateurId ORDER BY libelle_brut ASC")
    fun observerParUtilisateur(utilisateurId: String): Flow<List<AliasBancaire>>

    @Query("SELECT * FROM alias_bancaires WHERE utilisateur_id = :utilisateurId")
    suspend fun toutesParUtilisateur(utilisateurId: String): List<AliasBancaire>

    @Query("SELECT * FROM alias_bancaires WHERE id = :id")
    suspend fun trouverParId(id: String): AliasBancaire?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enregistrer(regle: AliasBancaire)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enregistrerToutes(regles: List<AliasBancaire>)

    @Query("DELETE FROM alias_bancaires WHERE id = :id AND utilisateur_id = :utilisateurId")
    suspend fun supprimer(id: String, utilisateurId: String): Int
}
