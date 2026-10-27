package com.xburnsx.toutiebudget.ui.archives

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.ui.comptes.ComptesViewModel
import com.xburnsx.toutiebudget.ui.comptes.composants.CompteItem
import com.xburnsx.toutiebudget.ui.budget.BudgetViewModel
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivesScreen(
    comptesViewModel: ComptesViewModel,
    budgetViewModel: BudgetViewModel,
    onBack: () -> Unit,
    onVoirHistoriqueCompte: (String, String, String) -> Unit = { _, _, _ -> },
    onVoirHistoriqueEnveloppe: (String, String) -> Unit = { _, _ -> }
) {
    val comptesState by comptesViewModel.uiState.collectAsState()
    val budgetState by budgetViewModel.uiState.collectAsState()

    val comptesArchives = comptesState.comptesGroupes.values.flatten().filter { it.estArchive }
    val enveloppesArchives: List<EnveloppeUi> = budgetState.categoriesEnveloppes
        .flatMap { it.enveloppes }
        .filter { env -> env.estArchive }

    var tabIndex by remember { mutableStateOf(0) }
    var recherche by remember { mutableStateOf("") }
    val tabs = listOf("Comptes archivés", "Enveloppes archivées")

    LaunchedEffect(Unit) {
        comptesViewModel.chargerComptesArchives()
        budgetViewModel.setShowArchived(true)
    }
    DisposableEffect(Unit) {
        onDispose { budgetViewModel.setShowArchived(false) }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    windowInsets = WindowInsets(0),
                    title = { Text("Archives", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212), titleContentColor = Color.White),
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White) }
                    }
                )
                TabRow(selectedTabIndex = tabIndex, containerColor = Color(0xFF121212)) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = tabIndex == index,
                            onClick = { tabIndex = index },
                            text = { Text(title, color = if (tabIndex == index) Color.White else Color.LightGray) }
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ARA1 : recherche locale dans les éléments déjà archivés, sans écriture.
            OutlinedTextField(
                value = recherche,
                onValueChange = { recherche = it },
                label = { Text("Rechercher dans les archives") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            when (tabIndex) {
                0 -> {
                    // CO3 : liste propre aux archives, non écrasée par les rafraîchissements des comptes actifs
                    val comptesArchivesDyn = comptesState.comptesArchives.filter {
                        it.nom.contains(recherche.trim(), ignoreCase = true)
                    }
                    // IF15 : un placement archivé peut être réactivé (même compte, même historique)
                    var aReactiver by remember { mutableStateOf<com.xburnsx.toutiebudget.data.modeles.Compte?>(null) }
                    Text("${comptesArchivesDyn.size} compte(s) archivé(s)", modifier = Modifier.padding(horizontal = 16.dp))
                    LazyColumn { items(comptesArchivesDyn, key = { it.id }) { c ->
                        CompteItem(
                            compte = c,
                            onClick = { onVoirHistoriqueCompte(c.id, c.collection, c.nom) },
                            onLongClick = { aReactiver = c }
                        )
                    } }
                    aReactiver?.let { compte ->
                        AlertDialog(
                            onDismissRequest = { aReactiver = null },
                            title = { Text("Réactiver ${compte.nom} ?") },
                            text = { Text("Le compte revient dans la liste avec ses transactions et ses valeurs passées.") },
                            confirmButton = {
                                TextButton(onClick = { comptesViewModel.reactiverCompte(compte); aReactiver = null }) { Text("Réactiver") }
                            },
                            dismissButton = { TextButton(onClick = { aReactiver = null }) { Text("Annuler") } }
                        )
                    }
                }
                1 -> {
                    var aReactiver by remember { mutableStateOf<EnveloppeUi?>(null) }
                    val enveloppesFiltrees = enveloppesArchives.filter {
                        it.nom.contains(recherche.trim(), ignoreCase = true)
                    }
                    Text("${enveloppesFiltrees.size} enveloppe(s) archivée(s)", modifier = Modifier.padding(horizontal = 16.dp))
                    LazyColumn { items(enveloppesFiltrees, key = { it.id }) { env ->
                        ListItem(
                            modifier = Modifier.clickable { onVoirHistoriqueEnveloppe(env.id, env.nom) },
                            headlineContent = { Text(env.nom, color = Color.White) },
                            supportingContent = { Text("Objectif: ${env.objectif}", color = Color.LightGray) },
                            trailingContent = { TextButton(onClick = { aReactiver = env }) { Text("Réactiver") } }
                        )
                    } }
                    aReactiver?.let { env ->
                        AlertDialog(
                            onDismissRequest = { aReactiver = null },
                            title = { Text("Réactiver ${env.nom} ?") },
                            text = { Text("L'enveloppe revient dans le budget avec son objectif, ses allocations et son historique.") },
                            confirmButton = {
                                TextButton(onClick = { budgetViewModel.reactiverEnveloppe(env.id); aReactiver = null }) { Text("Réactiver") }
                            },
                            dismissButton = { TextButton(onClick = { aReactiver = null }) { Text("Annuler") } }
                        )
                    }
                }
            }
        }
    }
}
