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
import com.xburnsx.toutiebudget.ui.composants_communs.ClavierNumerique
import com.xburnsx.toutiebudget.ui.composants_communs.EnveloppeDropdownItem
import com.xburnsx.toutiebudget.ui.composants_communs.SelecteurGenerique

/**
 * Écran principal pour ajouter une nouvelle transaction.
 * Permet de saisir tous les types de transactions : dépenses, revenus, prêts, etc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjoutTransactionScreen(viewModel: AjoutTransactionViewModel) {
    val uiState by viewModel.uiState.collectAsState()

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

                // Affichage du montant en gros
                AffichageMontant(
                    valeurEnCentimes = uiState.montant,
                    typeTransaction = uiState.typeTransaction
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
            }

            // Clavier numérique en bas
            ClavierNumerique(onKeyPress = viewModel::onClavierKeyPress)

            // Boutons d'actions
            ActionsBoutons(
                onFractionnerClick = { /* TODO: Implémenter le fractionnement */ },
                onSauvegarderClick = { viewModel.sauvegarderTransaction() }
            )
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