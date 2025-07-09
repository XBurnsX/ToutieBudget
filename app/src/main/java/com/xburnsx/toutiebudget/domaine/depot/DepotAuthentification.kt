package com.xburnsx.toutiebudget.domaine.depot
import kotlinx.coroutines.flow.Flow

/**
 * Contrat pour la gestion de l'authentification.
 */
interface DepotAuthentification {
    fun recupererEtatAuth(): Flow<Boolean>
    suspend fun connexionAvecGoogle(idToken: String): Result<Unit>
    suspend fun deconnexion()
    val idUtilisateurActuel: String?
}