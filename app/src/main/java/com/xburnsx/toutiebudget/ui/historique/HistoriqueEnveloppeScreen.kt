package com.xburnsx.toutiebudget.ui.historique

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.ui.comptes.composants.HistoriqueItem
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoriqueEnveloppeScreen(
    viewModel: HistoriqueEnveloppeViewModel,
    onNavigateBack: () -> Unit,
    onModifierTransaction: (String) -> Unit = {},
    onReprendreTransaction: ((String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigationEvents by viewModel.navigationEvents.collectAsState()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = uiState.scrollPosition,
        initialFirstVisibleItemScrollOffset = uiState.scrollOffset
    )
    var transactionASupprimer by remember { mutableStateOf<TransactionUi?>(null) }
    
    // Observer les événements de navigation
    LaunchedEffect(navigationEvents) {
        navigationEvents?.let { event ->
            when (event) {
                is HistoriqueNavigationEvent.ModifierTransaction -> {
                    // Sauvegarder la position actuelle avant de naviguer
                    viewModel.sauvegarderPositionScroll(
                        listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset
                    )
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

    // HC14 : écouter vraiment les mouvements (item + décalage pixels), sans relancer
    // la collecte à chaque recomposition ni revenir au début après une modification.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) -> viewModel.sauvegarderPositionScroll(index, offset) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.nomEnveloppe, fontWeight = FontWeight.Bold) },
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
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // HC10/HC12 : une erreur n'efface pas les transactions précédentes.
            uiState.erreur?.let { erreur ->
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(erreur, color = MaterialTheme.colorScheme.error)
                    TextButton(enabled = !uiState.isLoading,
                        onClick = viewModel::rechargerTransactions) { Text("Réessayer") }
                }
            }
            if (uiState.isLoading && uiState.transactionsGroupees.isNotEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (uiState.isLoading && uiState.transactionsGroupees.isEmpty()) {
                CircularProgressIndicator()
            } else if (uiState.transactionsGroupees.isEmpty() && uiState.erreur == null) {
                Text(
                    "Aucune transaction pour cette enveloppe.",
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
                        // En-tête de date
                        item(key = "date_$dateString") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                            ) {
                                Text(
                                    text = formaterDate(dateString),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(8.dp)
                                )
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
                                    transactionASupprimer = transaction
                                },
                                onReprendre = onReprendreTransaction?.let { reprendre -> { transaction -> reprendre(transaction.id) } }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
            }
        }
    }
    ConfirmationSuppressionTransaction(
        transaction = transactionASupprimer,
        onAnnuler = { transactionASupprimer = null },
        onConfirmer = { transactionASupprimer = null; viewModel.supprimerTransaction(it.id) }
    )
}

/**
 * Formate les clés de regroupement « d MMMM yyyy » en français (HE6).
 */
private fun formaterDate(dateString: String): String {
    return try {
        val inputFormat = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.FRENCH).apply { isLenient = false }
        val date = inputFormat.parse(dateString)
        val outputFormat = java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.FRENCH)
        outputFormat.format(date!!).replaceFirstChar { it.uppercase() }
    } catch (e: Exception) {
        dateString
    }
}
