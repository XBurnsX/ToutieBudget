package com.xburnsx.toutiebudget.data.repository

import com.xburnsx.toutiebudget.data.local.dao.CategorieDao
import com.xburnsx.toutiebudget.data.modeles.Categorie
import javax.inject.Inject

class CategorieRepository @Inject constructor(
    private val categorieDao: CategorieDao
) {
    suspend fun getAll(): List<Categorie> = categorieDao.getAll()
    
    suspend fun saveAll(categories: List<Categorie>) = categorieDao.saveAll(categories)
    
    suspend fun deleteById(id: String) = categorieDao.deleteById(id)
    
    suspend fun clearAll() = categorieDao.clearAll()
}
