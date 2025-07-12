package com.xburnsx.toutiebudget.data.repositories

import com.xburnsx.toutiebudget.data.modeles.Compte

interface CompteRepository {
    suspend fun recupererTousLesComptes(): Result<List<Compte>>
    suspend fun creerCompte(compte: Compte): Result<Unit>
    suspend fun mettreAJourCompte(compte: Compte): Result<Unit>
    suspend fun supprimerCompte(compteId: String, collection: String): Result<Unit>

    /**
     * Récupère un compte par son ID et sa collection.
     */
    suspend fun getCompteById(compteId: String, collection: String): Compte?

    /**
     * Met à jour le solde d'un compte.
     */
    suspend fun mettreAJourSolde(compteId: String, collection: String, nouveauSolde: Double)
}

