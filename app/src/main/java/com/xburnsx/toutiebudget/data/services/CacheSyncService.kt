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
        }
    }

    /**
     * Notifie une modification de tiers
     */
    fun notifyTiersChanged(changeType: ChangeType, tiersId: String) {
        serviceScope.launch {
            cacheService.notifyModification("tiers")
        }
    }

    /**
     * Notifie une modification de compte
     */
    fun notifyCompteChanged(changeType: ChangeType, compteId: String) {
        serviceScope.launch {
            cacheService.notifyModification("comptes")
        }
    }

    /**
     * Notifie une modification d'enveloppe
     */
    fun notifyEnveloppeChanged(changeType: ChangeType, enveloppeId: String) {
        serviceScope.launch {
            cacheService.notifyModification("enveloppes")
        }
    }

    /**
     * Notifie une modification de transaction (invalide tous les caches liés)
     */
    fun notifyTransactionChanged(changeType: ChangeType, transactionId: String) {
        serviceScope.launch {
            // Les transactions affectent tous les caches car elles modifient les soldes
            cacheService.invalidateAllStaticCaches()
        }
    }

    /**
     * Notifie une modification d'allocation (invalide tous les caches liés)
     */
    fun notifyAllocationChanged(changeType: ChangeType, allocationId: String) {
        serviceScope.launch {
            // Les allocations affectent tous les caches car elles modifient les soldes
            cacheService.invalidateAllStaticCaches()
        }
    }
} 