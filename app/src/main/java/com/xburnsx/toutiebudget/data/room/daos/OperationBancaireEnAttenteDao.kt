package com.xburnsx.toutiebudget.data.room.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xburnsx.toutiebudget.data.room.entities.OperationBancaireEnAttente
import kotlinx.coroutines.flow.Flow

@Dao
interface OperationBancaireEnAttenteDao {
    @Query("SELECT * FROM operations_bancaires_attente WHERE utilisateur_id = :utilisateurId AND decision = 'A_CONFIRMER' ORDER BY date_operation DESC, id ASC")
    fun observerAConfirmer(utilisateurId: String): Flow<List<OperationBancaireEnAttente>>

    @Query("SELECT * FROM operations_bancaires_attente WHERE id = :id AND utilisateur_id = :utilisateurId")
    suspend fun parId(id: String, utilisateurId: String): OperationBancaireEnAttente?

    @Query("SELECT * FROM operations_bancaires_attente WHERE utilisateur_id = :utilisateurId AND compte_id = :compteId AND source = :source AND id_externe = :idExterne LIMIT 1")
    suspend fun parCleExterne(
        utilisateurId: String, compteId: String, source: String, idExterne: String
    ): OperationBancaireEnAttente?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun ajouter(operation: OperationBancaireEnAttente)

    @Query("UPDATE operations_bancaires_attente SET statut_bancaire = :statutBancaire, date_operation = :dateOperation, montant_centimes = :montantCentimes, libelle_brut = :libelleBrut, verification_requise = :verificationRequise, montant_final_centimes = :montantFinalCentimes WHERE id = :id AND utilisateur_id = :utilisateurId")
    suspend fun actualiserImport(
        id: String, utilisateurId: String, statutBancaire: String,
        dateOperation: String, montantCentimes: Long, libelleBrut: String,
        verificationRequise: Boolean, montantFinalCentimes: Long?
    )

    @Query("UPDATE operations_bancaires_attente SET decision = :decision WHERE id = :id AND utilisateur_id = :utilisateurId AND decision IN ('A_CONFIRMER', 'IGNOREE')")
    suspend fun changerDecision(id: String, utilisateurId: String, decision: String): Int
}
