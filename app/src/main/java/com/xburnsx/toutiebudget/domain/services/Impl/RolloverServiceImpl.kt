// chemin/simule: /domain/services/impl/RolloverServiceImpl.kt


package com.xburnsx.toutiebudget.domain.services.Impl

import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.domain.services.RolloverService
import java.util.Date

/**
 * Reporte le solde de chaque enveloppe (surplus ou déficit) du mois précédent vers le nouveau mois.
 *
 * Aucun argent ne disparaît : si le nouveau mois ne peut pas être écrit, l'ancien mois est remis tel
 * quel et le service rend un échec, pour que le rollover soit refait plus tard. Une enveloppe déjà
 * reportée (solde ancien à 0) est sautée, donc refaire le rollover ne double rien.
 */
class RolloverServiceImpl(
    private val enveloppeRepository: EnveloppeRepository,
    private val allocationMensuelleRepository: AllocationMensuelleRepository
) : RolloverService {
    override suspend fun effectuerRolloverMensuel(moisPrecedent: Date, nouveauMois: Date): Result<Unit> {
        return try {
            val allocationsPrecedentes = enveloppeRepository.recupererAllocationsPourMois(moisPrecedent).getOrThrow()
            var premiereErreur: Exception? = null

            for (allocationAncienne in allocationsPrecedentes) {
                // ⚠️ IGNORER les erreurs d'arrondi microscopiques (moins de 1 centime)
                if (kotlin.math.abs(allocationAncienne.solde) < 0.01) continue

                val avant = allocationMensuelleRepository.getAllocationById(allocationAncienne.id) ?: continue
                val montantATransferer = avant.solde // surplus (+) ou déficit (-)

                // 1. Ancien mois : solde remis à zéro, alloué = dépenses (pour couvrir les dépenses réelles)
                allocationMensuelleRepository.mettreAJourAllocation(avant.copy(solde = 0.0, alloue = avant.depense))

                // 2. Nouveau mois : le surplus ou le déficit, alloué mais pas encore dépensé
                try {
                    allocationMensuelleRepository.creerNouvelleAllocation(
                        allocationAncienne.copy(
                            id = "", // Nouveau ID généré
                            mois = nouveauMois,
                            solde = montantATransferer,
                            alloue = montantATransferer,
                            depense = 0.0
                        )
                    )
                } catch (e: Exception) {
                    // L'argent reste dans l'ancien mois ; le rollover sera refait
                    allocationMensuelleRepository.mettreAJourAllocation(avant)
                    if (premiereErreur == null) premiereErreur = e
                }
            }
            premiereErreur?.let { Result.failure(it) } ?: Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
