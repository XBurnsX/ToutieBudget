package com.xburnsx.toutiebudget.data.repository

import com.xburnsx.toutiebudget.data.local.dao.PretPersonnelDao
import com.xburnsx.toutiebudget.data.modeles.PretPersonnel
import javax.inject.Inject

class PretPersonnelRepository @Inject constructor(
    private val pretPersonnelDao: PretPersonnelDao
) {
    suspend fun getAll(): List<PretPersonnel> = pretPersonnelDao.getAll()
    
    suspend fun saveAll(prets: List<PretPersonnel>) = pretPersonnelDao.saveAll(prets)
    
    suspend fun deleteById(id: String) = pretPersonnelDao.deleteById(id)
    
    suspend fun clearAll() = pretPersonnelDao.clearAll()
}
