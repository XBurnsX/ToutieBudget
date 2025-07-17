// chemin/simule: /data/repositories/TiersRepository.kt
// Dépendances: Modèle Tiers

package com.xburnsx.toutiebudget.data.repositories

import com.xburnsx.toutiebudget.data.modeles.Tiers

/**
 * Interface du repository pour la gestion des tiers.
 * Définit les opérations CRUD pour les tiers.
 */
interface TiersRepository {
    
    /**
     * Récupère tous les tiers de l'utilisateur connecté.
     * @return Result contenant la liste des tiers
     */
    suspend fun recupererTousLesTiers(): Result<List<Tiers>>
    
    /**
     * Crée un nouveau tiers.
     * @param tiers Le tiers à créer
     * @return Result contenant le tiers créé avec son ID généré par PocketBase
     */
    suspend fun creerTiers(tiers: Tiers): Result<Tiers>
    
    /**
     * Recherche des tiers par nom (insensible à la casse).
     * @param recherche Le texte à rechercher dans les noms
     * @return Result contenant la liste des tiers correspondants
     */
    suspend fun rechercherTiers(recherche: String): Result<List<Tiers>>
}