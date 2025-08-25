package com.xburnsx.toutiebudget.data.repository

import com.xburnsx.toutiebudget.data.local.dao.AllocationMensuelleDao
import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import javax.inject.Inject

class AllocationMensuelleRepository @Inject constructor(
    private val allocationMensuelleDao: AllocationMensuelleDao
) {
    suspend fun getAll(): List<AllocationMensuelle> = allocationMensuelleDao.getAll()
    
    suspend fun saveAll(allocations: List<AllocationMensuelle>) = allocationMensuelleDao.saveAll(allocations)
    
    suspend fun deleteById(id: String) = allocationMensuelleDao.deleteById(id)
    
    suspend fun clearAll() = allocationMensuelleDao.clearAll()
}
