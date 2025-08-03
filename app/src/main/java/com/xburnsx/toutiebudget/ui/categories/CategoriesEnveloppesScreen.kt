// chemin/simule: /ui/categories/CategoriesEnveloppesScreen.kt
// Dépendances: ViewModel, Composants UI, Dialogues

package com.xburnsx.toutiebudget.ui.categories

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xburnsx.toutiebudget.ui.categories.composants.CategorieCard
import com.xburnsx.toutiebudget.ui.categories.dialogs.AjoutCategorieDialog
import com.xburnsx.toutiebudget.ui.categories.dialogs.AjoutEnveloppeDialog
import com.xburnsx.toutiebudget.ui.categories.dialogs.DefinirObjectifDialog
import com.xburnsx.toutiebudget.ui.categories.dialogs.ConfirmationSuppressionDialog
import com.xburnsx.toutiebudget.ui.composants_communs.ClavierNumerique
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

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

    // 🆕 ÉTATS POUR LE CLAVIER NUMÉRIQUE GLOBAL
    var showKeyboard by remember { mutableStateOf(false) }
    var montantClavierInitial by remember { mutableStateOf(0L) }
    var nomDialogClavier by remember { mutableStateOf("") }
    var onMontantChangeCallback by remember { mutableStateOf<((Long) -> Unit)?>(null) }

    // 🐲 NOUVEAUX ÉTATS POUR LE DRAG & DROP
    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            viewModel.onMoveCategorie(from.key as String, to.key as String)
        },
        canDragOver = { draggedOver, _ ->
            // Autoriser le drop sur d'autres catégories uniquement
            (draggedOver.key as? String)?.startsWith("categorie_") == true
        }
    )
    val lazyListState = reorderableState.listState
    val scope = rememberCoroutineScope()

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
            onValueChange = { type, montant, date, jour, resetApresEcheance, dateFin, dateDebut ->
                type?.let { viewModel.onObjectifTypeChange(it) }
                montant?.let { viewModel.onObjectifMontantChange(it) }
                date?.let { viewModel.onObjectifDateChange(it) }
                jour?.let { viewModel.onObjectifJourChange(it) }
                resetApresEcheance?.let { viewModel.onObjectifResetApresEcheanceChange(it) }
                dateFin?.let { viewModel.onObjectifDateFinChange(it) }
                dateDebut?.let { viewModel.onObjectifDateDebutChange(it) }
            },
            onDismissRequest = viewModel::onFermerObjectifDialog,
            onSave = viewModel::onSauvegarderObjectif,
            onOpenKeyboard = { montantActuel, onMontantChange ->
                montantClavierInitial = montantActuel
                nomDialogClavier = "Montant objectif"
                onMontantChangeCallback = onMontantChange
                showKeyboard = true
            }
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
                                Icons.AutoMirrored.Filled.ArrowBack,
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
                    if (uiState.isModeEdition) {
                        // Mode édition actif - Boutons Sauvegarder et Annuler
                        IconButton(onClick = { viewModel.onSauvegarderOrdreCategories() }) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Sauvegarder l'ordre",
                                tint = Color.Green
                            )
                        }
                        IconButton(onClick = { viewModel.onAnnulerModeEdition() }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Annuler",
                                tint = Color.Red
                            )
                        }
                    } else {
                        // Mode normal - Boutons habituels
                        // Bouton mode édition (seulement si il y a des catégories)
                        if (uiState.enveloppesGroupees.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onActiverModeEdition() }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Réorganiser les catégories",
                                    tint = Color.White
                                )
                            }
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
            // Message d'erreur si présent
            if (uiState.erreur != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
            // Message si aucune catégorie
            if (uiState.enveloppesGroupees.isEmpty() && !uiState.isLoading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
            // Liste des catégories et enveloppes
            if (uiState.enveloppesGroupees.isNotEmpty()) {
                if (uiState.isModeEdition) {
                    com.xburnsx.toutiebudget.ui.categories.composants.SystemeGlisserDeposerCategories(
                        categories = uiState.enveloppesGroupees.keys.toList(),
                        enveloppesParCategorie = uiState.enveloppesGroupees,
                        onReordonner = { nouvelleListe ->
                            viewModel.onReorganiserCategories(nouvelleListe)
                        },
                        contenuCategorie = { nomCategorie, enveloppes, isDragging ->
                            CategorieCard(
                                nomCategorie = nomCategorie,
                                enveloppes = enveloppes,
                                isModeEdition = true,
                                isDragging = isDragging,
                                modifier = Modifier,
                                onAjouterEnveloppeClick = {
                                    viewModel.onOuvrirAjoutEnveloppeDialog(nomCategorie)
                                },
                                onObjectifClick = { enveloppe ->
                                    viewModel.onOuvrirObjectifDialog(enveloppe)
                                },
                                onSupprimerEnveloppe = { enveloppe ->
                                    viewModel.onOuvrirConfirmationSuppressionEnveloppe(enveloppe)
                                },
                                onSupprimerObjectifEnveloppe = { enveloppe ->
                                    viewModel.onSupprimerObjectifEnveloppe(enveloppe)
                                },
                                onSupprimerCategorie = { nomCat ->
                                    viewModel.onOuvrirConfirmationSuppressionCategorie(nomCat)
                                }
                            )
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            items = uiState.enveloppesGroupees.entries.toList(),
                            key = { _, (categorie, _) -> "categorie_$categorie" }
                        ) { _, (nomCategorie, enveloppes) ->
                            CategorieCard(
                                nomCategorie = nomCategorie,
                                enveloppes = enveloppes,
                                isModeEdition = false,
                                isDragging = false,
                                modifier = Modifier,
                                onAjouterEnveloppeClick = {
                                    viewModel.onOuvrirAjoutEnveloppeDialog(nomCategorie)
                                },
                                onObjectifClick = { enveloppe ->
                                    viewModel.onOuvrirObjectifDialog(enveloppe)
                                },
                                onSupprimerEnveloppe = { enveloppe ->
                                    viewModel.onOuvrirConfirmationSuppressionEnveloppe(enveloppe)
                                },
                                onSupprimerObjectifEnveloppe = { enveloppe ->
                                    viewModel.onSupprimerObjectifEnveloppe(enveloppe)
                                },
                                onSupprimerCategorie = { nomCat ->
                                    viewModel.onOuvrirConfirmationSuppressionCategorie(nomCat)
                                }
                            )
                        }
                    }
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

    // 🆕 CLAVIER NUMÉRIQUE PAR-DESSUS TOUT - EN BAS DE L'ÉCRAN
    if (showKeyboard) {
        Dialog(
            onDismissRequest = {
                showKeyboard = false
                onMontantChangeCallback = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            // Le Dialog garantit que le clavier sera au-dessus de tout
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            showKeyboard = false
                            onMontantChangeCallback = null
                        }
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                ClavierNumerique(
                    montantInitial = montantClavierInitial,
                    isMoney = true,
                    suffix = "",
                    onMontantChange = { nouveauMontant ->
                        onMontantChangeCallback?.invoke(nouveauMontant)
                    },
                    onFermer = {
                        showKeyboard = false
                        onMontantChangeCallback = null
                    }
                )
            }
        }
    }
}