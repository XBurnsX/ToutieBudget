// chemin/simule: /data/repositories/CategorieRepository.kt
// Dépendances: Modèle Categorie

package com.xburnsx.toutiebudget.data.repositories

import com.xburnsx.toutiebudget.data.modeles.Categorie

/**
 * Interface du repository pour la gestion des catégories.
 * Définit les opérations CRUD pour les catégories.
 */
interface CategorieRepository {
    
    /**
     * Récupère toutes les catégories de l'utilisateur connecté.
     * @return Result contenant la liste des catégories
     */
    suspend fun recupererToutesLesCategories(): Result<List<Categorie>>
    
    /**
     * Crée une nouvelle catégorie.
     * @param categorie La catégorie à créer
     * @return Result contenant la catégorie créée avec son ID généré par PocketBase
     */
    suspend fun creerCategorie(categorie: Categorie): Result<Categorie>
    
    /**
     * Met à jour une catégorie existante.
     * @param categorie La catégorie à mettre à jour
     * @return Result contenant la catégorie mise à jour
     */
    suspend fun mettreAJourCategorie(categorie: Categorie): Result<Categorie>

    /**
     * Supprime une catégorie par son ID.
     * @param id L'ID de la catégorie à supprimer
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun supprimerCategorie(id: String): Result<Unit>
}