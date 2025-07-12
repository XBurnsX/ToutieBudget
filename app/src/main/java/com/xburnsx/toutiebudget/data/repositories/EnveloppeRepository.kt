// chemin/simule: /data/repositories/EnveloppeRepository.kt
// Dépendances: Modèles AllocationMensuelle et Enveloppe

package com.xburnsx.toutiebudget.data.repositories

import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import java.util.Date

/**
 * Interface du repository pour la gestion des enveloppes et allocations.
 * Définit les opérations CRUD pour les enveloppes et leurs allocations mensuelles.
 */
interface EnveloppeRepository {
    
    /**
     * Récupère toutes les enveloppes de l'utilisateur connecté.
     * @return Result contenant la liste des enveloppes
     */
    suspend fun recupererToutesLesEnveloppes(): Result<List<Enveloppe>>
    
    /**
     * Récupère les allocations mensuelles pour un mois donné.
     * @param mois Le mois pour lequel récupérer les allocations
     * @return Result contenant la liste des allocations
     */
    suspend fun recupererAllocationsPourMois(mois: Date): Result<List<AllocationMensuelle>>
    
    /**
     * Met à jour une allocation mensuelle.
     * @param allocation L'allocation à mettre à jour
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun mettreAJourAllocation(allocation: AllocationMensuelle): Result<Unit>
    
    /**
     * Crée une nouvelle enveloppe.
     * @param enveloppe L'enveloppe à créer
     * @return Result contenant l'enveloppe créée avec son ID généré par PocketBase
     */
    suspend fun creerEnveloppe(enveloppe: Enveloppe): Result<Enveloppe>
    
    /**
     * Récupère ou crée une allocation pour une enveloppe et un mois donnés.
     * @param enveloppeId L'ID de l'enveloppe
     * @param mois Le mois pour l'allocation
     * @return Result contenant l'allocation trouvée ou créée
     */
    suspend fun recupererOuCreerAllocation(enveloppeId: String, mois: Date): Result<AllocationMensuelle>
    
    /**
     * Met à jour une enveloppe existante.
     * @param enveloppe L'enveloppe à mettre à jour
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun mettreAJourEnveloppe(enveloppe: Enveloppe): Result<Unit>
    
    /**
     * Supprime une enveloppe par son ID.
     * @param id L'ID de l'enveloppe à supprimer
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun supprimerEnveloppe(id: String): Result<Unit>
}