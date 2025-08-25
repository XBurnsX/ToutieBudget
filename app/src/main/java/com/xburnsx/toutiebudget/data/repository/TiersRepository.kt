package com.xburnsx.toutiebudget.data.repository

import com.xburnsx.toutiebudget.data.local.dao.TiersDao
import com.xburnsx.toutiebudget.data.modeles.Tiers
import javax.inject.Inject

class TiersRepository @Inject constructor(
    private val tiersDao: TiersDao
) {
    suspend fun getAll(): List<Tiers> = tiersDao.getAll()
    
    suspend fun saveAll(tiers: List<Tiers>) = tiersDao.saveAll(tiers)
    
    suspend fun deleteById(id: String) = tiersDao.deleteById(id)
    
    suspend fun clearAll() = tiersDao.clearAll()
}
