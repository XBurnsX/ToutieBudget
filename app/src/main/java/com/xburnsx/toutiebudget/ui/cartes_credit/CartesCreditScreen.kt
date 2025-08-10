package com.xburnsx.toutiebudget.ui.cartes_credit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.ui.cartes_credit.composants.CarteCreditDetailCard
import com.xburnsx.toutiebudget.ui.cartes_credit.composants.CalculateurPaiement
import com.xburnsx.toutiebudget.ui.cartes_credit.composants.SimulateurRemboursement
import com.xburnsx.toutiebudget.ui.cartes_credit.composants.AlertesCartesCredit
import com.xburnsx.toutiebudget.ui.cartes_credit.composants.FraisMensuelsCard
import com.xburnsx.toutiebudget.ui.cartes_credit.dialogs.ModifierCarteCreditDialog
import com.xburnsx.toutiebudget.ui.cartes_credit.dialogs.PlanRemboursementDialog
import com.xburnsx.toutiebudget.ui.cartes_credit.dialogs.ModifierFraisDialog
import com.xburnsx.toutiebudget.ui.composants_communs.DialogErreur
import com.xburnsx.toutiebudget.ui.composants_communs.ClavierNumerique

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestionCarteCreditScreen(
    carteCreditId: String,
    viewModel: CartesCreditViewModel = viewModel(),
    onRetour: () -> Unit = {},
    onNaviguerVersPaiement: (CompteCredit) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    // États pour le clavier numérique
    var showKeyboard by remember { mutableStateOf(false) }
    var montantClavierInitial by remember { mutableStateOf(0L) }
    var onMontantChangeCallback by remember { mutableStateOf<((Long) -> Unit)?>(null) }

    // Charger la carte spécifique au démarrage
    LaunchedEffect(carteCreditId) {
        viewModel.chargerCarteSpecifique(carteCreditId)
    }

    val carteCredit = uiState.carteSelectionnee

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = {
                    Text(carteCredit?.nom ?: "Gestion Carte de Crédit")
                },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (carteCredit != null) {
                        IconButton(onClick = { viewModel.afficherDialogModification(carteCredit) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Modifier")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        if (uiState.estEnChargement) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (carteCredit == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Carte de crédit non trouvée",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRetour) {
                        Text("Retour")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Alertes (si disponibles)
                if (uiState.alertes.isNotEmpty()) {
                    item {
                        AlertesCartesCredit(
                            alertes = uiState.alertes,
                            onAlerteClick = { alerte ->
                                // Naviguer vers la carte concernée
                                viewModel.selectionnerCarte(alerte.carte)
                            }
                        )
                    }
                }

                // Carte avec détails principaux
                item {
                    CarteCreditDetailCard(
                        carte = carteCredit,
                        statistiques = viewModel.calculerStatistiques(carteCredit)
                    )
                }

                // Frais mensuels fixes
                item {
                    FraisMensuelsCard(
                        carte = carteCredit,
                        onModifierFrais = {
                            viewModel.afficherDialogModificationFrais(carteCredit)
                        },
                        onSupprimerFrais = { frais ->
                            viewModel.supprimerFraisMensuel(carteCredit, frais)
                        }
                    )
                }

                // Calculateur de paiement
                item {
                    CalculateurPaiement(
                        carte = carteCredit,
                        onCalculerPlan = { paiement ->
                            viewModel.genererPlanRemboursement(carteCredit, paiement)
                        },
                        onMontantChange = { montant ->
                            viewModel.calculerResultatsCalculateur(carteCredit, montant)
                        },
                        tempsRemboursement = viewModel.calculsCalculateur?.tempsRemboursement,
                        interetsTotal = viewModel.calculsCalculateur?.interetsTotal
                    )
                }

                // Simulateur de remboursement
                item {
                    SimulateurRemboursement(
                        carte = carteCredit,
                        statistiques = viewModel.calculerStatistiques(carteCredit)
                    )
                }

                // Actions rapides
                item {
                    ActionsRapides(
                        onEffectuerPaiement = {
                            // Navigation vers l'écran d'ajout de transaction en mode paiement
                            // avec la carte de crédit présélectionnée
                            onNaviguerVersPaiement(carteCredit)
                        }
                    )
                }
            }
        }
    }

    // Dialogs
    if (uiState.afficherDialogModification) {
        ModifierCarteCreditDialog(
            formulaire = viewModel.formulaire.collectAsState().value,
            onNomChange = viewModel::mettreAJourNom,
            onLimiteChange = viewModel::mettreAJourLimiteCredit,
            onTauxChange = viewModel::mettreAJourTauxInteret,
            onSoldeChange = viewModel::mettreAJourSoldeActuel,
            onPaiementMinimumChange = viewModel::mettreAJourPaiementMinimum,
            onSauvegarder = viewModel::sauvegarderModificationsCarteCredit,
            onDismiss = viewModel::fermerDialogs
        )
    }

    if (uiState.afficherPlanRemboursement) {
        PlanRemboursementDialog(
            carte = carteCredit!!,
            planRemboursement = uiState.planRemboursement,
            paiementMensuel = uiState.paiementMensuelSimulation,
            onDismiss = viewModel::fermerDialogs
        )
    }

    if (uiState.afficherDialogModificationFrais && carteCredit != null) {
        ModifierFraisDialog(
            formulaire = viewModel.formulaire.collectAsState().value,
            onNomFraisChange = viewModel::mettreAJourNomFraisMensuels,
            onFraisChange = viewModel::mettreAJourFraisMensuelsFixes,
            onSauvegarder = viewModel::sauvegarderModificationFrais,
            onDismiss = viewModel::fermerDialogs,
            onOpenKeyboard = { montantInitial, callback ->
                montantClavierInitial = montantInitial
                onMontantChangeCallback = callback
                showKeyboard = true
            }
        )
    }

    // Dialog d'erreur
    uiState.messageErreur?.let { erreur ->
        DialogErreur(
            titre = "Erreur",
            messageErreur = erreur,
            onDismiss = viewModel::effacerErreur
        )
    }

    // Clavier numérique par-dessus tout - utiliser Dialog pour garantir le z-index maximal
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

@Composable
private fun ActionsRapides(
    onEffectuerPaiement: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C2C2E)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Actions rapides",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onEffectuerPaiement,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null)
                    Text("Effectuer un paiement", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun GestionCarteCreditScreenPreview() {
    GestionCarteCreditScreen(
        carteCreditId = "1",
        onRetour = {}
    )
}
