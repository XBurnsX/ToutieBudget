package com.xburnsx.toutiebudget.ui.budget

import kotlinx.coroutines.flow.MutableSharedFlow

object BudgetEvents {
    val refreshBudget = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
} 