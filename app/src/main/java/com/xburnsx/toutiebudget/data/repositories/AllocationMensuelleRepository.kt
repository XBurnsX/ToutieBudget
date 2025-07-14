package com.xburnsx.toutiebudget.data.repositories

import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import java.util.Date

/**
 * Repository pour gérer les allocations mensuelles (enveloppes pour un mois donné).
 */
interface AllocationMensuelleRepository {
    /**
     * Récupère une allocation mensuelle par son ID.
     */
    suspend fun getAllocationById(id: String): AllocationMensuelle?
    
    /**
     * Met à jour les montants d'une allocation mensuelle.
     */
    suspend fun mettreAJourAllocation(
        id: String,
        nouveauSolde: Double,
        nouvelleDepense: Double
    )
    
    /**
     * Récupère ou crée (s'il n'existe pas) une allocation mensuelle pour une enveloppe donnée.
     * @param enveloppeId ID de l'enveloppe concernée.
     * @param mois Premier jour du mois ciblé.
     */
    suspend fun getOrCreateAllocationMensuelle(enveloppeId: String, mois: Date): AllocationMensuelle
    
    /**
     * Met à jour l'allocation complète via l'objet, plus flexible que le patch partiel.
     */
    suspend fun mettreAJourAllocation(allocation: AllocationMensuelle)
    
    /**
     * Met à jour le compte source d'une allocation mensuelle.
     */
    suspend fun mettreAJourCompteSource(
        id: String,
        compteSourceId: String,
        collectionCompteSource: String
    )

    /**
     * Crée une nouvelle allocation mensuelle dans PocketBase.
     */
    suspend fun creerNouvelleAllocation(allocation: AllocationMensuelle): AllocationMensuelle
}
