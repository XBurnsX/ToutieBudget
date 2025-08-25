package com.xburnsx.toutiebudget.data.repository

import com.xburnsx.toutiebudget.data.local.dao.TransactionDao
import com.xburnsx.toutiebudget.data.modeles.Transaction
import javax.inject.Inject

class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao
) {
    suspend fun getAll(): List<Transaction> = transactionDao.getAll()
    
    suspend fun saveAll(transactions: List<Transaction>) = transactionDao.saveAll(transactions)
    
    suspend fun deleteById(id: String) = transactionDao.deleteById(id)
    
    suspend fun clearAll() = transactionDao.clearAll()
}
