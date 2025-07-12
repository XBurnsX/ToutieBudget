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
    suspend operator fun invoke(montant: Double, compteCible: Compte, collectionCompteCible: String, dateTransaction: Date, note: String?, tiers: String?): Result<Unit>
}
interface EnregistrerPretAccordeUseCase {
    suspend operator fun invoke(montant: Double, compteSource: Compte, collectionCompteSource: String, tiers: String?, note: String?): Result<Unit>
}
interface EnregistrerDetteContracteeUseCase {
    suspend operator fun invoke(montant: Double, compteCible: Compte, collectionCompteCible: String, tiers: String?, note: String?): Result<Unit>
}
interface EnregistrerPaiementDetteUseCase {
    suspend operator fun invoke(montant: Double, compteSource: Compte, collectionCompteSource: String, tiers: String?, note: String?): Result<Unit>
}

// --- Implémentations ---

class EnregistrerDepenseUseCaseImpl(private val argentService: ArgentService) : EnregistrerDepenseUseCase {
    override suspend fun invoke(montant: Double, allocationMensuelle: com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle, dateTransaction: Date, note: String?, tiers: String?) = argentService.enregistrerTransaction(
        type = "DEPENSE",
        montant = montant,
        date = dateTransaction,
        compteId = allocationMensuelle.compteSourceId ?: "", // Le compte de la dépense est celui de l'allocation
        collectionCompte = allocationMensuelle.collectionCompteSource ?: "",
        allocationMensuelleId = allocationMensuelle.id,
        note = note
    )
}

class EnregistrerRevenuUseCaseImpl(private val argentService: ArgentService) : EnregistrerRevenuUseCase {
    override suspend fun invoke(montant: Double, compteCible: Compte, collectionCompteCible: String, dateTransaction: Date, note: String?, tiers: String?) = argentService.enregistrerTransaction(
        type = "REVENU",
        montant = montant,
        date = dateTransaction,
        compteId = compteCible.id,
        collectionCompte = collectionCompteCible,
        note = note
    )
}

class EnregistrerPretAccordeUseCaseImpl(private val argentService: ArgentService) : EnregistrerPretAccordeUseCase {
    override suspend fun invoke(montant: Double, compteSource: Compte, collectionCompteSource: String, tiers: String?, note: String?) = argentService.enregistrerTransaction(
        type = "PRET",
        montant = montant,
        date = Date(), // Utilise la date actuelle
        compteId = compteSource.id,
        collectionCompte = collectionCompteSource,
        note = "Prêt à $tiers. ${note ?: ""}".trim()
    )
}

class EnregistrerDetteContracteeUseCaseImpl(private val argentService: ArgentService) : EnregistrerDetteContracteeUseCase {
    override suspend fun invoke(montant: Double, compteCible: Compte, collectionCompteCible: String, tiers: String?, note: String?) = argentService.enregistrerTransaction(
        type = "EMPRUNT",
        montant = montant,
        date = Date(), // Utilise la date actuelle
        compteId = compteCible.id,
        collectionCompte = collectionCompteCible,
        note = "Emprunt de $tiers. ${note ?: ""}".trim()
    )
}

class EnregistrerPaiementDetteUseCaseImpl(private val argentService: ArgentService) : EnregistrerPaiementDetteUseCase {
    // Note: Un paiement de dette est une dépense. Il pourrait être modélisé différemment,
    // mais pour l'instant on le traite comme une transaction de type DEPENSE.
    override suspend fun invoke(montant: Double, compteSource: Compte, collectionCompteSource: String, tiers: String?, note: String?) = argentService.enregistrerTransaction(
        type = "DEPENSE",
        montant = montant,
        date = Date(),
        compteId = compteSource.id,
        collectionCompte = collectionCompteSource,
        note = "Remboursement à $tiers. ${note ?: ""}".trim()
        // Pas d'allocation mensuelle ID car c'est un paiement direct depuis un compte.
    )
}

// --- Autres Use Cases ---

class CalculerTexteObjectifUseCase {
    operator fun invoke(enveloppe: Enveloppe): String? {
        return when (enveloppe.objectifType) {
            TypeObjectif.Aucun -> null
            TypeObjectif.Mensuel -> "${enveloppe.objectifMontant} $ nécessaire d'ici le ${enveloppe.objectifJour ?: 1}"
            TypeObjectif.Bihebdomadaire -> {
                val jourSemaine = when(enveloppe.objectifJour) {
                    1 -> "Lundi"; 2 -> "Mardi"; 3 -> "Mercredi"; 4 -> "Jeudi"; 5 -> "Vendredi"; 6 -> "Samedi"; 7 -> "Dimanche"; else -> ""
                }
                "${enveloppe.objectifMontant} $ d'ici le prochain $jourSemaine"
            }
            TypeObjectif.Echeance -> {
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
            TypeObjectif.Annuel -> {
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
                val moisPrecedentCal = Calendar.getInstance().apply { time = dernierRolloverCal.time; set(Calendar.DAY_OF_MONTH, 1) }
                rolloverService.effectuerRolloverMensuel(moisPrecedent = moisPrecedentCal.time, nouveauMois = aujourdhui.time).getOrThrow()
                preferenceRepository.sauvegarderDernierRollover(aujourdhui.time)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
