package com.xburnsx.toutiebudget.ui.cartes_credit

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.ui.cartes_credit.composants.AlertesCartesCredit
import com.xburnsx.toutiebudget.ui.cartes_credit.composants.CarteCreditDetailCard
import com.xburnsx.toutiebudget.ui.cartes_credit.composants.FraisMensuelsCard
import com.xburnsx.toutiebudget.ui.cartes_credit.composants.NaturesMouvementsCarteCard
import com.xburnsx.toutiebudget.ui.cartes_credit.composants.RelevesCarteCard
import com.xburnsx.toutiebudget.ui.cartes_credit.composants.SimulateurCarte
import com.xburnsx.toutiebudget.ui.cartes_credit.dialogs.ModifierCarteCreditDialog
import com.xburnsx.toutiebudget.ui.cartes_credit.dialogs.ModifierFraisDialog
import com.xburnsx.toutiebudget.ui.cartes_credit.dialogs.ReleveDialog
import com.xburnsx.toutiebudget.ui.composants_communs.DialogErreur

/**
 * « Gérer la carte » : détails à la demande, hors de la page Comptes (CC11).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestionCarteCreditScreen(
    carteCreditId: String,
    viewModel: CartesCreditViewModel = viewModel(),
    onRetour: () -> Unit = {},
    onNaviguerVersPaiement: (CompteCredit) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val formulaire by viewModel.formulaire.collectAsState()

    LaunchedEffect(carteCreditId) {
        viewModel.chargerCarteSpecifique(carteCreditId)
    }

    val carteCredit = uiState.carteSelectionnee

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = { Text(carteCredit?.nom ?: "Gestion Carte de Crédit") },
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
        when {
            uiState.estEnChargement -> Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            carteCredit == null -> Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Carte de crédit non trouvée", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRetour) { Text("Retour") }
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // CC9 : alertes réelles et sobres
                if (uiState.alertes.isNotEmpty()) {
                    item {
                        AlertesCartesCredit(alertes = uiState.alertes, onAlerteClick = { })
                    }
                }

                item { CarteCreditDetailCard(carte = carteCredit, transactionsCarte = uiState.transactionsCarte) }

                item {
                    NaturesMouvementsCarteCard(
                        carte = carteCredit,
                        transactions = uiState.transactionsCarte,
                        onClasser = viewModel::definirNatureMouvement
                    )
                }

                item {
                    RelevesCarteCard(
                        carte = carteCredit,
                        onAjouter = { viewModel.ouvrirReleve(null) },
                        onModifier = { viewModel.ouvrirReleve(it) },
                        onComptabiliserInterets = { viewModel.comptabiliserInterets(it) },
                        onComptabiliserFrais = { viewModel.comptabiliserFrais(it) }
                    )
                }

                item {
                    FraisMensuelsCard(
                        carte = carteCredit,
                        onModifierFrais = { viewModel.afficherDialogModificationFrais(carteCredit) },
                        onSupprimerFrais = { frais -> viewModel.supprimerFraisMensuel(carteCredit, frais) }
                    )
                }

                item { SimulateurCarte(carte = carteCredit) }

                // Un vrai paiement = une transaction (formulaire Transactions prérempli)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Button(
                                onClick = { onNaviguerVersPaiement(carteCredit) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Payment, contentDescription = null)
                                    Text("Effectuer un paiement", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.afficherDialogModification) {
        ModifierCarteCreditDialog(
            formulaire = formulaire,
            enCours = uiState.enregistrementEnCours,
            onChange = viewModel::modifierFormulaire,
            onSauvegarder = viewModel::sauvegarderModificationsCarteCredit,
            onDismiss = viewModel::fermerDialogs
        )
    }

    if (uiState.afficherDialogModificationFrais && carteCredit != null) {
        ModifierFraisDialog(
            formulaire = formulaire,
            onChange = viewModel::modifierFormulaire,
            onSauvegarder = viewModel::sauvegarderModificationFrais,
            onDismiss = viewModel::fermerDialogs
        )
    }

    uiState.releveEnEdition?.let { releve ->
        ReleveDialog(
            releve = releve,
            estNouveau = carteCredit?.details?.releves?.none { it.id == releve.id } ?: true,
            onSauvegarder = viewModel::sauvegarderReleve,
            onSupprimer = viewModel::supprimerReleve,
            onDismiss = viewModel::fermerReleve
        )
    }

    uiState.messageErreur?.let { erreur ->
        DialogErreur(titre = "Erreur", messageErreur = erreur, onDismiss = viewModel::effacerErreur)
    }
    uiState.messageInfo?.let { info ->
        AlertDialog(
            onDismissRequest = viewModel::effacerErreur,
            text = { Text(info) },
            confirmButton = { TextButton(onClick = viewModel::effacerErreur) { Text("OK") } }
        )
    }
}
