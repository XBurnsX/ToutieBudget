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
     * Récupère les transactions d'un utilisateur pour une période donnée avec pagination.
     * @param debut Date de début de la période
     * @param fin Date de fin de la période
     * @param limit Nombre maximum de transactions à récupérer
     * @param offset Nombre de transactions à ignorer (pour la pagination)
     * @return Result contenant la liste des transactions, ou une erreur
     */
    suspend fun recupererTransactionsParPeriode(debut: Date, fin: Date, limit: Int, offset: Int): Result<List<Transaction>>
    
    /**
     * Récupère les transactions liées à un compte spécifique.
     * @param compteId ID du compte
     * @param collectionCompte Collection du compte (ex: "comptes_cheque")
     * @return Result contenant la liste des transactions, ou une erreur
     */
    suspend fun recupererTransactionsPourCompte(compteId: String, collectionCompte: String): Result<List<Transaction>>

    /**
     * HE14 : candidats paginés pour l'historique d'une enveloppe.
     * Le dépôt Room utilise une requête indexable pour les allocations et
     * inclut toutes les transactions fractionnées; le ViewModel vérifie
     * ensuite le contenu des fractions (y compris JSON ancien).
     * Fallback pour simulateurs et dépôts non-Room.
     */
    suspend fun recupererCandidatsHistoriqueEnveloppe(
        enveloppeId: String, limit: Int, offset: Int
    ): Result<List<Transaction>> = runCatching {
        require(enveloppeId.isNotBlank())
        require(limit in 1..500 && offset >= 0)
        recupererToutesLesTransactions().getOrThrow()
            .sortedWith(compareByDescending<Transaction> { it.date.time }.thenByDescending { it.id })
            .drop(offset).take(limit)
    }
    
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
     * HC5 : consultation de l'historique limitée à la session connectée.
     * La variante Room utilise utilisateur_id dans le SQL. Implémentation
     * par défaut compatible avec les dépôts de test et non-Room.
     * N'affecte pas les autres opérations existantes par ID.
     */
    /**
     * HC5/HC13 : jeton d'identité de lecture (null pour dépôts simulés).
     * Vérifier ce jeton avant/après un chargement empêche d'afficher un
     * ancien profil quand la session change pendant une requête Room.
     */
    fun identifiantSessionHistorique(): String? = null

    suspend fun recupererTransactionDeLaSession(transactionId: String): Result<Transaction> =
        runCatching {
            recupererToutesLesTransactions().getOrThrow()
                .firstOrNull { it.id == transactionId }
                ?: throw NoSuchElementException("Transaction absente de la session courante.")
        }
    
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