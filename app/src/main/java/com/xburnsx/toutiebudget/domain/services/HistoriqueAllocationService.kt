package com.xburnsx.toutiebudget.domain.services

import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.room.entities.HistoriqueAllocation
import com.xburnsx.toutiebudget.utils.IdGenerator
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service pour gérer l'historique des modifications d'allocations.
 * Enregistre automatiquement toutes les modifications d'allocations
 * pour permettre le suivi complet dans la page Historique.
 */
interface HistoriqueAllocationService {
    
    /**
     * Enregistre la création d'une nouvelle allocation.
     */
    suspend fun enregistrerCreationAllocation(
        allocation: AllocationMensuelle,
        compte: CompteCheque,
        enveloppe: Enveloppe,
        montant: Double,
        soldeAvant: Double,
        soldeApres: Double,
        pretAPlacerAvant: Double,
        pretAPlacerApres: Double
    )
    
    /**
     * Enregistre la modification d'une allocation existante.
     */
    suspend fun enregistrerModificationAllocation(
        allocationAvant: AllocationMensuelle,
        allocationApres: AllocationMensuelle,
        compte: CompteCheque,
        enveloppe: Enveloppe,
        montantModification: Double,
        soldeAvant: Double,
        soldeApres: Double,
        pretAPlacerAvant: Double,
        pretAPlacerApres: Double
    )
    
    /**
     * Enregistre la suppression d'une allocation.
     */
    suspend fun enregistrerSuppressionAllocation(
        allocation: AllocationMensuelle,
        compte: CompteCheque,
        enveloppe: Enveloppe,
        soldeAvant: Double,
        soldeApres: Double,
        pretAPlacerAvant: Double,
        pretAPlacerApres: Double
    )
    
    /**
     * Enregistre une transaction directe (sans allocation).
     */
    suspend fun enregistrerTransactionDirecte(
        compte: CompteCheque,
        enveloppe: Enveloppe?,
        typeTransaction: String,
        montant: Double,
        soldeAvant: Double,
        soldeApres: Double,
        pretAPlacerAvant: Double,
        pretAPlacerApres: Double,
        note: String?
    )

    /**
     * Trace d'un ajustement de compte (réconciliation ou modification manuelle), reconnaissable
     * comme tel et jamais comptée comme un revenu ou une dépense (CO5, CO9).
     * Contrairement aux autres méthodes, une erreur est relancée pour que l'appelant puisse annuler.
     */
    suspend fun enregistrerAjustementCompte(
        compte: com.xburnsx.toutiebudget.data.modeles.Compte,
        typeAction: String,
        description: String,
        montant: Double,
        soldeAvant: Double,
        soldeApres: Double,
        pretAPlacerAvant: Double,
        pretAPlacerApres: Double
    )

    /** GA5 : montant des déficits déjà couverts par les virements vers ces allocations (0 si inconnu). */
    suspend fun montantDejaCouvert(allocationIds: List<String>): Double = 0.0
}
