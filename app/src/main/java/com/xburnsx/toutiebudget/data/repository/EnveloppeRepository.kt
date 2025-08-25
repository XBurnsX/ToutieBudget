package com.xburnsx.toutiebudget.data.repository

import com.xburnsx.toutiebudget.data.local.dao.EnveloppeDao
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import javax.inject.Inject

class EnveloppeRepository @Inject constructor(
    private val enveloppeDao: EnveloppeDao
) {
    suspend fun getAll(): List<Enveloppe> = enveloppeDao.getAll()
    
    suspend fun saveAll(enveloppes: List<Enveloppe>) = enveloppeDao.saveAll(enveloppes)
    
    suspend fun deleteById(id: String) = enveloppeDao.deleteById(id)
    
    suspend fun clearAll() = enveloppeDao.clearAll()
}
