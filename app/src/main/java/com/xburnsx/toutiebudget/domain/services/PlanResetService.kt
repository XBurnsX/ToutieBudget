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
        val montant: Double,
        val soldeAvant: Double,
        val alloueAvant: Double,
        val depenseAvant: Double
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
    private fun montantRetournable(alloue: Double, depense: Double, solde: Double): Double {
        if (!alloue.isFinite() || !depense.isFinite() || !solde.isFinite()) {
            throw IllegalStateException("Allocation invalide : les montants ne sont pas finis.")
        }
        return MoneyFormatter.roundAmount(minOf(maxOf(alloue - maxOf(depense, 0.0), 0.0), maxOf(solde, 0.0)))
    }

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
            val nom = enveloppes[allocation.enveloppeId]?.nom
            if (nom == null) {
                ignorees.add("Enveloppe supprimée (${allocation.enveloppeId})")
                return@forEach
            }
            val compte = allocation.compteSourceId?.let { comptes[it] }
            if (compte == null || compte.estArchive) {
                ignorees.add(nom)
            } else {
                lignes.add(LigneRetour(
                    allocation.id, allocation.enveloppeId, nom, compte.id, compte.nom,
                    montant, allocation.solde, allocation.alloue, allocation.depense
                ))
            }
        }
        Apercu(mois, lignes.sortedBy { it.nomEnveloppe.lowercase() }, ignorees.sorted())
    }


    /**
     * BUD-01 : ne valider QUE les allocations explicitement prévisualisées et choisies.
     * Les IDs sont propres à chaque provenance, même si plusieurs allocations portent
     * le même nom d'enveloppe. Le compte et le montant de l'aperçu doivent être identiques
     * au moment de l'écriture; autrement la confirmation est périmée et tout est annulé.
     */
    suspend fun appliquer(apercu: Apercu, allocationIds: Set<String>): Result<List<String>> = runCatching {
        require(allocationIds.isNotEmpty()) { "Aucune allocation sélectionnée." }
        val lignesParId = apercu.lignes.associateBy { it.allocationId }
        require(allocationIds.all { it in lignesParId }) {
            "Sélection invalide : recharge l'aperçu avant de confirmer."
        }
        val faites = mutableListOf<String>()
        operationAtomique.executer {
            // TOUTES les validations et écritures sont sous la même transaction Room.
            val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes()
                .getOrThrow().associateBy { it.id }
            val allocations = enveloppeRepository.recupererAllocationsPourMois(apercu.mois)
                .getOrThrow().associateBy { it.id }
            for (allocationId in allocationIds) {
                val ligne = lignesParId.getValue(allocationId)
                val avant = allocations[allocationId]
                    ?: throw IllegalStateException("L'allocation a disparu : recharge l'aperçu.")
                val enveloppe = enveloppes[avant.enveloppeId]
                    ?: throw IllegalStateException("Enveloppe introuvable : recharge l'aperçu.")
                val montant = montantRetournable(avant.alloue, avant.depense, avant.solde)
                if (avant.enveloppeId != ligne.enveloppeId ||
                    avant.compteSourceId != ligne.compteId ||
                    MoneyFormatter.roundAmount(avant.solde) != MoneyFormatter.roundAmount(ligne.soldeAvant) ||
                    MoneyFormatter.roundAmount(avant.alloue) != MoneyFormatter.roundAmount(ligne.alloueAvant) ||
                    MoneyFormatter.roundAmount(avant.depense) != MoneyFormatter.roundAmount(ligne.depenseAvant) ||
                    MoneyFormatter.roundAmount(montant) != MoneyFormatter.roundAmount(ligne.montant)
                ) throw IllegalStateException(
                    "La répartition a changé depuis l'aperçu pour « ${enveloppe.nom} ». Recharge avant de confirmer."
                )
                if (montant <= 0.0) throw IllegalStateException("Montant à remettre devenu nul.")
                val compte = compteRepository.recupererCompteParIdToutesCollections(ligne.compteId)
                    .getOrThrow() as? CompteCheque
                    ?: throw IllegalStateException("Compte source invalide : recharge l'aperçu.")
                if (compte.estArchive) throw IllegalStateException(
                    "Le compte « ${compte.nom} » est archivé; réactivation requise."
                )
                if (!compte.pretAPlacer.isFinite()) throw IllegalStateException("Solde du compte invalide.")
                val nouveauPretAPlacer = MoneyFormatter.roundAmount(compte.pretAPlacer + montant)
                if (!nouveauPretAPlacer.isFinite()) throw IllegalStateException("Montant du compte trop élevé.")

                // L'alloué du mois est le seul montant diminué : pas de suppression
                // de transactions, de dépenses, des autres mois ou du rollover.
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
                    pretAPlacerApres = nouveauPretAPlacer
                )
                faites.add(enveloppe.nom)
            }
        }
        BudgetEvents.onAllocationUpdated()
        faites.distinct()
    }

    /** Compatibilité avec les appels métier existants sélectionnant des enveloppes. */
    suspend fun appliquer(mois: Date, enveloppeIds: Set<String>): Result<List<String>> {
        return preparerApercu(mois).fold(
            onSuccess = { apercu ->
                val ids = apercu.lignes
                    .filter { it.enveloppeId in enveloppeIds }
                    .map { it.allocationId }.toSet()
                if (ids.isEmpty()) Result.success(emptyList()) else appliquer(apercu, ids)
            },
            onFailure = { Result.failure(it) }
        )
    }

}
