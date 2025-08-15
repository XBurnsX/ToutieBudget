// chemin/simule: /ui/historique/HistoriqueCompteScreen.kt
package com.xburnsx.toutiebudget.ui.historique

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = uiState.scrollPosition)
    
    // Observer les événements de navigation
    LaunchedEffect(navigationEvents) {
        navigationEvents?.let { event ->
            when (event) {
                is HistoriqueNavigationEvent.ModifierTransaction -> {
                    // Sauvegarder la position actuelle avant de naviguer
                    viewModel.sauvegarderPositionScroll(listState.firstVisibleItemIndex)
                    onModifierTransaction(event.transactionId)
                    viewModel.effacerNavigationEvent()
                }
                is HistoriqueNavigationEvent.TransactionModifiee -> {
                    // Recharger les transactions quand une transaction est modifiée
                    viewModel.rechargerTransactions()
                    viewModel.effacerNavigationEvent()
                }
            }
        }
    }

    // Sauvegarder la position de scroll quand elle change
    LaunchedEffect(remember { derivedStateOf { listState.firstVisibleItemIndex } }) {
        viewModel.sauvegarderPositionScroll(listState.firstVisibleItemIndex)
    }

    // ✅ OPTIMISATION : Supprimer le rechargement automatique qui cause le double loading
    // LaunchedEffect(Unit) {
    //     // Recharger une fois au début pour s'assurer que les données sont à jour
    //     viewModel.rechargerTransactions()
    // }

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
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.erreur != null) {
                Text(
                    uiState.erreur!!,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            } else if (uiState.transactionsGroupees.isEmpty()) {
                Text(
                    "Aucune transaction pour ce compte.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Convertir en liste ordonnée pour garantir l'ordre
                    val datesList = uiState.transactionsGroupees.toList()

                    datesList.forEach { (dateString, transactionsPourDate) ->
                        // Séparateur de date
                        stickyHeader(key = "header_$dateString") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    tonalElevation = 6.dp,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = dateString,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }
}
