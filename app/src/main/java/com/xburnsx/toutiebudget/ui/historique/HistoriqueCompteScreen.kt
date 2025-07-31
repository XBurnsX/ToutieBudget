// chemin/simule: /ui/historique/HistoriqueCompteScreen.kt
package com.xburnsx.toutiebudget.ui.historique

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.ui.comptes.composants.HistoriqueItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoriqueCompteScreen(
    viewModel: HistoriqueCompteViewModel,
    onNavigateBack: () -> Unit,
    onModifierTransaction: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigationEvents by viewModel.navigationEvents.collectAsState()
    
    // Observer les événements de navigation
    LaunchedEffect(navigationEvents) {
        navigationEvents?.let { event ->
            when (event) {
                is HistoriqueNavigationEvent.ModifierTransaction -> {
                    onModifierTransaction(event.transactionId)
                    viewModel.effacerNavigationEvent()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.nomCompte, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.erreur != null) {
                Text(uiState.erreur!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            } else if (uiState.transactionsGroupees.isEmpty()) {
                Text("Aucune transaction pour ce compte.", color = Color.Gray)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Convertir en liste ordonnée pour garantir l'ordre
                    val datesList = uiState.transactionsGroupees.toList()

                    datesList.forEach { (dateString, transactionsPourDate) ->
                        // Séparateur de date
                        stickyHeader(key = "header_$dateString") {
                            Text(
                                text = dateString,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E1E1E))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }

                        // Transactions pour cette date
                        items(transactionsPourDate, key = { "transaction_${it.id}" }) { transactionUi ->
                            HistoriqueItem(
                                transaction = transactionUi,
                                onLongPress = { transaction ->
                                    // Le menu s'ouvre automatiquement maintenant
                                },
                                onModifier = { transaction ->
                                    // Naviguer vers l'écran de modification
                                    viewModel.naviguerVersModification(transaction.id)
                                },
                                onSupprimer = { transaction ->
                                    // Supprimer la transaction
                                    viewModel.supprimerTransaction(transaction.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
