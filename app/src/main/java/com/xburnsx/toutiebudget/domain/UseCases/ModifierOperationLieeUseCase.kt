package com.xburnsx.toutiebudget.domain.usecases

import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.PretPersonnelRepository
import com.xburnsx.toutiebudget.data.repositories.TransactionRepository
import com.xburnsx.toutiebudget.domain.services.ArgentService
import com.xburnsx.toutiebudget.domain.services.EcrituresLiees
import com.xburnsx.toutiebudget.domain.services.Impl.ArgentServiceImpl
import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
import com.xburnsx.toutiebudget.utils.IdGenerator
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import com.xburnsx.toutiebudget.utils.OperationAtomique
import com.xburnsx.toutiebudget.utils.OperationAtomiqueDirecte
import java.util.Date
import kotlin.math.abs

/**
 * MT5 / MT6 / MT7 : modifier le montant, la date ou la note d'une opération liée, sur place et en une seule
 * transaction Room.
 *
 * - Paiement de carte/dette (MT5) et virement (MT7) : les DEUX côtés sont défaits (comptes, enveloppe, dette),
 *   puis l'opération est refaite par le même service qu'à la création (mêmes validations : trop-payé, provenance,
 *   archivage), en réécrivant les deux mêmes écritures sous le même identifiant d'opération.
 * - Prêt / emprunt personnel et leurs remboursements (MT6) : l'écart de montant est appliqué au compte, à son
 *   Prêt à placer et au dossier du tiers ; jamais de prêt sous ce qui est déjà remboursé ni de trop-perçu.
 *
 * Les comptes d'une opération liée ne changent pas ici : pour changer de compte, supprimer puis refaire.
 */
class ModifierOperationLieeUseCase(
    private val transactionRepository: TransactionRepository,
    private val compteRepository: CompteRepository,
    private val pretPersonnelRepository: PretPersonnelRepository,
    private val supprimerTransactionUseCase: SupprimerTransactionUseCase,
    private val argentService: ArgentService,
    private val operationAtomique: OperationAtomique = OperationAtomiqueDirecte
) {

    enum class Genre { PAIEMENT, VIREMENT, PRET_PERSONNEL }

    fun genre(tx: Transaction): Genre? = when {
        tx.sousItems.orEmpty().contains("pret_personnel_id") -> Genre.PRET_PERSONNEL
        tx.type == TypeTransaction.TransfertSortant || tx.type == TypeTransaction.TransfertEntrant -> Genre.VIREMENT
        supprimerTransactionUseCase.estMoitieDePaiement(tx) -> Genre.PAIEMENT
        else -> null
    }

    suspend fun executer(transactionId: String, montant: Double, date: Date, note: String?): Result<Unit> = runCatching {
        require(montant.isFinite() && montant > 0.0) { "Le montant doit être positif." }
        val nouveauMontant = MoneyFormatter.roundAmount(montant)
        operationAtomique.executer {
            val tx = transactionRepository.recupererTransactionParId(transactionId).getOrThrow()
            when (genre(tx) ?: throw IllegalArgumentException("Cette écriture n'est pas une opération liée.")) {
                Genre.PAIEMENT -> modifierPaiementOuVirement(tx, nouveauMontant, date, note, virement = false)
                Genre.VIREMENT -> modifierPaiementOuVirement(tx, nouveauMontant, date, note, virement = true)
                Genre.PRET_PERSONNEL -> modifierPretPersonnel(tx, nouveauMontant, date, note)
            }
        }
        // Rafraîchir seulement après le commit
        BudgetEvents.refreshManual()
    }

    private suspend fun modifierPaiementOuVirement(tx: Transaction, montant: Double, date: Date, note: String?, virement: Boolean) {
        val autre = supprimerTransactionUseCase.trouverMoitieLiee(tx)
            ?: throw IllegalStateException("L'autre côté de cette opération est introuvable : rien n'a été modifié.")
        val sortie = if (virement) {
            listOf(tx, autre).single { it.type == TypeTransaction.TransfertSortant }
        } else {
            listOf(tx, autre).single { it.type == TypeTransaction.PaiementEffectue }
        }
        val entree = if (sortie.id == tx.id) autre else tx
        val operationId = idOperation(tx.sousItems) ?: idOperation(autre.sousItems) ?: IdGenerator.generateId()
        val ecritures = EcrituresLiees(sortie.id, entree.id, operationId, note?.takeIf { it.isNotBlank() })

        // 1. Défaire les deux côtés en gardant les écritures
        supprimerTransactionUseCase.executer(tx.id, garderEcriture = true).getOrThrow()

        // 2. Refaire l'opération avec les mêmes règles qu'à la création, sur les mêmes écritures
        if (virement) {
            val source = compteRepository.recupererCompteParIdToutesCollections(sortie.compteId).getOrThrow()
            val destination = compteRepository.recupererCompteParIdToutesCollections(entree.compteId).getOrThrow()
            argentService.effectuerVirementEntreComptes(
                source.id, destination.id, montant, source.nom, destination.nom, date, remplacer = ecritures
            ).getOrThrow()
        } else {
            argentService.effectuerPaiementCarteOuDette(
                compteQuiPaieId = sortie.compteId,
                collectionCompteQuiPaie = sortie.collectionCompte,
                carteOuDetteId = entree.compteId,
                collectionCarteOuDette = entree.collectionCompte,
                montant = montant,
                note = entree.tiersUtiliser,
                date = date,
                operationId = null,
                remplacer = ecritures
            ).getOrThrow()
        }
    }

    private suspend fun modifierPretPersonnel(tx: Transaction, montant: Double, date: Date, note: String?) {
        val idDossier = com.google.gson.JsonParser.parseString(tx.sousItems).asJsonObject.get("pret_personnel_id")?.asString
            ?: throw IllegalStateException("Dossier de prêt introuvable.")
        val dossier = pretPersonnelRepository.lister().getOrThrow().firstOrNull { it.id == idDossier }
            ?: throw IllegalStateException("Le dossier du tiers est introuvable : rien n'a été modifié.")
        val ecart = MoneyFormatter.roundAmount(montant - tx.montant)

        // Effet sur le compte (et son Prêt à placer, comme à l'enregistrement) et sur le dossier
        val (variationCompte, nouveauDossier) = when (tx.type) {
            TypeTransaction.Pret -> {
                val solde = MoneyFormatter.roundAmount(dossier.solde + ecart)
                require(solde >= -0.005) { "Le prêt ne peut pas descendre sous ce qui a déjà été remboursé." }
                -ecart to dossier.copy(montantInitial = MoneyFormatter.roundAmount(dossier.montantInitial + ecart), solde = solde)
            }
            TypeTransaction.Emprunt -> {
                val solde = MoneyFormatter.roundAmount(dossier.solde - ecart)
                require(solde <= 0.005) { "L'emprunt ne peut pas descendre sous ce qui a déjà été remboursé." }
                ecart to dossier.copy(montantInitial = MoneyFormatter.roundAmount(dossier.montantInitial + ecart), solde = solde)
            }
            TypeTransaction.RemboursementRecu -> {
                val solde = MoneyFormatter.roundAmount(dossier.solde - ecart)
                require(solde >= -0.005) { "Remboursement supérieur à ce qui reste dû par ${dossier.nomTiers}." }
                ecart to dossier.copy(solde = solde.coerceAtLeast(0.0), estArchive = solde < 0.005)
            }
            TypeTransaction.RemboursementDonne -> {
                val solde = MoneyFormatter.roundAmount(dossier.solde + ecart)
                require(solde <= 0.005) { "Remboursement supérieur à ce qui reste dû à ${dossier.nomTiers}." }
                -ecart to dossier.copy(solde = solde.coerceAtMost(0.0), estArchive = abs(solde) < 0.005)
            }
            else -> throw IllegalArgumentException("Type de prêt personnel non pris en charge.")
        }
        if (abs(variationCompte) >= 0.005) {
            compteRepository.mettreAJourSoldeAvecVariationEtPretAPlacer(tx.compteId, tx.collectionCompte, variationCompte, true).getOrThrow()
        }
        pretPersonnelRepository.mettreAJour(nouveauDossier).getOrThrow()
        transactionRepository.mettreAJourTransaction(
            tx.copy(montant = montant, date = date, note = note?.takeIf { it.isNotBlank() }, updated = Date())
        ).getOrThrow()
    }

    private fun idOperation(sousItems: String?): String? {
        if (sousItems.isNullOrBlank() || !sousItems.contains(ArgentServiceImpl.CLE_OPERATION_PAIEMENT)) return null
        return runCatching {
            com.google.gson.JsonParser.parseString(sousItems).asJsonObject.get(ArgentServiceImpl.CLE_OPERATION_PAIEMENT)?.asString
        }.getOrNull()
    }
}
