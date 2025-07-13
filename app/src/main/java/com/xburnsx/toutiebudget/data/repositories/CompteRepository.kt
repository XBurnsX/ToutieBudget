// chemin/simule: /data/repositories/CompteRepository.kt
// Dépendances: Modèle Compte

package com.xburnsx.toutiebudget.data.repositories

import com.xburnsx.toutiebudget.data.modeles.Compte

/**
 * Interface du repository pour la gestion des comptes.
 * Définit les opérations CRUD pour tous types de comptes.
 */
interface CompteRepository {
    
    /**
     * Récupère tous les comptes de l'utilisateur connecté.
     * @return Result contenant la liste de tous les comptes
     */
    suspend fun recupererTousLesComptes(): Result<List<Compte>>
    
    /**
     * Crée un nouveau compte.
     * @param compte Le compte à créer
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun creerCompte(compte: Compte): Result<Unit>
    
    /**
     * Met à jour un compte existant.
     * @param compte Le compte avec les nouvelles données
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun mettreAJourCompte(compte: Compte): Result<Unit>
    
    /**
     * Supprime un compte.
     * @param compteId ID du compte à supprimer
     * @param collection Collection du compte (ex: "comptes_cheque")
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun supprimerCompte(compteId: String, collection: String): Result<Unit>

    /**
     * Récupère un compte par son ID et sa collection.
     * @param compteId ID du compte
     * @param collection Collection du compte
     * @return Le compte trouvé ou null
     */
    suspend fun getCompteById(compteId: String, collection: String): Compte?

    /**
     * Met à jour le solde d'un compte (ancienne méthode).
     * @param compteId ID du compte
     * @param collection Collection du compte
     * @param nouveauSolde Le nouveau solde
     */
    suspend fun mettreAJourSolde(compteId: String, collection: String, nouveauSolde: Double)
    
    // ===== NOUVELLES MÉTHODES POUR LES TRANSACTIONS =====
    
    /**
     * Met à jour le solde d'un compte en ajoutant la variation spécifiée.
     * Utilisée pour les transactions (dépenses/revenus).
     * @param compteId ID du compte à modifier
     * @param collectionCompte Collection du compte (ex: "comptes_cheque")
     * @param variationSolde Montant à ajouter/soustraire du solde (peut être négatif)
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun mettreAJourSoldeAvecVariation(compteId: String, collectionCompte: String, variationSolde: Double): Result<Unit>

    /**
     * Met à jour le solde d'un compte avec gestion intelligente du "prêt à placer".
     * Pour les comptes chèque, met à jour aussi pret_a_placer selon le type de transaction.
     * @param compteId ID du compte à modifier
     * @param collectionCompte Collection du compte (ex: "comptes_cheque")
     * @param variationSolde Montant à ajouter/soustraire du solde (peut être négatif)
     * @param mettreAJourPretAPlacer Si true, met aussi à jour pret_a_placer (pour revenus, transferts entrants, etc.)
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun mettreAJourSoldeAvecVariationEtPretAPlacer(
        compteId: String,
        collectionCompte: String,
        variationSolde: Double,
        mettreAJourPretAPlacer: Boolean
    ): Result<Unit>
    
    /**
     * Récupère un compte spécifique par son ID et sa collection.
     * Version pour les transactions avec gestion d'erreurs.
     * @param compteId ID du compte
     * @param collectionCompte Collection du compte
     * @return Result contenant le compte ou une erreur
     */
    suspend fun recupererCompteParId(compteId: String, collectionCompte: String): Result<Compte>
}