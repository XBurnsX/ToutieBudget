package com.xburnsx.toutiebudget.data.services

import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
import java.time.LocalDate
import java.time.ZoneId

/**
 * CE18 : renouvellement automatique des dates d'objectifs.
 *
 * CRITIQUE : le renouvellement d'un objectif N'EST PAS une transaction
 * financière. L'ancien service modifiait les allocations.alloue des mois
 * existants, ce qui faisait disparaître l'historique des montants alloués.
 * Le budget reste maintenant la source unique des soldes, allocations et
 * dépenses; les renouvellements ne modifient que les échéances d'objectif.
 */
class ObjectifResetService(
    private val enveloppeRepository: EnveloppeRepository,
    @Suppress("UNUSED_PARAMETER")
    allocationMensuelleRepository: AllocationMensuelleRepository
) {
    private suspend fun renouveler(type: TypeObjectif): Result<List<Enveloppe>> =
        runCatching {
            val maintenant = LocalDate.now()
            val zone = ZoneId.systemDefault()
            val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
            val modifiees = mutableListOf<Enveloppe>()
            enveloppes.filter { it.typeObjectif == type && !it.estArchive }.forEach { ancienne ->
                val nouvelle = CalendrierRenouvellementObjectif.prochaineVersion(
                    ancienne, maintenant, zone
                )
                if (nouvelle != null && nouvelle != ancienne) {
                    // Un échec est une erreur, pas un renouvellement accompli.
                    enveloppeRepository.mettreAJourEnveloppe(nouvelle).getOrThrow()
                    modifiees += nouvelle
                }
            }
            modifiees
        }

    suspend fun verifierEtResetterObjectifsBihebdomadaires(): Result<List<Enveloppe>> =
        renouveler(TypeObjectif.Bihebdomadaire)

    suspend fun verifierEtResetterObjectifsAnnuels(): Result<List<Enveloppe>> =
        renouveler(TypeObjectif.Annuel)

    suspend fun verifierEtResetterObjectifsEcheance(): Result<List<Enveloppe>> =
        renouveler(TypeObjectif.Echeance)
}
