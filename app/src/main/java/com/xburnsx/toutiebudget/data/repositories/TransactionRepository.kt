// chemin/simule: /data/repositories/TransactionRepository.kt
// Dépendances: Transaction.kt, java.util.Date

package com.xburnsx.toutiebudget.data.repositories

import com.xburnsx.toutiebudget.data.modeles.Transaction
import java.util.Date

/**
 * Interface du repository pour la gestion des transactions.
 * Définit les opérations de persistance des transactions financières.
 */
interface TransactionRepository {
    
    /**
     * Crée une nouvelle transaction dans la base de données.
     * @param transaction La transaction à enregistrer
     * @return Result contenant la transaction créée avec son ID, ou une erreur
     */
    suspend fun creerTransaction(transaction: Transaction): Result<Transaction>
    
    /**
     * Récupère toutes les transactions d'un utilisateur.
     * @return Result contenant la liste de toutes les transactions, ou une erreur
     */
    suspend fun recupererToutesLesTransactions(): Result<List<Transaction>>

    /**
     * Récupère toutes les transactions d'un utilisateur pour une période donnée.
     * @param debut Date de début de la période
     * @param fin Date de fin de la période
     * @return Result contenant la liste des transactions, ou une erreur
     */
    suspend fun recupererTransactionsParPeriode(debut: Date, fin: Date): Result<List<Transaction>>
    
    /**
     * Récupère les transactions liées à un compte spécifique.
     * @param compteId ID du compte
     * @param collectionCompte Collection du compte (ex: "comptes_cheque")
     * @return Result contenant la liste des transactions, ou une erreur
     */
    suspend fun recupererTransactionsPourCompte(compteId: String, collectionCompte: String): Result<List<Transaction>>
    
    /**
     * Récupère les transactions liées à une allocation mensuelle spécifique.
     * @param allocationId ID de l'allocation mensuelle
     * @return Result contenant la liste des transactions, ou une erreur
     */
    suspend fun recupererTransactionsParAllocation(allocationId: String): Result<List<Transaction>>
    
    /**
     * Récupère une transaction par son ID.
     * @param transactionId ID de la transaction à récupérer
     * @return Result contenant la transaction, ou une erreur
     */
    suspend fun recupererTransactionParId(transactionId: String): Result<Transaction>
    
    /**
     * Met à jour une transaction existante.
     * @param transaction La transaction mise à jour
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun mettreAJourTransaction(transaction: Transaction): Result<Unit>
    
    /**
     * Supprime une transaction.
     * @param transactionId ID de la transaction à supprimer
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun supprimerTransaction(transactionId: String): Result<Unit>
}