package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
import com.xburnsx.toutiebudget.utils.MoneyFormatter
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
    private val historiqueAllocationService: HistoriqueAllocationService
) {

    data class LigneRetour(
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
        allocations.groupBy { it.enveloppeId }.forEach { (enveloppeId, allocs) ->
            val montant = montantRetournable(allocs.sumOf { it.alloue }, allocs.sumOf { it.depense }, allocs.sumOf { it.solde })
            if (montant < 0.01) return@forEach
            val nom = enveloppes[enveloppeId]?.nom ?: return@forEach
            val compte = allocs.lastOrNull { it.compteSourceId != null }?.compteSourceId?.let { comptes[it] }
            if (compte == null) {
                ignorees.add(nom)
            } else {
                lignes.add(LigneRetour(enveloppeId, nom, compte.id, compte.nom, montant))
            }
        }
        Apercu(mois, lignes.sortedBy { it.nomEnveloppe.lowercase() }, ignorees.sorted())
    }

    /**
     * Rend au « Prêt à placer » les enveloppes choisies. Chaque montant est recalculé sur les données
     * à jour juste avant l'écriture ; une enveloppe qui a changé depuis l'aperçu n'est jamais vidée au-delà.
     * Retourne les enveloppes traitées ; s'arrête à la première erreur en indiquant celles déjà faites.
     */
    suspend fun appliquer(mois: Date, enveloppeIds: Set<String>): Result<List<String>> {
        val faites = mutableListOf<String>()
        return runCatching {
            val premierJour = premierJourDuMois(mois)
            val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow().associateBy { it.id }

            enveloppeIds.forEach { enveloppeId ->
                val enveloppe = enveloppes[enveloppeId] ?: return@forEach
                val avant = allocationMensuelleRepository.recupererOuCreerAllocation(enveloppeId, premierJour)
                val montant = montantRetournable(avant.alloue, avant.depense, avant.solde)
                if (montant < 0.01) return@forEach

                val compte = avant.compteSourceId?.let { compteRepository.recupererCompteParIdToutesCollections(it).getOrNull() }
                    as? CompteCheque
                    ?: throw IllegalStateException("${enveloppe.nom} : compte d'origine introuvable")

                val apres = avant.copy(
                    solde = MoneyFormatter.roundAmount(avant.solde - montant),
                    alloue = MoneyFormatter.roundAmount(avant.alloue - montant)
                )
                allocationMensuelleRepository.mettreAJourAllocation(apres)
                compteRepository.mettreAJourPretAPlacerSeulement(compte.id, montant).getOrThrow()

                try {
                    historiqueAllocationService.enregistrerModificationAllocation(
                        allocationAvant = avant,
                        allocationApres = apres,
                        compte = compte,
                        enveloppe = enveloppe,
                        montantModification = -montant,
                        soldeAvant = compte.solde,
                        soldeApres = compte.solde, // Le solde bancaire ne change pas
                        pretAPlacerAvant = compte.pretAPlacer,
                        pretAPlacerApres = compte.pretAPlacer + montant
                    )
                } catch (_: Exception) {
                    // L'historique ne doit pas annuler un retour déjà enregistré
                }
                faites.add(enveloppe.nom)
            }
            BudgetEvents.onAllocationUpdated()
            faites.toList()
        }.recoverCatching { e ->
            if (faites.isNotEmpty()) BudgetEvents.onAllocationUpdated()
            throw if (faites.isEmpty()) e else Exception("Déjà remises : ${faites.joinToString()}. Arrêt : ${e.message}")
        }
    }
}
