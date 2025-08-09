package com.xburnsx.toutiebudget.ui.archives

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
    onBack: () -> Unit
) {
    val comptesState by comptesViewModel.uiState.collectAsState()
    val budgetState by budgetViewModel.uiState.collectAsState()

    val comptesArchives = comptesState.comptesGroupes.values.flatten().filter { it.estArchive }
    val enveloppesArchives: List<EnveloppeUi> = budgetState.categoriesEnveloppes
        .flatMap { it.enveloppes }
        .filter { env -> env.estArchive }

    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Comptes archivés", "Enveloppes archivées")

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
        Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            when (tabIndex) {
                0 -> {
                    LaunchedEffect(Unit) { comptesViewModel.chargerComptesArchives() }
                    val comptesArchivesDyn = comptesState.comptesGroupes.values.flatten().filter { it.estArchive }
                    LazyColumn { items(comptesArchivesDyn) { c ->
                        CompteItem(compte = c, onClick = {}, onLongClick = {})
                    } }
                }
                1 -> {
                    LazyColumn { items(enveloppesArchives) { env ->
                        ListItem(
                            headlineContent = { Text(env.nom, color = Color.White) },
                            supportingContent = { Text("Objectif: ${env.objectif}", color = Color.LightGray) }
                        )
                    } }
                }
            }
        }
    }
}

