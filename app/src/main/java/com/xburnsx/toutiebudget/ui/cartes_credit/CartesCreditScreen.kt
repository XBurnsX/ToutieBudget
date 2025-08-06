package com.xburnsx.toutiebudget.ui.cartes_credit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import com.xburnsx.toutiebudget.utils.MoneyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestionCarteCreditScreen(
    carteCreditId: String,
    viewModel: CartesCreditViewModel = viewModel(),
    onRetour: () -> Unit = {}
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
                title = {
                    Text(carteCredit?.nom ?: "Gestion Carte de Crédit")
                },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
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
                        carte = carteCredit,
                        onPayerMinimum = { viewModel.effectuerPaiementMinimum(carteCredit) },
                        onPayerComplet = { viewModel.effectuerPaiementComplet(carteCredit) },
                        onAjouterDepense = { viewModel.ajouterDepenseCarte(carteCredit) }
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
            onCouleurChange = viewModel::mettreAJourCouleur,
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
            carte = carteCredit,
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
                    // Permet de cliquer à travers la Box pour fermer le dialogue
                    .pointerInput(Unit) {
                        detectTapGestures {
                            showKeyboard = false
                            onMontantChangeCallback = null
                        }
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                // Le clavier lui-même
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

@Composable
private fun ActionsRapides(
    carte: CompteCredit,
    onPayerMinimum: () -> Unit,
    onPayerComplet: () -> Unit,
    onAjouterDepense: () -> Unit
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPayerMinimum,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null)
                        Text("Payer minimum", fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = onPayerComplet,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Text("Payer complet", fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = onAjouterDepense,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Ajouter dépense", fontSize = 12.sp)
                    }
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
