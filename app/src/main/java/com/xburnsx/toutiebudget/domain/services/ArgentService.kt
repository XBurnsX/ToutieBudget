// chemin/simule: /domain/services/ArgentService.kt
package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Compte
import java.util.Date

interface ArgentService {
    suspend fun allouerArgent(montant: Double, compteSource: Compte, enveloppeId: String, mois: Date): Result<Unit>
    suspend fun enregistrerDepense(montant: Double, allocationMensuelle: AllocationMensuelle, dateTransaction: Date, note: String?, tiers: String?): Result<Unit>
    suspend fun enregistrerRevenu(montant: Double, compteCible: Compte, dateTransaction: Date, note: String?, tiers: String?): Result<Unit>
    suspend fun transfererEntreEnveloppes(montant: Double, allocationSource: AllocationMensuelle, allocationCible: AllocationMensuelle): Result<Unit>
    suspend fun renvoyerEnveloppeVersCompte(montant: Double, allocationSource: AllocationMensuelle): Result<Unit>
    suspend fun enregistrerPretAccorde(montant: Double, compteSource: Compte, tiers: String?, note: String?): Result<Unit>
    suspend fun enregistrerDetteContractee(montant: Double, compteCible: Compte, tiers: String?, note: String?): Result<Unit>
    suspend fun enregistrerPaiementDette(montant: Double, compteSource: Compte, tiers: String?, note: String?): Result<Unit>
}
