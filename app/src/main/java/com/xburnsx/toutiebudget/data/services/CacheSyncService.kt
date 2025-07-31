package com.xburnsx.toutiebudget.data.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Service de synchronisation du cache qui notifie les modifications
 * PRIORITÉ AUX MODIFICATIONS : Ce service s'assure que le cache est invalidé lors des modifications
 */
class CacheSyncService(
    private val cacheService: CacheService
) {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    enum class ChangeType {
        CREATED,
        UPDATED,
        DELETED
    }

    /**
     * Notifie une modification de catégorie
     */
    fun notifyCategorieChanged(changeType: ChangeType, categorieId: String) {
        serviceScope.launch {
            cacheService.notifyModification("categories")
            println("[CacheSyncService] 🔄 Modification catégorie détectée: $changeType - $categorieId")
        }
    }

    /**
     * Notifie une modification de tiers
     */
    fun notifyTiersChanged(changeType: ChangeType, tiersId: String) {
        serviceScope.launch {
            cacheService.notifyModification("tiers")
            println("[CacheSyncService] 🔄 Modification tiers détectée: $changeType - $tiersId")
        }
    }

    /**
     * Notifie une modification de compte
     */
    fun notifyCompteChanged(changeType: ChangeType, compteId: String) {
        serviceScope.launch {
            cacheService.notifyModification("comptes")
            println("[CacheSyncService] 🔄 Modification compte détectée: $changeType - $compteId")
        }
    }

    /**
     * Notifie une modification d'enveloppe
     */
    fun notifyEnveloppeChanged(changeType: ChangeType, enveloppeId: String) {
        serviceScope.launch {
            cacheService.notifyModification("enveloppes")
            println("[CacheSyncService] 🔄 Modification enveloppe détectée: $changeType - $enveloppeId")
        }
    }

    /**
     * Notifie une modification de transaction (invalide tous les caches liés)
     */
    fun notifyTransactionChanged(changeType: ChangeType, transactionId: String) {
        serviceScope.launch {
            // Les transactions affectent tous les caches car elles modifient les soldes
            cacheService.invalidateAllStaticCaches()
            println("[CacheSyncService] 🔄 Modification transaction détectée: $changeType - $transactionId")
        }
    }

    /**
     * Notifie une modification d'allocation (invalide tous les caches liés)
     */
    fun notifyAllocationChanged(changeType: ChangeType, allocationId: String) {
        serviceScope.launch {
            // Les allocations affectent tous les caches car elles modifient les soldes
            cacheService.invalidateAllStaticCaches()
            println("[CacheSyncService] 🔄 Modification allocation détectée: $changeType - $allocationId")
        }
    }
} 