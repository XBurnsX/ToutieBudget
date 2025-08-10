// chemin/simule: /ui/comptes/ComptesScreen.kt
package com.xburnsx.toutiebudget.ui.comptes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xburnsx.toutiebudget.ui.comptes.composants.CompteItem
import com.xburnsx.toutiebudget.ui.comptes.composants.CompteReorganisable
import com.xburnsx.toutiebudget.ui.comptes.dialogs.AjoutCompteDialog
import com.xburnsx.toutiebudget.ui.comptes.dialogs.ModifierCompteDialog
import com.xburnsx.toutiebudget.ui.composants_communs.ClavierNumerique
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ComptesScreen(
    viewModel: ComptesViewModel,
    onCompteClick: (String, String, String) -> Unit,
    onCarteCreditLongClick: (String) -> Unit = {}, // Nouveau paramètre pour la navigation vers l'écran de gestion
    onDetteLongClick: (String) -> Unit = {} // Nouveau paramètre pour la navigation vers l'écran de gestion des dettes
) {
    val uiState by viewModel.uiState.collectAsState()

    // États pour le clavier numérique
    var showKeyboard by remember { mutableStateOf(false) }
    var montantClavierInitial by remember { mutableStateOf(0L) }
    var nomDialogClavier by remember { mutableStateOf("") }
    var onMontantChangeCallback by remember { mutableStateOf<((Long) -> Unit)?>(null) }

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("Comptes", fontWeight = FontWeight.Bold) },
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
                                "Terminer réorganisation" else "Réorganiser comptes",
                            tint = if (uiState.isModeReorganisation)
                                Color(0xFF00FF00) else Color.White
                        )
                    }

                    // Bouton d'ajout de compte
                    IconButton(
                        onClick = { viewModel.onOuvrirAjoutDialog() },
                        enabled = !uiState.isModeReorganisation // Désactivé en mode réorganisation
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Ajouter un compte",
                            tint = if (uiState.isModeReorganisation) Color.Gray else Color.White
                        )
                    }
                }
            )
        }
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
                        text = "Mode réorganisation activé - Utilisez les flèches pour déplacer les comptes",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                uiState.comptesGroupes.forEach { (typeDeCompte, listeDeComptes) ->
                    stickyHeader {
                        Text(
                            text = typeDeCompte,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().background(Color(0xFF121212))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                    items(listeDeComptes, key = { it.id }) { compte ->
                        if (uiState.isModeReorganisation) {
                            // Mode réorganisation : utiliser le composant spécialisé
                            CompteReorganisable(
                                compte = compte,
                                position = listeDeComptes.indexOf(compte),
                                totalComptes = listeDeComptes.size,
                                isModeReorganisation = true,
                                isEnDeplacement = uiState.compteEnDeplacement == compte.id,
                                onDeplacerCompte = viewModel::onDeplacerCompte,
                                modifier = Modifier
                            )
                        } else {
                            // Mode normal : utiliser le composant standard
                            CompteItem(
                                compte = compte,
                                onClick = {
                                    // Corriger la valeur de collection pour correspondre à ce qui est stocké dans PocketBase
                                    val collectionCompte = when (compte.javaClass.simpleName) {
                                        "CompteCheque" -> "comptes_cheques"
                                        "CompteCredit" -> "comptes_credits"
                                        "CompteDette" -> "comptes_dettes"
                                        "CompteInvestissement" -> "comptes_investissements"
                                        else -> "comptes_cheques" // Fallback par défaut
                                    }

                                    // Vérifications de sécurité pour éviter les paramètres null
                                    val compteId = compte.id
                                    val nomCompte = compte.nom

                                    if (compteId.isNotEmpty() && collectionCompte.isNotEmpty()) {
                                        onCompteClick(compteId, collectionCompte, nomCompte)
                                    }
                                },
                                onLongClick = {
                                    // Afficher le menu contextuel pour tous les types de comptes
                                    viewModel.onCompteLongPress(compte)
                                }
                            )
                        }
                    }
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
            onSave = { viewModel.onSauvegarderCompte() },
            onOpenKeyboard = { montantActuel, onMontantChange ->
                montantClavierInitial = montantActuel
                nomDialogClavier = "Solde initial"
                onMontantChangeCallback = onMontantChange
                showKeyboard = true
            }
        )
    }

    if (uiState.isModificationDialogVisible) {
        ModifierCompteDialog(
            formState = uiState.formState,
            onDismissRequest = { viewModel.onFermerTousLesDialogues() },
            onValueChange = viewModel::onFormValueChange,
            onSave = { viewModel.onSauvegarderCompte() },
            onOpenKeyboard = { montantActuel, onMontantChange ->
                montantClavierInitial = montantActuel
                nomDialogClavier = "Solde actuel"
                onMontantChangeCallback = onMontantChange
                showKeyboard = true
            }
        )
    }

    // 🔄 DIALOG DE RÉCONCILIATION
    if (uiState.isReconciliationDialogVisible) {
        Dialog(
            onDismissRequest = { viewModel.onFermerTousLesDialogues() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Réconcilier ${uiState.compteSelectionne?.nom}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = "Entrez le solde réel de votre compte selon votre relevé bancaire :",
                        fontSize = 14.sp,
                        color = Color.LightGray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    var nouveauSolde by remember {
                        mutableStateOf(
                            ((uiState.compteSelectionne?.solde ?: 0.0) * 100).toLong()
                        )
                    }

                    ChampUniversel(
                        valeur = nouveauSolde,
                        onValeurChange = { nouveauSolde = it },
                        libelle = "Solde réel",
                        utiliserClavier = false, // Désactiver le clavier intégré
                        isMoney = true,
                        icone = Icons.Default.AccountBalance,
                        estObligatoire = true,
                        couleurValeur = Color.White,
                        onClicPersonnalise = {
                            // Utiliser le système de clavier global
                            montantClavierInitial = nouveauSolde
                            nomDialogClavier = "Solde réel"
                            onMontantChangeCallback = { nouveauMontant ->
                                nouveauSolde = nouveauMontant
                            }
                            showKeyboard = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = { viewModel.onFermerTousLesDialogues() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Annuler", color = Color.Gray)
                        }

                        Button(
                            onClick = {
                                viewModel.onReconcilierCompte(nouveauSolde / 100.0)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Réconcilier", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // 🔽 MENU CONTEXTUEL sur long tap
    if (uiState.isMenuContextuelVisible) {
        Dialog(
            onDismissRequest = { viewModel.onDismissMenu() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Actions pour ${uiState.compteSelectionne?.nom}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Options spécifiques selon le type de compte
                    if (uiState.compteSelectionne is com.xburnsx.toutiebudget.data.modeles.CompteCredit) {
                        // 💳 GÉRER LA CARTE (uniquement pour les cartes de crédit)
                        TextButton(
                            onClick = {
                                viewModel.onDismissMenu()
                                onCarteCreditLongClick(uiState.compteSelectionne!!.id)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = "Gérer la carte",
                                    tint = Color(0xFF3B82F6),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Gérer la carte",
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    } else if (uiState.compteSelectionne is com.xburnsx.toutiebudget.data.modeles.CompteDette) {
                        // 💰 GÉRER LA DETTE (uniquement pour les dettes)
                        TextButton(
                            onClick = {
                                viewModel.onDismissMenu()
                                onDetteLongClick(uiState.compteSelectionne!!.id)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RequestQuote,
                                    contentDescription = "Gérer la dette",
                                    tint = Color.Red,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Gérer la dette",
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    } else {
                        // Pour les autres comptes (chèque, investissement) : options normales

                        // 📝 MODIFIER
                        TextButton(
                            onClick = { viewModel.onOuvrirModificationDialog() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Modifier",
                                    tint = Color(0xFF6366F1),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Modifier",
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        // 🔄 RÉCONCILIER
                        TextButton(
                            onClick = { viewModel.onOuvrirReconciliationDialog() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Réconcilier",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Réconcilier",
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }

                    // 🗃️ ARCHIVER (pour tous les types de comptes)
                    TextButton(
                        onClick = { viewModel.onArchiverCompte() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = "Archiver",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Archiver",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Clavier numérique par-dessus tout - utiliser Dialog pour garantir le z-index maximal
// 🎯 SOLUTION OPTIMISÉE POUR PIXEL 7 & 8 PRO

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