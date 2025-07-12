// chemin/simule: /ui/categories/CategoriesEnveloppesScreen.kt
// Dépendances: ViewModel, Composants UI, Dialogues

package com.xburnsx.toutiebudget.ui.categories

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.ui.categories.composants.CategorieCard
import com.xburnsx.toutiebudget.ui.categories.composants.DebugInfoComposant
import com.xburnsx.toutiebudget.ui.categories.dialogs.AjoutCategorieDialog
import com.xburnsx.toutiebudget.ui.categories.dialogs.AjoutEnveloppeDialog
import com.xburnsx.toutiebudget.ui.categories.dialogs.DefinirObjectifDialog
import com.xburnsx.toutiebudget.ui.categories.dialogs.ConfirmationSuppressionDialog

/**
 * Écran principal pour la gestion des catégories et enveloppes.
 * Permet de créer, modifier et supprimer les catégories et enveloppes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CategoriesEnveloppesScreen(
    viewModel: CategoriesEnveloppesViewModel,
    onBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()

    // ===== DIALOGUES =====
    
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
    
    // Dialogue de confirmation de suppression d'enveloppe
    if (uiState.isConfirmationSuppressionEnveloppeVisible) {
        val enveloppe = uiState.enveloppePourSuppression
        if (enveloppe != null) {
            ConfirmationSuppressionDialog(
                titre = "Supprimer l'enveloppe",
                message = "Êtes-vous sûr de vouloir supprimer l'enveloppe '${enveloppe.nom}' ?\n\nCette action est irréversible et supprimera toutes les données associées.",
                onConfirm = viewModel::onConfirmerSuppressionEnveloppe,
                onDismiss = viewModel::onFermerConfirmationSuppressionEnveloppe
            )
        }
    }
    
    // Dialogue de confirmation de suppression de catégorie
    if (uiState.isConfirmationSuppressionCategorieVisible) {
        val nomCategorie = uiState.categoriePourSuppression
        if (nomCategorie != null) {
            ConfirmationSuppressionDialog(
                titre = "Supprimer la catégorie",
                message = "Êtes-vous sûr de vouloir supprimer la catégorie '$nomCategorie' ?\n\nUne catégorie ne peut être supprimée que si elle est vide.\nCette action est irréversible.",
                onConfirm = viewModel::onConfirmerSuppressionCategorie,
                onDismiss = viewModel::onFermerConfirmationSuppressionCategorie
            )
        }
    }

    // ===== INTERFACE PRINCIPALE =====

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Catégories & Enveloppes", 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBack, 
                                contentDescription = "Retour", 
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212), 
                    titleContentColor = Color.White
                ),
                actions = {
                    // Bouton de rechargement
                    IconButton(onClick = { viewModel.chargerDonnees() }) {
                        Icon(
                            Icons.Default.Refresh, 
                            contentDescription = "Recharger", 
                            tint = Color.White
                        )
                    }
                    // Bouton d'ajout de catégorie
                    IconButton(onClick = { viewModel.onOuvrirAjoutCategorieDialog() }) {
                        Icon(
                            Icons.Default.Add, 
                            contentDescription = "Ajouter une catégorie", 
                            tint = Color.White
                        )
                    }
                }
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        
        // ===== CONTENU PRINCIPAL =====
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Composant de debug (en mode développement)
                if (com.xburnsx.toutiebudget.BuildConfig.EST_MODE_DEBUG) {
                    item(key = "debug_info") {
                        val toutesLesEnveloppes = uiState.enveloppesGroupees.values.flatten()
                        val categoriesMap = uiState.enveloppesGroupees.keys.associateWith { nomCategorie ->
                            // Pour le debug, on simule les IDs de catégories
                            "cat_${nomCategorie.hashCode()}"
                        }
                        
                        DebugInfoComposant(
                            enveloppes = toutesLesEnveloppes,
                            categories = categoriesMap
                        )
                    }
                }
                
                // Message d'erreur si présent
                if (uiState.erreur != null) {
                    item(key = "error_message") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⚠️ ${uiState.erreur}",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.onEffacerErreur() }) {
                                    Text("OK")
                                }
                            }
                        }
                    }
                }
                
                // Message si aucune catégorie
                if (uiState.enveloppesGroupees.isEmpty() && !uiState.isLoading) {
                    item(key = "empty_state") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF2C2C2E)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "📁",
                                    fontSize = 48.dp.value.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Aucune catégorie",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Cliquez sur '+' pour créer votre première catégorie",
                                    color = Color.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.onOuvrirAjoutCategorieDialog() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Créer une catégorie")
                                }
                            }
                        }
                    }
                }
                
                // Liste des catégories et leurs enveloppes
                items(
                    items = uiState.enveloppesGroupees.entries.toList(),
                    key = { (categorie, _) -> "categorie_$categorie" }
                ) { (nomCategorie, enveloppes) ->
                    CategorieCard(
                        nomCategorie = nomCategorie,
                        enveloppes = enveloppes,
                        onAjouterEnveloppeClick = { 
                            viewModel.onOuvrirAjoutEnveloppeDialog(nomCategorie) 
                        },
                        onObjectifClick = { enveloppe -> 
                            viewModel.onOuvrirObjectifDialog(enveloppe) 
                        },
                        onSupprimerEnveloppe = { enveloppe -> 
                            viewModel.onOuvrirConfirmationSuppressionEnveloppe(enveloppe) 
                        },
                        onSupprimerCategorie = { nomCat -> 
                            viewModel.onOuvrirConfirmationSuppressionCategorie(nomCat) 
                        }
                    )
                }
                
                // Espacement en bas pour éviter que le contenu soit coupé
                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
            
            // Indicateur de chargement centré
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.7f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Chargement des données...",
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}