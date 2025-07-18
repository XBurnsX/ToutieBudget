package com.xburnsx.toutiebudget.ui.budget

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Système d'événements centralisé pour la mise à jour automatique du budget.
 * Permet aux repositories de notifier les ViewModels quand des données changent.
 */
object BudgetEvents {
    val refreshBudget = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Déclenche un rafraîchissement du budget après une modification d'allocation
     */
    fun onAllocationUpdated(allocationId: String) {
        refreshBudget.tryEmit(Unit)
        println("[BUDGET_EVENTS] 🔄 Rafraîchissement déclenché après modification allocation: $allocationId")
    }

    /**
     * Déclenche un rafraîchissement du budget après une modification de compte
     */
    fun onCompteUpdated(compteId: String) {
        refreshBudget.tryEmit(Unit)
        println("[BUDGET_EVENTS] 🔄 Rafraîchissement déclenché après modification compte: $compteId")
    }

    /**
     * Déclenche un rafraîchissement du budget après une modification d'enveloppe
     */
    fun onEnveloppeUpdated(enveloppeId: String) {
        refreshBudget.tryEmit(Unit)
        println("[BUDGET_EVENTS] 🔄 Rafraîchissement déclenché après modification enveloppe: $enveloppeId")
    }

    /**
     * Déclenche un rafraîchissement manuel
     */
    fun refreshManual() {
        refreshBudget.tryEmit(Unit)
        println("[BUDGET_EVENTS] 🔄 Rafraîchissement manuel déclenché")
    }
}
