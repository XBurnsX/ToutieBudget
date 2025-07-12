// chemin/simule: /ui/categories/CategoriesEnveloppesScreen.kt
package com.xburnsx.toutiebudget.ui.categories

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.ui.categories.composants.CategorieCard
import com.xburnsx.toutiebudget.ui.categories.dialogs.AjoutCategorieDialog
import com.xburnsx.toutiebudget.ui.categories.dialogs.AjoutEnveloppeDialog
import com.xburnsx.toutiebudget.ui.categories.dialogs.DefinirObjectifDialog
import com.xburnsx.toutiebudget.ui.categories.dialogs.ConfirmationSuppressionDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CategoriesEnveloppesScreen(
    viewModel: CategoriesEnveloppesViewModel,
    onBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isAjoutCategorieDialogVisible) {
        AjoutCategorieDialog(
            nomCategorie = uiState.nomNouvelleCategorie,
            onNomChange = viewModel::onNomCategorieChange,
            onDismissRequest = viewModel::onFermerAjoutCategorieDialog,
            onSave = viewModel::onAjouterCategorie
        )
    }
    if (uiState.isAjoutEnveloppeDialogVisible) {
        AjoutEnveloppeDialog(
            nomEnveloppe = uiState.nomNouvelleEnveloppe,
            onNomChange = viewModel::onNomEnveloppeChange,
            onDismissRequest = viewModel::onFermerAjoutEnveloppeDialog,
            onSave = viewModel::onAjouterEnveloppe
        )
    }
    if (uiState.isObjectifDialogVisible) {
        DefinirObjectifDialog(
            nomEnveloppe = uiState.enveloppePourObjectif?.nom ?: "",
            formState = uiState.objectifFormState,
            onValueChange = { type, montant, date, jour ->
                type?.let { viewModel.onObjectifTypeChange(it) }
                montant?.let { viewModel.onObjectifMontantChange(it) }
                date?.let { viewModel.onObjectifDateChange(it) }
                jour?.let { viewModel.onObjectifJourChange(it) }
            },
            onDismissRequest = viewModel::onFermerObjectifDialog,
            onSave = viewModel::onSauvegarderObjectif
        )
    }
    
    // Boîte de dialogue de confirmation de suppression d'enveloppe
    if (uiState.isConfirmationSuppressionEnveloppeVisible) {
        val enveloppe = uiState.enveloppePourSuppression
        if (enveloppe != null) {
            ConfirmationSuppressionDialog(
                titre = "Supprimer l'enveloppe",
                message = "Êtes-vous sûr de vouloir supprimer l'enveloppe '${enveloppe.nom}' ? Cette action est irréversible.",
                onConfirm = viewModel::onConfirmerSuppressionEnveloppe,
                onDismiss = viewModel::onFermerConfirmationSuppressionEnveloppe
            )
        }
    }
    
    // Boîte de dialogue de confirmation de suppression de catégorie
    if (uiState.isConfirmationSuppressionCategorieVisible) {
        val nomCategorie = uiState.categoriePourSuppression
        if (nomCategorie != null) {
            ConfirmationSuppressionDialog(
                titre = "Supprimer la catégorie",
                message = "Êtes-vous sûr de vouloir supprimer la catégorie '$nomCategorie' ? Cette action est irréversible.",
                onConfirm = viewModel::onConfirmerSuppressionCategorie,
                onDismiss = viewModel::onFermerConfirmationSuppressionCategorie
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catégories & Enveloppes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onBack?.invoke() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212), titleContentColor = Color.White),
                actions = {
                    IconButton(onClick = { viewModel.onOuvrirAjoutCategorieDialog() }) {
                        Icon(Icons.Default.Add, "Ajouter une catégorie", tint = Color.White)
                    }
                }
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            // Affiche toujours la liste, même si elle est vide pendant le chargement
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = uiState.enveloppesGroupees.entries.toList(),
                    key = { (categorie, enveloppes) -> "${categorie}_${enveloppes.firstOrNull()?.categorieId ?: "vide"}" }
                ) { (categorie, enveloppes) ->
                    CategorieCard(
                        nomCategorie = categorie,
                        enveloppes = enveloppes,
                        onAjouterEnveloppeClick = { viewModel.onOuvrirAjoutEnveloppeDialog(categorie) },
                        onObjectifClick = { enveloppe -> viewModel.onOuvrirObjectifDialog(enveloppe) },
                        onSupprimerEnveloppe = { enveloppe -> viewModel.onOuvrirConfirmationSuppressionEnveloppe(enveloppe) },
                        onSupprimerCategorie = { nomCat -> viewModel.onOuvrirConfirmationSuppressionCategorie(nomCat) }
                    )
                }
            }
        }
    }
}
