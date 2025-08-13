package com.xburnsx.toutiebudget.ui.pret_personnel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import com.xburnsx.toutiebudget.ui.pret_personnel.composants.HistoriqueDetteEmpruntItem

@Composable
fun PretPersonnelScreen(
    viewModel: PretPersonnelViewModel,
    onBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.charger()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        TabRow(
            selectedTabIndex = when (uiState.currentTab) {
                PretTab.PRET -> 0
                PretTab.EMPRUNT -> 1
                PretTab.ARCHIVER -> 2
            },
            containerColor = Color(0xFF1E1E1E),
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(selected = uiState.currentTab == PretTab.PRET, onClick = { viewModel.setTab(PretTab.PRET) }, text = { Text("Prêt") })
            Tab(selected = uiState.currentTab == PretTab.EMPRUNT, onClick = { viewModel.setTab(PretTab.EMPRUNT) }, text = { Text("Emprunt") })
            Tab(selected = uiState.currentTab == PretTab.ARCHIVER, onClick = { viewModel.setTab(PretTab.ARCHIVER) }, text = { Text("Archiver") })
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            uiState.isLoading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.erreur != null -> {
                Text(uiState.erreur ?: "Erreur inconnue", color = MaterialTheme.colorScheme.error)
            }
            else -> {
                if (uiState.items.isEmpty()) {
                    Text("Aucun prêt personnel trouvé", color = Color.White)
                } else {
                    val items = when (uiState.currentTab) {
                        PretTab.PRET -> uiState.itemsPret
                        PretTab.EMPRUNT -> uiState.itemsEmprunt
                        PretTab.ARCHIVER -> emptyList() // TODO: brancher quand archive sera supporté
                    }
                    var openDialogFor by remember { mutableStateOf<String?>(null) }
                    if (openDialogFor != null) {
                        HistoriqueDetteEmpruntItem(
                            visible = true,
                            nomTiers = openDialogFor!!,
                            uiState = uiState,
                            onDismiss = {
                                openDialogFor = null
                                viewModel.clearHistorique()
                            }
                        )
                    }

                    LazyColumn {
                        items(items, key = { it.key }) { item ->
                            androidx.compose.material3.Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clickable {
                                        openDialogFor = item.nomTiers
                                        viewModel.chargerHistoriquePourPret(item.key, item.nomTiers)
                                    },
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = Color(0xFF1E1E1E)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(item.nomTiers, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        val solde = MoneyFormatter.formatAmount(item.soldeRestant)
                                        Text(solde, color = if (item.soldeRestant > 0) Color(0xFF4CAF50) else Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        val label = if (uiState.currentTab == PretTab.PRET) "Montant prêté" else "Montant emprunté"
                                        Text("$label: ${MoneyFormatter.formatAmount(item.montantPrete)}", color = Color.LightGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


