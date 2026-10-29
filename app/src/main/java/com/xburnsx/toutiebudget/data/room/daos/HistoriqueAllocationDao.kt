package com.xburnsx.toutiebudget.data.room.daos

import androidx.room.*
import com.xburnsx.toutiebudget.data.room.entities.HistoriqueAllocation
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour la gestion de l'historique des allocations.
 * Permet de récupérer l'historique complet des modifications d'allocations
 * pour un compte donné, trié par date.
 */
@Dao
interface HistoriqueAllocationDao {

    /**
     * HG5/HG17 : une page du journal de l'utilisateur connecté SEULEMENT (compte facultatif),
     * trié du plus récent au plus ancien, avec un départage stable par ID.
     */
    @Query("""
        SELECT * FROM historique_allocation
        WHERE utilisateurId = :utilisateurId AND (:compteId IS NULL OR compteId = :compteId)
        ORDER BY dateAction DESC, id DESC
        LIMIT :limite OFFSET :decalage
    """)
    suspend fun page(utilisateurId: String, compteId: String?, limite: Int, decalage: Int): List<HistoriqueAllocation>

    /** HG13 : même événement déjà journalisé (même seconde, même effet) ? */
    @Query("""
        SELECT COUNT(*) FROM historique_allocation
        WHERE utilisateurId = :utilisateurId AND compteId = :compteId AND allocationId = :allocationId
          AND typeAction = :typeAction AND montant = :montant AND dateAction = :dateAction
          AND enveloppeId = :enveloppeId
    """)
    suspend fun compterIdentiques(
        utilisateurId: String, compteId: String, allocationId: String, typeAction: String,
        montant: Double, dateAction: String, enveloppeId: String
    ): Int

    /** HG4/HG5 : suppression venue du serveur, jamais pour un autre utilisateur. */
    @Query("DELETE FROM historique_allocation WHERE id = :id AND utilisateurId = :utilisateurId")
    suspend fun supprimer(id: String, utilisateurId: String)

    /** GA5 : entrées d'historique des allocations données (un mois du budget). */
    @Query("SELECT * FROM historique_allocation WHERE allocationId IN (:ids)")
    suspend fun parAllocations(ids: List<String>): List<HistoriqueAllocation>

    
    /**
     * Récupère tout l'historique des allocations pour un compte donné,
     * trié par date décroissante (plus récent en premier).
     */
    @Query("""
        SELECT * FROM historique_allocation 
        WHERE compteId = :compteId 
        ORDER BY dateAction DESC
    """)
    fun getHistoriqueByCompte(compteId: String): Flow<List<HistoriqueAllocation>>
    
    /**
     * Récupère l'historique des allocations pour un compte donné avec une limite,
     * trié par date décroissante.
     */
    @Query("""
        SELECT * FROM historique_allocation 
        WHERE compteId = :compteId 
        ORDER BY dateAction DESC 
        LIMIT :limit
    """)
    fun getHistoriqueByCompteWithLimit(compteId: String, limit: Int): Flow<List<HistoriqueAllocation>>
    
    /**
     * Récupère l'historique des allocations pour une enveloppe donnée,
     * trié par date décroissante.
     */
    @Query("""
        SELECT * FROM historique_allocation 
        WHERE enveloppeId = :enveloppeId 
        ORDER BY dateAction DESC
    """)
    fun getHistoriqueByEnveloppe(enveloppeId: String): Flow<List<HistoriqueAllocation>>
    
    /**
     * Récupère l'historique des allocations pour une allocation donnée,
     * trié par date décroissante.
     */
    @Query("""
        SELECT * FROM historique_allocation 
        WHERE allocationId = :allocationId 
        ORDER BY dateAction DESC
    """)
    fun getHistoriqueByAllocation(allocationId: String): Flow<List<HistoriqueAllocation>>
    
    /**
     * Insère un nouvel enregistrement d'historique.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistorique(historique: HistoriqueAllocation)
    
    /**
     * Insère plusieurs enregistrements d'historique en une seule opération.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllHistorique(historique: List<HistoriqueAllocation>)
    
    /**
     * Supprime l'historique pour un compte donné.
     */
    @Query("DELETE FROM historique_allocation WHERE compteId = :compteId")
    suspend fun deleteHistoriqueByCompte(compteId: String)
    
    /**
     * Supprime l'historique pour une allocation donnée.
     */
    @Query("DELETE FROM historique_allocation WHERE allocationId = :allocationId")
    suspend fun deleteHistoriqueByAllocation(allocationId: String)
    
    /**
     * Supprime tout l'historique.
     */
    @Query("DELETE FROM historique_allocation")
    suspend fun deleteAllHistorique()
    
    /**
     * Récupère le nombre total d'enregistrements d'historique pour un compte.
     */
    @Query("SELECT COUNT(*) FROM historique_allocation WHERE compteId = :compteId")
    suspend fun getCountByCompte(compteId: String): Int
    
    /**
     * Récupère l'historique récent (dernières 24h) pour un compte.
     */
    @Query("""
        SELECT * FROM historique_allocation 
        WHERE compteId = :compteId 
        AND dateAction >= datetime('now', '-1 day')
        ORDER BY dateAction DESC
    """)
    fun getHistoriqueRecentByCompte(compteId: String): Flow<List<HistoriqueAllocation>>
}
