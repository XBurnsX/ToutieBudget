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

    /** CA1/CE13 : modifier sans écraser un changement reçu depuis
     * l'ouverture du formulaire; Room revalide dans sa transaction. */
    suspend fun modifierCategorieSiInchangee(
        attendue: Categorie, nouvelle: Categorie
    ): Result<Categorie> = runCatching {
        require(attendue.id.isNotBlank() && attendue.id == nouvelle.id &&
            attendue.utilisateurId == nouvelle.utilisateurId) {
            "Identité de catégorie invalide."
        }
        val actuelle = recupererToutesLesCategories().getOrThrow()
            .firstOrNull { it.id == attendue.id }
            ?: throw NoSuchElementException("Catégorie supprimée.")
        require(actuelle == attendue) {
            "Catégorie modifiée ailleurs; actualise avant de recommencer."
        }
        if (actuelle == nouvelle) actuelle
        else mettreAJourCategorie(nouvelle).getOrThrow()
    }

    /** CE11/CE13/CA6 : vérifie l'instantané avant, n'écrit que les ordres changés.
     * Room surcharge pour exécuter ces changements ET les SyncJobs atomiquement. */
    suspend fun reorganiserCategories(avant: List<Categorie>, apres: List<Categorie>): Result<Unit> =
        runCatching {
            val actuelles = recupererToutesLesCategories().getOrThrow().associateBy { it.id }
            require(avant.all { actuelles[it.id] == it }) {
                "Les catégories ont changé sur un autre appareil. Recharge le classement."
            }
            require(avant.map { it.id }.toSet() == apres.map { it.id }.toSet())
            apres.filter { actuelles[it.id]?.ordre != it.ordre }
                .forEach { mettreAJourCategorie(it).getOrThrow() }
        }


    /**
     * Supprime une catégorie par son ID.
     * @param id L'ID de la catégorie à supprimer
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun supprimerCategorie(id: String): Result<Unit>
}