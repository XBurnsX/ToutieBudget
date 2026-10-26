package com.xburnsx.toutiebudget.data.repositories

import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.Transaction

/**
 * Accès aux cartes de crédit. Les calculs (minimum, intérêts, simulations) sont dans
 * MoteurCarteCredit pour que tous les écrans donnent les mêmes chiffres (CC1).
 */
interface CarteCreditRepository {

    /** Toutes les cartes de crédit de l'utilisateur connecté. */
    suspend fun recupererCartesCredit(): Result<List<CompteCredit>>

    suspend fun creerCarteCredit(carteCredit: CompteCredit): Result<Unit>

    suspend fun mettreAJourCarteCredit(carteCredit: CompteCredit): Result<Unit>

    suspend fun supprimerCarteCredit(carteCreditId: String): Result<Unit>

    /** Transactions de la carte (achats, paiements, remboursements de marchands…). */
    suspend fun recupererHistoriqueTransactions(carteCreditId: String): Result<List<Transaction>>
}
