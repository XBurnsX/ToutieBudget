package com.xburnsx.toutiebudget.data.repositories.impl

import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.repositories.CarteCreditRepository
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Les intérêts ESTIMÉS ne sont jamais ajoutés automatiquement au solde (CC5) :
 * seule une confirmation explicite depuis un relevé officiel le fait (CC6).
 */
class CarteCreditRepositoryImpl(
    private val compteRepository: CompteRepository,
    private val transactionRepository: TransactionRepository
) : CarteCreditRepository {

    override suspend fun recupererCartesCredit(): Result<List<CompteCredit>> = withContext(Dispatchers.IO) {
        runCatching {
            compteRepository.recupererTousLesComptes().getOrThrow().filterIsInstance<CompteCredit>()
        }
    }

    override suspend fun creerCarteCredit(carteCredit: CompteCredit): Result<Unit> =
        compteRepository.creerCompte(carteCredit)

    override suspend fun mettreAJourCarteCredit(carteCredit: CompteCredit): Result<Unit> =
        compteRepository.mettreAJourCompte(carteCredit)

    override suspend fun supprimerCarteCredit(carteCreditId: String): Result<Unit> =
        compteRepository.supprimerCompte(carteCreditId, "comptes_credits")

    override suspend fun recupererHistoriqueTransactions(carteCreditId: String): Result<List<Transaction>> =
        withContext(Dispatchers.IO) {
            transactionRepository.recupererTransactionsPourCompte(carteCreditId, "comptes_credits")
        }
}
