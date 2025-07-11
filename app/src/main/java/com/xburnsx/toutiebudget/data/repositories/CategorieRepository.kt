package com.xburnsx.toutiebudget.data.repositories

import com.xburnsx.toutiebudget.data.modeles.Categorie

interface CategorieRepository {
    suspend fun recupererToutesLesCategories(): Result<List<Categorie>>
    suspend fun creerCategorie(categorie: Categorie): Result<Unit>
    suspend fun supprimerCategorie(id: String): Result<Unit>
} 