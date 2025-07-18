// filepath: c:\Users\XBurnsX\Desktop\Project\Kotlin\ToutieBudget2\app\src\main\java\com\xburnsx\toutiebudget\data\repositories\TiersRepository.kt
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
     * @return Result contenant la liste de tous les tiers
     */
    suspend fun recupererTousLesTiers(): Result<List<Tiers>>

    /**
     * Crée un nouveau tiers.
     * @param tiers Le tiers à créer
     * @return Result contenant le tiers créé avec son ID
     */
    suspend fun creerTiers(tiers: Tiers): Result<Tiers>

    /**
     * Recherche des tiers par nom (filtrage insensible à la casse).
     * @param recherche Le texte à rechercher dans les noms des tiers
     * @return Result contenant la liste des tiers correspondants
     */
    suspend fun rechercherTiersParNom(recherche: String): Result<List<Tiers>>

    /**
     * Met à jour un tiers existant.
     * @param tiers Le tiers avec les nouvelles données
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun mettreAJourTiers(tiers: Tiers): Result<Unit>

    /**
     * Supprime un tiers.
     * @param tiersId L'ID du tiers à supprimer
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun supprimerTiers(tiersId: String): Result<Unit>
}
