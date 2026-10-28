// chemin/simule: /ui/historique/HistoriqueCompteScreen.kt
package com.xburnsx.toutiebudget.ui.historique

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.ui.comptes.composants.HistoriqueItem
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoriqueCompteScreen(
    viewModel: HistoriqueCompteViewModel,
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
    var menuTypeOuvert by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Barre de recherche
            TextField(
                value = uiState.searchQuery,
                onValueChange = { query ->
                    viewModel.mettreAJourRecherche(query)
                    scope.launch { listState.scrollToItem(0) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Rechercher dans les transactions...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Rechercher")
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.mettreAJourRecherche("")
                            scope.launch { listState.scrollToItem(0) }
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Effacer")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                )
            )

            // HA1/HA2 : périodes et types. Même recherche et même ViewModel
            // au retour de Modifier : pas de nouveau chargement à chaque clic.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    VuePeriodeHistorique.TOUTES to "Tout",
                    VuePeriodeHistorique.MOIS to "Mois",
                    VuePeriodeHistorique.ANNEE to "Année"
                ).forEach { (vue, libelle) ->
                    FilterChip(
                        selected = uiState.periode.vue == vue,
                        onClick = {
                            viewModel.selectionnerVuePeriode(vue)
                            scope.launch { listState.scrollToItem(0) }
                        },
                        label = { Text(libelle) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box {
                    OutlinedButton(onClick = { menuTypeOuvert = true }) {
                        Text("Type : " + (uiState.typeFiltre?.libelle ?: "Tous"))
                    }
                    DropdownMenu(
                        expanded = menuTypeOuvert,
                        onDismissRequest = { menuTypeOuvert = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tous les types") },
                            onClick = {
                                menuTypeOuvert = false
                                viewModel.selectionnerTypeFiltre(null)
                                scope.launch { listState.scrollToItem(0) }
                            }
                        )
                        TypeTransaction.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.libelle) },
                                onClick = {
                                    menuTypeOuvert = false
                                    viewModel.selectionnerTypeFiltre(type)
                                    scope.launch { listState.scrollToItem(0) }
                                }
                            )
                        }
                    }
                }
            }
            if (uiState.periode.vue != VuePeriodeHistorique.TOUTES) {
                val periode = uiState.periode
                val libellePeriode = if (periode.vue == VuePeriodeHistorique.MOIS) {
                    YearMonth.of(periode.annee, periode.mois).format(
                        DateTimeFormatter.ofPattern("LLLL yyyy", Locale.CANADA_FRENCH)
                    )
                } else periode.annee.toString()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = {
                        viewModel.deplacerPeriode(-1)
                        scope.launch { listState.scrollToItem(0) }
                    }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Période précédente")
                    }
                    Text(libellePeriode, style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = {
                        viewModel.deplacerPeriode(1)
                        scope.launch { listState.scrollToItem(0) }
                    }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Période suivante")
                    }
                }
            }

            // HA3 : uniquement les mouvements visibles après recherche, période
            // et type. Le total d'un achat fractionné compte une seule fois.
            if (!uiState.isLoading || uiState.transactionsGroupees.isNotEmpty()) {
                val resume = remember(uiState.transactionsGroupees) {
                    runCatching {
                        ResumeFluxHistoriqueCompte.calculer(
                            uiState.transactionsGroupees.values.flatten()
                        )
                    }.getOrNull()
                }
                if (resume == null) {
                    Text(
                        "Totaux indisponibles : certains montants dépassent les limites du calcul.",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            "Mouvements affichés : " + resume.mouvements +
                                " · Entrées : " + MoneyFormatter.formatAmountFromCents(resume.entreesCentimes) +
                                " · Sorties : " + MoneyFormatter.formatAmountFromCents(resume.sortiesCentimes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Flux net affiché : " +
                                MoneyFormatter.formatAmountFromCents(resume.netCentimes) +
                                if (resume.montantsExclus > 0 || resume.typesNonClasses > 0)
                                    " · Résumé partiel : " + resume.montantsExclus +
                                        " montant(s) invalide(s), " + resume.typesNonClasses +
                                        " type(s) non classé(s)"
                                else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // HC10 : les transactions peuvent être disponibles même si leurs libellés
            // d'enveloppes / allocations ne le sont pas. Ne jamais cacher l'incident.
            uiState.avertissement?.let { texte ->
                Text(
                    text = texte,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // HC10/HC12 : une erreur ne supprime pas les mouvements déjà consultables.
            uiState.erreur?.let { erreur ->
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(erreur, color = MaterialTheme.colorScheme.error)
                    TextButton(
                        enabled = !uiState.isLoading,
                        onClick = viewModel::rechargerTransactions
                    ) { Text("Réessayer") }
                }
            }
            if (uiState.isLoading && uiState.transactionsGroupees.isNotEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Contenu principal
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading && uiState.transactionsGroupees.isEmpty()) {
                    CircularProgressIndicator()
                } else if (uiState.transactionsGroupees.isEmpty() && uiState.erreur == null) {
                    Text(
                        when {
                            uiState.searchQuery.isNotEmpty() ->
                                "Aucun résultat pour cette recherche avec les filtres choisis."
                            uiState.periode.vue != VuePeriodeHistorique.TOUTES ||
                                uiState.typeFiltre != null ->
                                "Aucun mouvement pour ces filtres. Choisis « Tout » et « Tous les types »."
                            else -> "Aucune transaction pour ce compte."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
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
                            items(
                                transactionsPourDate,
                                key = { "transaction_${it.id}" }) { transactionUi ->
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
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(
                                        alpha = 0.3f
                                    )
                                )
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
        onConfirmer = { transactionASupprimer = null; viewModel.supprimerTransactionConfirmee(it) }
    )
}
