// chemin/simule: /data/repositories/impl/TransactionRepositoryImpl.kt
package com.xburnsx.toutiebudget.data.repositories.impl

import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import java.util.Date
import java.util.UUID

class TransactionRepositoryImpl : TransactionRepository {
    private val transactionsSimulees = mutableListOf<Transaction>()

    init {
        // Pour la simulation, on ajoute quelques transactions pour le compte "cheque1"
        transactionsSimulees.add(
            Transaction(
                id = UUID.randomUUID().toString(),
                utilisateurId = "user1",
                type = TypeTransaction.DEPENSE,
                montant = 75.50,
                date = Date(),
                note = "Épicerie IGA - Lait, pain, oeufs",
                compteId = "cheque1",
                collectionCompte = "comptes_cheque",
                allocationMensuelleId = "alloc2"
            )
        )
        transactionsSimulees.add(
            Transaction(
                id = UUID.randomUUID().toString(),
                utilisateurId = "user1",
                type = TypeTransaction.DEPENSE,
                montant = 22.45,
                date = Date(System.currentTimeMillis() - 86400000 * 2), // Il y a 2 jours
                note = "Restaurant - Poutine",
                compteId = "cheque1",
                collectionCompte = "comptes_cheque",
                allocationMensuelleId = "alloc3" // Supposons une enveloppe loisirs
            )
        )
    }

    override suspend fun recupererTransactionsParPeriode(debut: Date, fin: Date): Result<List<Transaction>> {
        return Result.success(transactionsSimulees.filter { it.date in debut..fin })
    }

    override suspend fun recupererTransactionsPourCompte(compteId: String, collectionCompte: String): Result<List<Transaction>> {
        return Result.success(transactionsSimulees.filter { it.compteId == compteId })
    }

    override suspend fun creerTransaction(transaction: Transaction): Result<Unit> {
        // Ajoute au début de la liste pour que la nouvelle transaction apparaisse en premier
        transactionsSimulees.add(0, transaction)
        println("Transaction créée: $transaction")
        return Result.success(Unit)
    }
}
