// chemin/simule: /domain/services/impl/RolloverServiceImpl.kt


package com.xburnsx.toutiebudget.domain.services.Impl

import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.domain.services.RolloverService
import com.xburnsx.toutiebudget.utils.OperationAtomique
import com.xburnsx.toutiebudget.utils.OperationAtomiqueDirecte
import java.util.Date

/**
 * Reporte le solde de chaque enveloppe (surplus ou déficit) du mois précédent vers le nouveau mois.
 *
 * Aucun argent ne disparaît : si le nouveau mois ne peut pas être écrit, l'ancien mois est remis tel
 * quel et le service rend un échec, pour que le rollover soit refait plus tard. Une enveloppe déjà
 * reportée (solde ancien à 0) est sautée, donc refaire le rollover ne double rien.
 *
 * GN3 : tout le report se fait dans UNE transaction Room. Une fermeture de l'app entre « ancien mois
 * à zéro » et « nouveau mois écrit » ne peut plus perdre l'argent : rien n'est gardé à moitié.
 */
class RolloverServiceImpl(
    private val enveloppeRepository: EnveloppeRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository,
    private val operationAtomique: OperationAtomique = OperationAtomiqueDirecte
) : RolloverService {
    override suspend fun effectuerRolloverMensuel(moisPrecedent: Date, nouveauMois: Date): Result<Unit> {
        return try { operationAtomique.executer {
            val allocationsPrecedentes = enveloppeRepository.recupererAllocationsPourMois(moisPrecedent).getOrThrow()
            var premiereErreur: Exception? = null

            for (allocationAncienne in allocationsPrecedentes) {
                // ⚠️ IGNORER les erreurs d'arrondi microscopiques (moins de 1 centime)
                if (kotlin.math.abs(allocationAncienne.solde) < 0.01) continue

                val avant = allocationMensuelleRepository.getAllocationById(allocationAncienne.id) ?: continue
                val montantATransferer = avant.solde // surplus (+) ou déficit (-)

                // 1. Ancien mois : solde remis à zéro, alloué = dépenses (pour couvrir les dépenses réelles).
                //    GN3 : le montant parti est noté sur le mois passé ; consulté plus tard, ce mois montre
                //    toujours son vrai solde de fin de mois (solde + reporté) sans double compter l'argent.
                allocationMensuelleRepository.mettreAJourAllocation(avant.copy(
                    solde = 0.0, alloue = avant.depense,
                    montantReporte = com.xburnsx.toutiebudget.utils.MoneyFormatter.roundAmount(avant.montantReporte + montantATransferer)
                ))

                // 2. Nouveau mois : le surplus ou le déficit, alloué mais pas encore dépensé
                try {
                    allocationMensuelleRepository.creerNouvelleAllocation(
                        allocationAncienne.copy(
                            id = "", // Nouveau ID généré
                            mois = nouveauMois,
                            solde = montantATransferer,
                            alloue = montantATransferer,
                            depense = 0.0,
                            montantReporte = 0.0
                        )
                    )
                } catch (e: Exception) {
                    // L'argent reste dans l'ancien mois ; le rollover sera refait
                    allocationMensuelleRepository.mettreAJourAllocation(avant)
                    if (premiereErreur == null) premiereErreur = e
                }
            }
            // Un échec annule toute la transaction (aucun mois modifié à moitié)
            premiereErreur?.let { throw it }
            Result.success(Unit)
        } } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
