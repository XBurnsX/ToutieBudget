// chemin/simule: /ui/ajout_transaction/AjoutTransactionScreen.kt
// Dépendances: Jetpack Compose, ViewModel, Composants communs, Clavier numérique

package com.xburnsx.toutiebudget.ui.ajout_transaction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.*
import com.xburnsx.toutiebudget.ui.composants_communs.BlocSaisieMontant
import com.xburnsx.toutiebudget.ui.composants_communs.EnveloppeDropdownItem
import com.xburnsx.toutiebudget.ui.composants_communs.SelecteurGenerique
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Écran principal pour ajouter une nouvelle transaction.
 * Permet de saisir tous les types de transactions : dépenses, revenus, prêts, etc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjoutTransactionScreen(viewModel: AjoutTransactionViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    println("DEBUG montant affiché: ${uiState.montant}")

    // État pour l'affichage du clavier numérique en modal
    var showClavier by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajouter Transaction", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zone de défilement pour les champs de saisie
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Sélecteur de mode d'opération (Standard, Prêt, Dette, etc.)
                ModeOperationSelector(
                    selectedMode = uiState.modeOperation,
                    onModeSelected = viewModel::onModeOperationSelected
                )

                // Sélecteur Dépense/Revenu
                SelecteurTypeTransaction(
                    typeSelectionne = uiState.typeTransaction,
                    onTypeSelected = viewModel::onTypeTransactionSelected
                )

                // Affichage du montant en gros, cliquable
                AffichageMontant(
                    valeurEnCentimes = uiState.montant,
                    typeTransaction = uiState.typeTransaction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showClavier = true
                        }
                )

                // Champ pour le tiers (payé à / reçu de)
                ChampTiers(
                    valeur = uiState.tiers,
                    onValeurChange = viewModel::onTiersChanged
                )

                // Sélecteur de compte
                SelecteurGenerique(
                    label = "Compte",
                    icone = Icons.Default.AccountBalanceWallet,
                    itemsGroupes = mapOf("Comptes" to uiState.comptesDisponibles),
                    itemSelectionne = uiState.compteSelectionne,
                    onItemSelected = viewModel::onCompteSelected,
                    itemToString = { it.nom },
                    customItemContent = { compte ->
                        Text(
                            compte.nom,
                            modifier = Modifier.padding(16.dp),
                            color = Color.Black
                        )
                    }
                )

                // Sélecteur d'enveloppe (uniquement pour les dépenses)
                SelecteurGenerique(
                    label = "Enveloppe",
                    icone = Icons.Default.Category,
                    itemsGroupes = uiState.enveloppesFiltrees,
                    itemSelectionne = uiState.enveloppeSelectionnee,
                    onItemSelected = viewModel::onEnveloppeSelected,
                    itemToString = { it.nom },
                    enabled = uiState.typeTransaction == "Dépense" && uiState.compteSelectionne != null,
                    customItemContent = { enveloppeUi ->
                        EnveloppeDropdownItem(enveloppeUi = enveloppeUi)
                    }
                )

                // Champ de note optionnel
                ChampNote(
                    valeur = uiState.note,
                    onValeurChange = viewModel::onNoteChanged
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Boutons d'actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Bouton Fractionner
                    Button(
                        onClick = { /* TODO: Implémenter le fractionnement */ },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Fractionner")
                    }

                    // Bouton Sauvegarder
                    Button(
                        onClick = { viewModel.sauvegarderTransaction() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Sauvegarder")
                    }
                }
            }
        }
    }

    // Gestion du clavier numérique en modal bottom sheet
    if (showClavier) {
        ModalBottomSheet(
            onDismissRequest = { showClavier = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BlocSaisieMontant(
                    montantInitial = uiState.montant,
                    onTermine = { montant ->
                        viewModel.onMontantTermine(montant)
                        // Ne pas fermer le clavier automatiquement
                    },
                    onFermer = {
                        showClavier = false
                    }
                )
            }
        }
    }

    // Gestion des messages d'erreur et de succès
    LaunchedEffect(uiState.erreur) {
        if (uiState.erreur != null) {
            // TODO: Afficher une Snackbar ou un Toast avec l'erreur
        }
    }

    LaunchedEffect(uiState.transactionReussie) {
        if (uiState.transactionReussie) {
            // TODO: Retourner à l'écran précédent ou réinitialiser le formulaire
        }
    }
}