// chemin/simule: /domain/usecases/UseCases.kt
package com.xburnsx.toutiebudget.domain.usecases

import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.domain.services.ArgentService
import com.xburnsx.toutiebudget.domain.services.RolloverService
import com.xburnsx.toutiebudget.data.repositories.PreferenceRepository
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// --- Interfaces ---

interface EnregistrerDepenseUseCase {
    suspend operator fun invoke(montant: Double, allocationMensuelle: com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle, dateTransaction: Date, note: String?, tiers: String?): Result<Unit>
}
interface EnregistrerRevenuUseCase {
    suspend operator fun invoke(montant: Double, compteCible: Compte, dateTransaction: Date, note: String?, tiers: String?): Result<Unit>
}
interface EnregistrerPretAccordeUseCase {
    suspend operator fun invoke(montant: Double, compteSource: Compte, tiers: String?, note: String?): Result<Unit>
}
interface EnregistrerDetteContracteeUseCase {
    suspend operator fun invoke(montant: Double, compteCible: Compte, tiers: String?, note: String?): Result<Unit>
}
interface EnregistrerPaiementDetteUseCase {
    suspend operator fun invoke(montant: Double, compteSource: Compte, tiers: String?, note: String?): Result<Unit>
}

// --- Implémentations ---

class EnregistrerDepenseUseCaseImpl(private val argentService: ArgentService) : EnregistrerDepenseUseCase {
    override suspend fun invoke(montant: Double, allocationMensuelle: com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle, dateTransaction: Date, note: String?, tiers: String?) =
        argentService.enregistrerDepense(montant, allocationMensuelle, dateTransaction, note, tiers)
}
class EnregistrerRevenuUseCaseImpl(private val argentService: ArgentService) : EnregistrerRevenuUseCase {
    override suspend fun invoke(montant: Double, compteCible: Compte, dateTransaction: Date, note: String?, tiers: String?) =
        argentService.enregistrerRevenu(montant, compteCible, dateTransaction, note, tiers)
}
class EnregistrerPretAccordeUseCaseImpl(private val argentService: ArgentService) : EnregistrerPretAccordeUseCase {
    override suspend fun invoke(montant: Double, compteSource: Compte, tiers: String?, note: String?) =
        argentService.enregistrerPretAccorde(montant, compteSource, tiers, note)
}
class EnregistrerDetteContracteeUseCaseImpl(private val argentService: ArgentService) : EnregistrerDetteContracteeUseCase {
    override suspend fun invoke(montant: Double, compteCible: Compte, tiers: String?, note: String?) =
        argentService.enregistrerDetteContractee(montant, compteCible, tiers, note)
}
class EnregistrerPaiementDetteUseCaseImpl(private val argentService: ArgentService) : EnregistrerPaiementDetteUseCase {
    override suspend fun invoke(montant: Double, compteSource: Compte, tiers: String?, note: String?) =
        argentService.enregistrerPaiementDette(montant, compteSource, tiers, note)
}

// --- Autres Use Cases ---

class CalculerTexteObjectifUseCase {
    operator fun invoke(enveloppe: Enveloppe): String? {
        return when (enveloppe.objectifType) {
            TypeObjectif.AUCUN -> null
            TypeObjectif.MENSUEL -> "${enveloppe.objectifMontant} $ nécessaire d'ici le ${enveloppe.objectifJour ?: 1}"
            TypeObjectif.BIHEBDOMADAIRE -> {
                val jourSemaine = when(enveloppe.objectifJour) {
                    1 -> "Lundi"; 2 -> "Mardi"; 3 -> "Mercredi"; 4 -> "Jeudi"; 5 -> "Vendredi"; 6 -> "Samedi"; 7 -> "Dimanche"; else -> ""
                }
                "${enveloppe.objectifMontant} $ d'ici le prochain $jourSemaine"
            }
            TypeObjectif.ECHEANCE -> {
                val dateFin = enveloppe.objectifDate ?: return null
                val aujourdhui = Date()
                if (aujourdhui.after(dateFin)) return "Échéance passée"
                val diffMillis = dateFin.time - aujourdhui.time
                val diffJours = TimeUnit.MILLISECONDS.toDays(diffMillis)
                val moisRestants = (diffJours / 30.0).coerceAtLeast(1.0)
                val montantParMois = enveloppe.objectifMontant / moisRestants
                val montantFormatte = String.format(Locale.CANADA_FRENCH, "%.2f", montantParMois)
                "$montantFormatte $ nécessaire ce mois-ci"
            }
            TypeObjectif.ANNUEL -> {
                val montantParMois = enveloppe.objectifMontant / 12.0
                val montantFormatte = String.format(Locale.CANADA_FRENCH, "%.2f", montantParMois)
                "$montantFormatte $ nécessaire ce mois-ci"
            }
        }
    }
}

class VerifierEtExecuterRolloverUseCase(
    private val rolloverService: RolloverService,
    private val preferenceRepository: PreferenceRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return try {
            val aujourdhui = Calendar.getInstance()
            val dernierRolloverCal = Calendar.getInstance()
            val dernierRolloverDate = preferenceRepository.recupererDernierRollover()

            if (dernierRolloverDate != null) {
                dernierRolloverCal.time = dernierRolloverDate
            } else {
                dernierRolloverCal.add(Calendar.MONTH, -1)
            }

            val anneeActuelle = aujourdhui.get(Calendar.YEAR)
            val moisActuel = aujourdhui.get(Calendar.MONTH)
            val anneeDernierRollover = dernierRolloverCal.get(Calendar.YEAR)
            val moisDernierRollover = dernierRolloverCal.get(Calendar.MONTH)

            if (anneeActuelle > anneeDernierRollover || (anneeActuelle == anneeDernierRollover && moisActuel > moisDernierRollover)) {
                println("Nouveau mois détecté. Lancement du rollover...")
                val moisPrecedentCal = Calendar.getInstance().apply { time = dernierRolloverCal.time; set(Calendar.DAY_OF_MONTH, 1) }
                rolloverService.effectuerRolloverMensuel(moisPrecedent = moisPrecedentCal.time, nouveauMois = aujourdhui.time).getOrThrow()
                preferenceRepository.sauvegarderDernierRollover(aujourdhui.time)
                println("Rollover terminé et date sauvegardée.")
            } else {
                println("Rollover non nécessaire pour ce mois.")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
