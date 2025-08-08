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
    fun onAllocationUpdated() {
        refreshBudget.tryEmit(Unit)
    }

    /**
     * Déclenche un rafraîchissement du budget après une modification de compte
     */
    fun onCompteUpdated() {
        refreshBudget.tryEmit(Unit)
    }

    /**
     * Déclenche un rafraîchissement manuel
     */
    fun refreshManual() {
        refreshBudget.tryEmit(Unit)
    }
}
