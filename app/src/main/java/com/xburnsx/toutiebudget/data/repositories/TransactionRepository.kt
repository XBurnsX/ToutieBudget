// chemin/simule: /data/repositories/TransactionRepository.kt
package com.xburnsx.toutiebudget.data.repositories

import com.xburnsx.toutiebudget.data.modeles.Transaction
import java.util.Date

interface TransactionRepository {
    suspend fun recupererTransactionsParPeriode(debut: Date, fin: Date): Result<List<Transaction>>
    suspend fun recupererTransactionsPourCompte(compteId: String, collectionCompte: String): Result<List<Transaction>>
    suspend fun creerTransaction(transaction: Transaction): Result<Unit>
}
