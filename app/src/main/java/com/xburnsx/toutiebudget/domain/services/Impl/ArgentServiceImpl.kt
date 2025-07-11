// chemin/simule: /domain/services/impl/ArgentServiceImpl.kt
package com.xburnsx.toutiebudget.domain.services.impl

import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.domain.services.ArgentService
import java.util.*

class ArgentServiceImpl(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val transactionRepository: TransactionRepository
) : ArgentService {

    override suspend fun allouerArgent(montant: Double, compteSource: Compte, enveloppeId: String, mois: Date): Result<Unit> {
        val allocationResult = enveloppeRepository.recupererOuCreerAllocation(enveloppeId, mois)
        val allocation = allocationResult.getOrNull() ?: return Result.failure(allocationResult.exceptionOrNull()!!)

        val estSourceCompatible = allocation.compteSourceId == null || allocation.compteSourceId == compteSource.id
        if (!estSourceCompatible) {
            return Result.failure(IllegalStateException("Opération refusée : L'enveloppe contient déjà de l'argent d'un autre compte."))
        }
        if (compteSource.solde < montant) {
            return Result.failure(IllegalStateException("Solde insuffisant sur le compte '${compteSource.nom}'."))
        }
        return try {
            val compteMaj = when (compteSource) {
                is CompteCheque -> compteSource.copy(solde = compteSource.solde - montant)
                is CompteCredit -> compteSource.copy(solde = compteSource.solde - montant)
            }
            compteRepository.mettreAJourCompte(compteMaj).getOrThrow()

            val allocationMaj = allocation.copy(
                solde = allocation.solde + montant,
                alloue = allocation.alloue + montant,
                compteSourceId = compteSource.id,
                collectionCompteSource = if (compteSource is CompteCheque) "comptes_cheque" else "comptes_credit"
            )
            enveloppeRepository.mettreAJourAllocation(allocationMaj).getOrThrow()

            val transaction = Transaction(UUID.randomUUID().toString(), compteSource.utilisateurId, TypeTransaction.ALLOCATION, montant, Date(), "Allocation vers enveloppe", compteSource.id, if (compteSource is CompteCheque) "comptes_cheque" else "comptes_credit", allocation.id)
            transactionRepository.creerTransaction(transaction).getOrThrow()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun enregistrerDepense(montant: Double, allocationMensuelle: AllocationMensuelle, dateTransaction: Date, note: String?, tiers: String?): Result<Unit> {
        val compteSourceId = allocationMensuelle.compteSourceId ?: return Result.failure(IllegalStateException("L'enveloppe est vide, impossible de dépenser."))
        val collectionCompte = allocationMensuelle.collectionCompteSource ?: return Result.failure(IllegalStateException("Collection de compte source inconnue."))

        if (allocationMensuelle.solde < montant) {
            return Result.failure(IllegalStateException("Solde insuffisant dans l'enveloppe."))
        }
        return try {
            var allocationMaj = allocationMensuelle.copy(
                solde = allocationMensuelle.solde - montant,
                depense = allocationMensuelle.depense + montant
            )
            if (allocationMaj.solde == 0.0) {
                allocationMaj = allocationMaj.copy(compteSourceId = null, collectionCompteSource = null)
            }
            enveloppeRepository.mettreAJourAllocation(allocationMaj).getOrThrow()

            val transaction = Transaction(UUID.randomUUID().toString(), allocationMensuelle.utilisateurId, TypeTransaction.DEPENSE, montant, dateTransaction, listOfNotNull(tiers, note).joinToString(" - "), compteSourceId, collectionCompte, allocationMensuelle.id)
            transactionRepository.creerTransaction(transaction).getOrThrow()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ... autres implémentations ...
    override suspend fun enregistrerRevenu(montant: Double, compteCible: Compte, dateTransaction: Date, note: String?, tiers: String?): Result<Unit> {TODO()}
    override suspend fun transfererEntreEnveloppes(montant: Double, allocationSource: AllocationMensuelle, allocationCible: AllocationMensuelle): Result<Unit> {TODO()}
    override suspend fun renvoyerEnveloppeVersCompte(montant: Double, allocationSource: AllocationMensuelle): Result<Unit> {TODO()}
    override suspend fun enregistrerPretAccorde(montant: Double, compteSource: Compte, tiers: String?, note: String?): Result<Unit> {TODO()}
    override suspend fun enregistrerDetteContractee(montant: Double, compteCible: Compte, tiers: String?, note: String?): Result<Unit> {TODO()}
    override suspend fun enregistrerPaiementDette(montant: Double, compteSource: Compte, tiers: String?, note: String?): Result<Unit> {TODO()}
}
