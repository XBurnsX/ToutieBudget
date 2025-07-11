// chemin/simule: /data/repositories/CompteRepository.kt
package com.xburnsx.toutiebudget.data.repositories

import com.xburnsx.toutiebudget.data.modeles.Compte

interface CompteRepository {
    suspend fun recupererTousLesComptes(): Result<List<Compte>>
    suspend fun creerCompte(compte: Compte): Result<Unit>
    suspend fun mettreAJourCompte(compte: Compte): Result<Unit>
    suspend fun supprimerCompte(compteId: String, collection: String): Result<Unit>
}
