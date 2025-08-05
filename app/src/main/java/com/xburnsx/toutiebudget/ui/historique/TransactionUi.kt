package com.xburnsx.toutiebudget.ui.historique

import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import java.util.Date

data class TransactionUi(
    val id: String,
    val type: TypeTransaction,
    val montant: Double,
    val date: Date,
    val tiers: String,
    val nomEnveloppe: String?,
    val note: String?,
    val estFractionnee: Boolean = false,
    val sousItems: String? = null,
    val nomsEnveloppesFractions: List<String> = emptyList()
) 