package com.xburnsx.toutiebudget.ui.statistiques

import com.xburnsx.toutiebudget.data.modeles.Transaction
import java.util.Date

data class TopItem(
    val id: String,
    val label: String,
    val montant: Double,
    val pourcentage: Double
)

data class Periode(
    val debut: Date,
    val fin: Date,
    val label: String
)

data class StatistiquesUiState(
    val isLoading: Boolean = false,
    val erreur: String? = null,
    val periode: Periode? = null,
    val totalDepenses: Double = 0.0,
    val totalRevenus: Double = 0.0,
    val totalNet: Double = 0.0,
    val transactionsPeriode: List<Transaction> = emptyList(),
    val top5Enveloppes: List<TopItem> = emptyList(),
    val top5Tiers: List<TopItem> = emptyList(),
    val depenses6DerniersMois: List<Pair<String, Double>> = emptyList(),
    val revenus6DerniersMois: List<Pair<String, Double>> = emptyList(),
    val cashflowCumulMensuel: List<Pair<String, Double>> = emptyList(),
    val moyenneMobile7Jours: List<Pair<String, Double>> = emptyList(),
    val tiersIdToNom: Map<String, String> = emptyMap(),
    val modalOuvert: Boolean = false,
    val modalTitre: String = "",
    val modalTransactions: List<Transaction> = emptyList()
)

