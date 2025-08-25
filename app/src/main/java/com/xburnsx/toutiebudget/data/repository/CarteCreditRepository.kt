package com.xburnsx.toutiebudget.data.repository

import com.xburnsx.toutiebudget.data.local.dao.CarteCreditDao
import com.xburnsx.toutiebudget.data.modeles.CarteCredit
import javax.inject.Inject

class CarteCreditRepository @Inject constructor(
    private val carteCreditDao: CarteCreditDao
) {
    suspend fun getAll(): List<CarteCredit> = carteCreditDao.getAll()
    
    suspend fun saveAll(cartes: List<CarteCredit>) = carteCreditDao.saveAll(cartes)
    
    suspend fun deleteById(id: String) = carteCreditDao.deleteById(id)
    
    suspend fun clearAll() = carteCreditDao.clearAll()
}
