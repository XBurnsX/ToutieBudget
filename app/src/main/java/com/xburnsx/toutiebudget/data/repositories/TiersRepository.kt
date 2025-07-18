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
     */
    suspend fun recupererTousLesTiers(): Result<List<Tiers>>

    /**
     * Crée un nouveau tiers.
     * @param tiers Le tiers à créer
     */
    suspend fun creerTiers(tiers: Tiers): Result<Tiers>

    /**
     * @return Result contenant la liste des tiers correspondants
     */
}