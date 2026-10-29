package com.xburnsx.toutiebudget.data.repositories.impl

import com.xburnsx.toutiebudget.data.repositories.HistoriqueAllocationRepository
import com.xburnsx.toutiebudget.data.room.daos.HistoriqueAllocationDao
import com.xburnsx.toutiebudget.data.room.entities.HistoriqueAllocation
import kotlinx.coroutines.flow.Flow

/**
 * Implémentation Room du repository pour l'historique des allocations.
 * Utilise Room comme source de données principale.
 */
class HistoriqueAllocationRepositoryRoomImpl(
    private val historiqueAllocationDao: HistoriqueAllocationDao,
    private val syncJobDao: com.xburnsx.toutiebudget.data.room.daos.SyncJobDao? = null
) : HistoriqueAllocationRepository {

    override suspend fun page(compteId: String?, limite: Int, decalage: Int): List<HistoriqueAllocation> {
        val utilisateurId = com.xburnsx.toutiebudget.di.PocketBaseClient.obtenirUtilisateurConnecte()?.id
            ?: throw IllegalStateException("Connexion requise pour lire le journal.")
        return historiqueAllocationDao.page(utilisateurId, compteId, limite, decalage)
    }
    
    override fun getHistoriqueByCompte(compteId: String): Flow<List<HistoriqueAllocation>> {
        return historiqueAllocationDao.getHistoriqueByCompte(compteId)
    }
    
    override fun getHistoriqueByCompteWithLimit(compteId: String, limit: Int): Flow<List<HistoriqueAllocation>> {
        return historiqueAllocationDao.getHistoriqueByCompteWithLimit(compteId, limit)
    }
    
    override fun getHistoriqueByEnveloppe(enveloppeId: String): Flow<List<HistoriqueAllocation>> {
        return historiqueAllocationDao.getHistoriqueByEnveloppe(enveloppeId)
    }
    
    override fun getHistoriqueByAllocation(allocationId: String): Flow<List<HistoriqueAllocation>> {
        return historiqueAllocationDao.getHistoriqueByAllocation(allocationId)
    }
    
    override suspend fun parAllocations(ids: List<String>): List<HistoriqueAllocation> =
        ids.distinct().chunked(400).flatMap { historiqueAllocationDao.parAllocations(it) }

    override suspend fun insertHistorique(historique: HistoriqueAllocation) {
        // HG13 : le même événement n'est jamais journalisé deux fois
        if (historiqueAllocationDao.compterIdentiques(
                historique.utilisateurId, historique.compteId, historique.allocationId, historique.typeAction,
                historique.montant, historique.dateAction, historique.enveloppeId
            ) > 0
        ) return
        // HG4 : conservé sur le serveur pour les autres appareils (événement immuable, même ID partout)
        syncJobDao?.let {
            com.xburnsx.toutiebudget.data.services.SyncPayloads.enregistrer(it, "CREATE", historique, historique.id)
        }
        // HG3 : Room remonte ses exceptions à l'appelant. Ne jamais annoncer
        // un succès avant l'écriture, ni journaliser montants et noms privés.
        historiqueAllocationDao.insertHistorique(historique)
    }
    
    override suspend fun insertAllHistorique(historique: List<HistoriqueAllocation>) {
        historiqueAllocationDao.insertAllHistorique(historique)
    }
    
    override suspend fun deleteHistoriqueByCompte(compteId: String) {
        historiqueAllocationDao.deleteHistoriqueByCompte(compteId)
    }
    
    override suspend fun deleteHistoriqueByAllocation(allocationId: String) {
        historiqueAllocationDao.deleteHistoriqueByAllocation(allocationId)
    }
    
    override suspend fun deleteAllHistorique() {
        historiqueAllocationDao.deleteAllHistorique()
    }
    
    override suspend fun getCountByCompte(compteId: String): Int {
        return historiqueAllocationDao.getCountByCompte(compteId)
    }
    
    override fun getHistoriqueRecentByCompte(compteId: String): Flow<List<HistoriqueAllocation>> {
        return historiqueAllocationDao.getHistoriqueRecentByCompte(compteId)
    }
}
