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
     * Met à jour une enveloppe existante.
     * @param enveloppe L'enveloppe à mettre à jour
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun mettreAJourEnveloppe(enveloppe: Enveloppe): Result<Unit>

    /** CE11/CE13/CA6 : réorganise avec vérification de conflit.
     * Room surcharge pour l'atomicité des changements et des SyncJobs. */
    suspend fun reorganiserEnveloppes(avant: List<Enveloppe>, apres: List<Enveloppe>): Result<Unit> =
        runCatching {
            val actuelles = recupererToutesLesEnveloppes().getOrThrow().associateBy { it.id }
            require(avant.all { actuelles[it.id] == it }) {
                "Les enveloppes ont changé sur un autre appareil. Recharge le classement."
            }
            require(avant.map { it.id }.toSet() == apres.map { it.id }.toSet())
            apres.filter { actuelles[it.id]?.ordre != it.ordre }
                .forEach { mettreAJourEnveloppe(it).getOrThrow() }
        }

    
    /**
     * Supprime une enveloppe par son ID.
     * @param id L'ID de l'enveloppe à supprimer
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun supprimerEnveloppe(id: String): Result<Unit>

    /**
     * CE2/CE5 : archivage explicite, jamais un DELETE.
     * Room surcharge et effectue la vérification des soldes et des paiements
     * dans la même transaction que l'UPDATE et le SyncJob.
     */
    suspend fun archiverEnveloppeApresVerification(id: String): Result<Unit> =
        runCatching {
            val actuelle = recupererToutesLesEnveloppes().getOrThrow()
                .firstOrNull { it.id == id }
                ?: error("Enveloppe introuvable.")
            val allocations = recupererAllocationsEnveloppe(id).getOrThrow()
            com.xburnsx.toutiebudget.ui.categories.VerificationArchivageEnveloppe
                .verifierSoldes(allocations.map { it.solde })
            if (!actuelle.estArchive) {
                mettreAJourEnveloppe(actuelle.copy(estArchive = true)).getOrThrow()
            }
        }
    
    // ===== NOUVELLES MÉTHODES POUR LES TRANSACTIONS =====
    
    /**
     * Ajoute une dépense à une allocation mensuelle.
     * Soustrait le montant du solde et l'ajoute aux dépenses.
     * @param allocationMensuelleId ID de l'allocation mensuelle
     * @param montantDepense Montant de la dépense
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun ajouterDepenseAllocation(allocationMensuelleId: String, montantDepense: Double): Result<Unit>
    
    /**
     * Annule une dépense sur une allocation mensuelle.
     * Ajoute le montant au solde et le soustrait des dépenses.
     * @param allocationMensuelleId ID de l'allocation mensuelle
     * @param montantDepense Montant de la dépense à annuler
     * @return Result indiquant le succès ou l'échec
     */
    suspend fun annulerDepenseAllocation(allocationMensuelleId: String, montantDepense: Double): Result<Unit>
    
    /**
     * Récupère une allocation mensuelle spécifique.
     * @param enveloppeId ID de l'enveloppe
     * @param mois Premier jour du mois concerné
     * @return Result contenant l'allocation ou null si non trouvée
     */
    suspend fun recupererAllocationMensuelle(enveloppeId: String, mois: Date): Result<AllocationMensuelle?>
    
    /**
     * Crée une nouvelle allocation mensuelle.
     * @param allocation L'allocation à créer
     * @return Result contenant l'allocation créée avec son ID
     */
    suspend fun creerAllocationMensuelle(allocation: AllocationMensuelle): Result<AllocationMensuelle>
    
    /**
     * Récupère les allocations par mois (alias pour compatibilité).
     * @param mois Le mois pour lequel récupérer les allocations
     * @return Result contenant la liste des allocations
     */
    suspend fun recupererAllocationsParMois(mois: Date): Result<List<AllocationMensuelle>> = recupererAllocationsPourMois(mois)

    /**
     * Récupère toutes les allocations mensuelles pour une enveloppe donnée.
     * @param enveloppeId ID de l'enveloppe
     * @return Result contenant la liste des allocations
     */
    suspend fun recupererAllocationsEnveloppe(enveloppeId: String): Result<List<AllocationMensuelle>>
    
    /**
     * Récupère une allocation mensuelle par son ID.
     * @param id ID de l'allocation mensuelle
     * @return Result contenant l'allocation
     */
    suspend fun recupererAllocationParId(id: String): Result<AllocationMensuelle>

    /**
     * Résolution groupée en lecture seule pour les historiques. Implémentation
     * par défaut compatible avec les simulateurs; Room utilise une requête IN.
     * Une allocation absente n'est pas inventée.
     */
    suspend fun recupererAllocationsParIds(ids: List<String>): Result<List<AllocationMensuelle>> =
        runCatching {
            ids.distinct().mapNotNull { id ->
                recupererAllocationParId(id).getOrNull()
            }
        }
}