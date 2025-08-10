// chemin/simule: /ui/categories/CategoriesEnveloppesScreen.kt
// Dépendances: ViewModel, Composants UI, Dialogues

package com.xburnsx.toutiebudget.ui.categories

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xburnsx.toutiebudget.ui.categories.composants.CategorieCard
import com.xburnsx.toutiebudget.ui.categories.composants.CategorieReorganisable
import com.xburnsx.toutiebudget.ui.categories.dialogs.AjoutCategorieDialog
import com.xburnsx.toutiebudget.ui.categories.dialogs.AjoutEnveloppeDialog
import com.xburnsx.toutiebudget.ui.categories.dialogs.DefinirObjectifDialog
import com.xburnsx.toutiebudget.ui.categories.dialogs.ConfirmationSuppressionDialog
import com.xburnsx.toutiebudget.ui.composants_communs.ClavierNumerique

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
                windowInsets = WindowInsets(0),
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
                    // 🆕 Bouton de mode réorganisation
                    IconButton(
                        onClick = { viewModel.onToggleModeReorganisation() }
                    ) {
                        Icon(
                            imageVector = if (uiState.isModeReorganisation)
                                Icons.Default.Check else Icons.AutoMirrored.Filled.Sort,
                            contentDescription = if (uiState.isModeReorganisation)
                                "Terminer réorganisation" else "Réorganiser catégories",
                            tint = if (uiState.isModeReorganisation)
                                Color(0xFF00FF00) else Color.White
                        )
                    }

                    // Bouton d'ajout de catégorie
                    IconButton(
                        onClick = { viewModel.onOuvrirAjoutCategorieDialog() },
                        enabled = !uiState.isModeReorganisation // Désactivé en mode réorganisation
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Ajouter une catégorie",
                            tint = if (uiState.isModeReorganisation) Color.Gray else Color.White
                        )
                    }
                }
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 🆕 Indicateur de mode réorganisation
            if (uiState.isModeReorganisation) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Mode réorganisation activé - Utilisez les flèches pour déplacer les catégories et les enveloppes",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF007AFF))
                }
            } else {
                // Liste des catégories avec support de réorganisation
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { viewModel.onEffacerErreur() }
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    // 🔥 UTILISER L'ORDRE CORRECT DES CATÉGORIES
                    // Le problème est que Map.toList() ne préserve pas l'ordre du LinkedHashMap
                    // On va utiliser les catégories triées par ordre directement
                    val categoriesOrdonnees = uiState.enveloppesGroupees.entries.toList()

                    itemsIndexed(
                        items = categoriesOrdonnees,
                        key = { _, (nomCategorie, _) -> nomCategorie }
                    ) { index, (nomCategorie, enveloppes) ->
                        if (uiState.isModeReorganisation) {
                            // Mode réorganisation : utiliser le composant spécialisé
                            CategorieReorganisable(
                                nomCategorie = nomCategorie,
                                enveloppes = enveloppes,
                                position = index,
                                totalCategories = categoriesOrdonnees.size,
                                isModeReorganisation = true,
                                isEnDeplacement = uiState.categorieEnDeplacement == nomCategorie,
                                onDeplacerCategorie = viewModel::onDeplacerCategorie,
                                onDebuterDeplacement = viewModel::onDebuterDeplacementCategorie,
                                onTerminerDeplacement = viewModel::onTerminerDeplacementCategorie,
                                // 🆕 NOUVEAUX PARAMÈTRES POUR LES ENVELOPPES
                                onDeplacerEnveloppe = viewModel::onDeplacerEnveloppe,
                                modifier = Modifier
                            )
                        } else {
                            // Mode normal : utiliser le composant standard
                            CategorieCard(
                                nomCategorie = nomCategorie,
                                enveloppes = enveloppes,
                                onAjouterEnveloppeClick = {
                                    viewModel.onOuvrirAjoutEnveloppeDialog(nomCategorie)
                                },
                                onObjectifClick = viewModel::onOuvrirObjectifDialog,
                                onSupprimerEnveloppe = viewModel::onOuvrirConfirmationSuppressionEnveloppe,
                                onSupprimerObjectifEnveloppe = viewModel::onSupprimerObjectifEnveloppe,
                                onSupprimerCategorie = { nom ->
                                    viewModel.onOuvrirConfirmationSuppressionCategorie(nom)
                                },
                                modifier = Modifier
                            )
                        }
                    }
                }
            }
        }
    }

    // ===== CLAVIER NUMÉRIQUE GLOBAL =====

    if (showKeyboard) {
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current

        // Padding optimisé pour les Pixel et appareils similaires
        val paddingBottom = with(density) {
            when {
                // Pixel 7, Galaxy S23, etc. (écrans ~6.3")
                configuration.screenHeightDp in 800..850 -> 72.dp
                // Pixel 8 Pro, Galaxy S23 Ultra, etc. (écrans ~6.7"+)
                configuration.screenHeightDp > 850 -> 88.dp
                // Appareils plus petits
                configuration.screenHeightDp < 800 -> 56.dp
                // Fallback
                else -> 64.dp
            }
        }

        Dialog(
            onDismissRequest = {
                showKeyboard = false
                onMontantChangeCallback = null
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Zone de fond cliquable pour fermer
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                showKeyboard = false
                                onMontantChangeCallback = null
                            }
                        }
                )

                // Clavier optimisé pour Pixel
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(
                            start = 0.dp,
                            top = 30.dp,      // Zone colorée qui remonte
                            end = 0.dp,
                            bottom = paddingBottom
                        )
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
}