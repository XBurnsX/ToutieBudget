package com.xburnsx.toutiebudget.domain.services.Impl

import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.room.entities.HistoriqueAllocation
import com.xburnsx.toutiebudget.data.repositories.HistoriqueAllocationRepository
import com.xburnsx.toutiebudget.domain.services.HistoriqueAllocationService
import com.xburnsx.toutiebudget.utils.IdGenerator
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Implémentation du service pour gérer l'historique des modifications d'allocations.
 */
class HistoriqueAllocationServiceImpl @Inject constructor(
    private val historiqueAllocationRepository: HistoriqueAllocationRepository
) : HistoriqueAllocationService {
    
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    override suspend fun enregistrerCreationAllocation(
        allocation: AllocationMensuelle,
        compte: CompteCheque,
        enveloppe: Enveloppe,
        montant: Double,
        soldeAvant: Double,
        soldeApres: Double,
        pretAPlacerAvant: Double,
        pretAPlacerApres: Double
    ) {
            val historique = HistoriqueAllocation(
                id = IdGenerator.generateId(),
                utilisateurId = allocation.utilisateurId,
                compteId = compte.id,
                collectionCompte = "compte_cheque",
                enveloppeId = enveloppe.id,
                enveloppeNom = enveloppe.nom,
                typeAction = "CREATION",
                description = "Allocation créée: ${String.format("%.2f", montant)}$ vers ${enveloppe.nom}",
                montant = montant,
                soldeAvant = soldeAvant,
                soldeApres = soldeApres,
                pretAPlacerAvant = pretAPlacerAvant,
                pretAPlacerApres = pretAPlacerApres,
                dateAction = dateFormatter.format(Date()),
                allocationId = allocation.id,
                details = "Nouvelle allocation mensuelle créée"
            )
            
        historiqueAllocationRepository.insertHistorique(historique)
    }
    
    override suspend fun enregistrerModificationAllocation(
        allocationAvant: AllocationMensuelle,
        allocationApres: AllocationMensuelle,
        compte: CompteCheque,
        enveloppe: Enveloppe,
        montantModification: Double,
        soldeAvant: Double,
        soldeApres: Double,
        pretAPlacerAvant: Double,
        pretAPlacerApres: Double
    ) {
            val historique = HistoriqueAllocation(
                id = IdGenerator.generateId(),
                utilisateurId = allocationApres.utilisateurId,
                compteId = compte.id,
                collectionCompte = "compte_cheque",
                enveloppeId = enveloppe.id,
                enveloppeNom = enveloppe.nom,
                typeAction = "MODIFICATION",
                description = "Allocation modifiée: ${String.format("%.2f", montantModification)}$ vers ${enveloppe.nom}",
                montant = montantModification,
                soldeAvant = soldeAvant,
                soldeApres = soldeApres,
                pretAPlacerAvant = pretAPlacerAvant,
                pretAPlacerApres = pretAPlacerApres,
                dateAction = dateFormatter.format(Date()),
                allocationId = allocationApres.id,
                details = "Allocation mensuelle modifiée"
            )
            
        historiqueAllocationRepository.insertHistorique(historique)
    }
    
    override suspend fun enregistrerSuppressionAllocation(
        allocation: AllocationMensuelle,
        compte: CompteCheque,
        enveloppe: Enveloppe,
        soldeAvant: Double,
        soldeApres: Double,
        pretAPlacerAvant: Double,
        pretAPlacerApres: Double
    ) {
        val historique = HistoriqueAllocation(
            id = IdGenerator.generateId(),
            utilisateurId = allocation.utilisateurId,
            compteId = compte.id,
            collectionCompte = "compte_cheque",
            enveloppeId = enveloppe.id,
            enveloppeNom = enveloppe.nom,
            typeAction = "SUPPRESSION",
            description = "Allocation supprimée: ${String.format("%.2f", allocation.solde)}$ de ${enveloppe.nom}",
            montant = -allocation.solde,
            soldeAvant = soldeAvant,
            soldeApres = soldeApres,
            pretAPlacerAvant = pretAPlacerAvant,
            pretAPlacerApres = pretAPlacerApres,
            dateAction = dateFormatter.format(Date()),
            allocationId = allocation.id,
            details = "Allocation mensuelle supprimée"
        )
        
        historiqueAllocationRepository.insertHistorique(historique)
    }
    
    override suspend fun enregistrerTransactionDirecte(
        compte: CompteCheque,
        enveloppe: Enveloppe?,
        typeTransaction: String,
        montant: Double,
        soldeAvant: Double,
        soldeApres: Double,
        pretAPlacerAvant: Double,
        pretAPlacerApres: Double,
        note: String?
    ) {
            val description = if (enveloppe != null) {
                "Transaction $typeTransaction: ${String.format("%.2f", montant)}$ vers ${enveloppe.nom}"
            } else {
                "Transaction $typeTransaction: ${String.format("%.2f", montant)}$ ${note ?: ""}"
            }
            
            val historique = HistoriqueAllocation(
                id = IdGenerator.generateId(),
                utilisateurId = compte.utilisateurId,
                compteId = compte.id,
                collectionCompte = "compte_cheque",
                enveloppeId = enveloppe?.id ?: "",
                enveloppeNom = enveloppe?.nom ?: "Transaction directe",
                typeAction = "TRANSACTION",
                description = description,
                montant = montant,
                soldeAvant = soldeAvant,
                soldeApres = soldeApres,
                pretAPlacerAvant = pretAPlacerAvant,
                pretAPlacerApres = pretAPlacerApres,
                dateAction = dateFormatter.format(Date()),
                allocationId = "",
                details = note ?: "Transaction directe sur le compte"
            )
            
        historiqueAllocationRepository.insertHistorique(historique)
    }

    override suspend fun enregistrerAjustementCompte(
        compte: com.xburnsx.toutiebudget.data.modeles.Compte,
        typeAction: String,
        description: String,
        montant: Double,
        soldeAvant: Double,
        soldeApres: Double,
        pretAPlacerAvant: Double,
        pretAPlacerApres: Double
    ) {
        historiqueAllocationRepository.insertHistorique(
            HistoriqueAllocation(
                id = IdGenerator.generateId(),
                utilisateurId = compte.utilisateurId,
                compteId = compte.id,
                collectionCompte = compte.collection,
                enveloppeId = "",
                enveloppeNom = compte.nom,
                typeAction = typeAction,
                description = description,
                montant = montant,
                soldeAvant = soldeAvant,
                soldeApres = soldeApres,
                pretAPlacerAvant = pretAPlacerAvant,
                pretAPlacerApres = pretAPlacerApres,
                dateAction = dateFormatter.format(Date()),
                allocationId = "",
                details = description
            )
        )
    }
}
