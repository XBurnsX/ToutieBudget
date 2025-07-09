package com.xburnsx.toutiebudget.domaine.depot
import com.xburnsx.toutiebudget.domaine.modele.Compte
import kotlinx.coroutines.flow.Flow

/**
 * Contrat pour la gestion des données des comptes.
 */
interface DepotCompte {
    fun recupererTousLesComptes(): Flow<List<Compte>>
}
