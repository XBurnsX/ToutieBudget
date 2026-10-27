package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import com.xburnsx.toutiebudget.utils.OperationAtomique
import com.xburnsx.toutiebudget.utils.OperationAtomiqueDirecte
import java.util.Calendar
import java.util.Date

/**
 * A8 / BUD-01 : refaire la répartition d'un mois sans effacer de transaction.
 *
 * Pour chaque enveloppe, seul l'argent ASSIGNÉ dans ce mois et pas encore dépensé est rendu au
 * « Prêt à placer » du compte d'où il vient. Le report des mois précédents (rollover), les
 * dépenses, les allocations des autres mois et les réservations futures ne bougent pas.
 * Rien n'est fait automatiquement : l'aperçu est montré puis l'utilisateur confirme.
 */
class PlanResetService(
    private val compteRepository: CompteRepository,
    private val enveloppeRepository: EnveloppeRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository,
    private val historiqueAllocationService: HistoriqueAllocationService,
    private val operationAtomique: OperationAtomique = OperationAtomiqueDirecte
) {

    data class LigneRetour(
        val allocationId: String,
        val enveloppeId: String,
        val nomEnveloppe: String,
        val compteId: String,
        val nomCompte: String,
        val montant: Double
    )

    data class Apercu(
        val mois: Date,
        val lignes: List<LigneRetour>,
        /** Enveloppes avec de l'argent assigné mais sans compte chèque d'origine connu : laissées telles quelles. */
        val ignorees: List<String>
    )

    fun premierJourDuMois(date: Date): Date = Calendar.getInstance().apply {
        time = date
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    /** Montant assigné ce mois-ci et pas encore dépensé (les dépenses consomment d'abord l'assignation du mois). */
    private fun montantRetournable(alloue: Double, depense: Double, solde: Double): Double =
        MoneyFormatter.roundAmount(minOf(maxOf(alloue - depense, 0.0), maxOf(solde, 0.0)))

    suspend fun preparerApercu(moisCible: Date): Result<Apercu> = runCatching {
        val mois = premierJourDuMois(moisCible)
        val comptes = compteRepository.recupererTousLesComptes().getOrThrow()
            .filterIsInstance<CompteCheque>().associateBy { it.id }
        val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow().associateBy { it.id }
        val allocations = enveloppeRepository.recupererAllocationsPourMois(mois).getOrThrow()

        val lignes = mutableListOf<LigneRetour>()
        val ignorees = mutableListOf<String>()
        // A8 : garder chaque allocation et SON compte d'origine distincts.
        // La somme de plusieurs sources ne peut pas être créditée à la dernière.
        allocations.forEach { allocation ->
            val montant = montantRetournable(allocation.alloue, allocation.depense, allocation.solde)
            if (montant < 0.01) return@forEach
            val nom = enveloppes[allocation.enveloppeId]?.nom ?: return@forEach
            val compte = allocation.compteSourceId?.let { comptes[it] }
            if (compte == null) {
                ignorees.add(nom)
            } else {
                lignes.add(LigneRetour(allocation.id, allocation.enveloppeId, nom, compte.id, compte.nom, montant))
            }
        }
        Apercu(mois, lignes.sortedBy { it.nomEnveloppe.lowercase() }, ignorees.sorted())
    }

    /**
     * Rend au « Prêt à placer » les enveloppes choisies. Chaque montant est recalculé sur les données
     * à jour juste avant l'écriture ; une enveloppe qui a changé depuis l'aperçu n'est jamais vidée au-delà.
     * Retourne les enveloppes traitées ; s'arrête à la première erreur en indiquant celles déjà faites.
     */
    suspend fun appliquer(mois: Date, enveloppeIds: Set<String>): Result<List<String>> = runCatching {
        val premierJour = premierJourDuMois(mois)
        val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow().associateBy { it.id }
        val allocations = enveloppeRepository.recupererAllocationsPourMois(premierJour).getOrThrow()
            .filter { it.enveloppeId in enveloppeIds }
        val faites = mutableListOf<String>()

        // A8 : réécriture de chaque allocation de la période et de chaque compte
        // au sein d'une seule transaction Room. Le journal fait partie du commit.
        operationAtomique.executer {
            allocations.forEach { avant ->
                val enveloppe = enveloppes[avant.enveloppeId]
                    ?: throw IllegalStateException("Enveloppe de l'allocation introuvable")
                val montant = montantRetournable(avant.alloue, avant.depense, avant.solde)
                if (montant < 0.01) return@forEach
                val compteId = avant.compteSourceId
                    ?: throw IllegalStateException("${enveloppe.nom} : provenance bancaire absente")
                val compte = compteRepository.recupererCompteParIdToutesCollections(compteId).getOrThrow()
                    as? CompteCheque ?: throw IllegalStateException("${enveloppe.nom} : compte d'origine invalide")
                val apres = avant.copy(
                    solde = MoneyFormatter.roundAmount(avant.solde - montant),
                    alloue = MoneyFormatter.roundAmount(avant.alloue - montant)
                )
                allocationMensuelleRepository.mettreAJourAllocation(apres)
                compteRepository.mettreAJourPretAPlacerSeulement(compte.id, montant).getOrThrow()
                historiqueAllocationService.enregistrerModificationAllocation(
                    allocationAvant = avant,
                    allocationApres = apres,
                    compte = compte,
                    enveloppe = enveloppe,
                    montantModification = -montant,
                    soldeAvant = compte.solde,
                    soldeApres = compte.solde,
                    pretAPlacerAvant = compte.pretAPlacer,
                    pretAPlacerApres = MoneyFormatter.roundAmount(compte.pretAPlacer + montant)
                )
                faites.add(enveloppe.nom)
            }
        }
        BudgetEvents.onAllocationUpdated()
        faites.distinct()
    }
}
