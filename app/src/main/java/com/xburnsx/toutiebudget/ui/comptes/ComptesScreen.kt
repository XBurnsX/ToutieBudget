// chemin/simule: /ui/comptes/ComptesScreen.kt
package com.xburnsx.toutiebudget.ui.comptes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.ui.comptes.composants.CompteItem
import com.xburnsx.toutiebudget.ui.comptes.dialogs.AjoutCompteDialog
import com.xburnsx.toutiebudget.ui.comptes.dialogs.ModifierCompteDialog

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ComptesScreen(
    viewModel: ComptesViewModel,
    onNavigateToHistorique: (Compte) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isAjoutDialogVisible) {
        AjoutCompteDialog(
            formState = uiState.formState,
            onDismissRequest = { viewModel.onFermerTousLesDialogues() },
            onValueChange = viewModel::onFormValueChange,
            onSave = { viewModel.onSauvegarderCompte() }
        )
    }
    if (uiState.isModificationDialogVisible) {
        ModifierCompteDialog(
            formState = uiState.formState,
            onDismissRequest = { viewModel.onFermerTousLesDialogues() },
            onValueChange = viewModel::onFormValueChange,
            onSave = { viewModel.onSauvegarderCompte() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes comptes", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212), titleContentColor = Color.White),
                actions = {
                    IconButton(onClick = { viewModel.onOuvrirAjoutDialog() }) {
                        Icon(Icons.Default.Add, "Ajouter un compte", tint = Color.White)
                    }
                }
            )
        },
        containerColor = Color(0xFF121212),
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.erreur != null) {
                Text(uiState.erreur!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    uiState.comptesGroupes.forEach { (typeDeCompte, listeDeComptes) ->
                        stickyHeader {
                            Text(
                                text = typeDeCompte,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().background(Color(0xFF121212)).padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                        items(listeDeComptes, key = { it.id }) { compte ->
                            Box {
                                CompteItem(
                                    compte = compte,
                                    onClick = { onNavigateToHistorique(compte) },
                                    onLongClick = { viewModel.onCompteLongPress(compte) }
                                )
                                DropdownMenu(
                                    expanded = uiState.isMenuContextuelVisible && uiState.compteSelectionne?.id == compte.id,
                                    onDismissRequest = { viewModel.onDismissMenu() }
                                ) {
                                    DropdownMenuItem(text = { Text("Modifier") }, onClick = { viewModel.onOuvrirModificationDialog() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                                    DropdownMenuItem(text = { Text("Réconcilier") }, onClick = { /* TODO */ }, leadingIcon = { Icon(Icons.Default.Sync, null) })
                                    Divider()
                                    DropdownMenuItem(text = { Text("Archiver") }, onClick = { viewModel.onArchiverCompte() }, leadingIcon = { Icon(Icons.Default.Archive, null, tint = MaterialTheme.colorScheme.error) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
