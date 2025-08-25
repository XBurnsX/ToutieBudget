package com.xburnsx.toutiebudget.data.repository

import com.xburnsx.toutiebudget.data.local.dao.CompteDao
import com.xburnsx.toutiebudget.data.modeles.Compte
import javax.inject.Inject

class CompteRepository @Inject constructor(
    private val compteDao: CompteDao
) {
    suspend fun getAll(): List<Compte> = compteDao.getAll()
    
    suspend fun saveAll(comptes: List<Compte>) = compteDao.saveAll(comptes)
    
    suspend fun deleteById(id: String) = compteDao.deleteById(id)
    
    suspend fun clearAll() = compteDao.clearAll()
}
