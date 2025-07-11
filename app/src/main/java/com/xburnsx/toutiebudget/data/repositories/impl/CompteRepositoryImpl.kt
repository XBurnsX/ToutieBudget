// chemin/simule: /data/repositories/impl/CompteRepositoryImpl.kt
package com.xburnsx.toutiebudget.data.repositories.impl

import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.repositories.CompteRepository

class CompteRepositoryImpl : CompteRepository {
    // Données simulées pour le développement
    private val comptesSimules = mutableListOf<Compte>(
        CompteCheque("cheque1", "user1", "Compte Principal", 1250.75, "#4CAF50", false, 1),
        CompteCheque("cheque2", "user1", "Fonds d'urgence", 875.20, "#2196F3", false, 2),
        CompteCredit("credit1", "user1", "Visa Odyssée", -450.50, "#F44336", false, 3, 5000.0)
    )

    override suspend fun recupererTousLesComptes(): Result<List<Compte>> {
        return Result.success(comptesSimules.filter { !it.estArchive }.sortedBy { it.ordre })
    }

    override suspend fun creerCompte(compte: Compte): Result<Unit> {
        comptesSimules.add(compte)
        return Result.success(Unit)
    }

    override suspend fun mettreAJourCompte(compte: Compte): Result<Unit> {
        val index = comptesSimules.indexOfFirst { it.id == compte.id }
        if (index != -1) {
            comptesSimules[index] = compte
        }
        return Result.success(Unit)
    }

    override suspend fun supprimerCompte(compteId: String, collection: String): Result<Unit> {
        comptesSimules.removeIf { it.id == compteId }
        return Result.success(Unit)
    }
}
