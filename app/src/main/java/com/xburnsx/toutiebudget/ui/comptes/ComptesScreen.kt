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
    onCompteClick: (String, String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                title = { Text("Comptes", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212), titleContentColor = Color.White),
                actions = {
                    IconButton(onClick = { viewModel.onOuvrirAjoutDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "Ajouter un compte", tint = Color.White)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
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
                    CompteItem(
                        compte = compte,
                        onClick = { onCompteClick(compte.id, compte.javaClass.simpleName.lowercase().replace("compte", ""), compte.nom) },
                        onLongClick = { viewModel.onCompteLongPress(compte) }
                    )
                }
            }
        }
    }

    // Dialogues
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
}
