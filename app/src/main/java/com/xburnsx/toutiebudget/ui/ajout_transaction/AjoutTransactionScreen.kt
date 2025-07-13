// chemin/simule: /ui/ajout_transaction/AjoutTransactionScreen.kt
// Dépendances: Jetpack Compose, ViewModel, Composants communs, ChampMontantUniversel

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
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.*
import com.xburnsx.toutiebudget.ui.composants_communs.ChampMontantUniversel
import com.xburnsx.toutiebudget.ui.composants_communs.EnveloppeDropdownItem
import com.xburnsx.toutiebudget.ui.composants_communs.SelecteurGenerique

/**
 * Écran principal pour ajouter une nouvelle transaction.
 * Utilise une hiérarchie de sélecteurs : Mode principal puis sous-types.
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
        
        if (uiState.isLoading) {
            // Écran de chargement
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Sélecteur de mode d'opération principal (Standard, Prêt, Dette, Paiement)
                    ModesOperationSelector(
                        modeSelectionne = uiState.modeOperation,
                        onModeChange = viewModel::onModeOperationChanged
                    )

                    // Sélecteur de sous-type selon le mode choisi
                    when (uiState.modeOperation) {
                        "Standard" -> {
                            TypeTransactionSelector(
                                typeSelectionne = uiState.typeTransaction,
                                onTypeChange = viewModel::onTypeTransactionChanged
                            )
                        }
                        "Prêt" -> {
                            TypePretSelector(
                                typeSelectionne = uiState.typePret,
                                onTypeChange = viewModel::onTypePretChanged
                            )
                        }
                        "Dette" -> {
                            TypeDetteSelector(
                                typeSelectionne = uiState.typeDette,
                                onTypeChange = viewModel::onTypeDetteChanged
                            )
                        }
                        // "Paiement" n'a pas de sous-types
                    }

                    // Champ de montant avec votre clavier original
                    ChampMontantUniversel(
                        montant = uiState.montant.toLongOrNull() ?: 0L,
                        onMontantChange = { nouveauMontant ->
                            viewModel.onMontantDirectChange(nouveauMontant.toString())
                        },
                        libelle = obtenirLibelleMontant(uiState),
                        nomDialog = obtenirLibelleMontant(uiState),
                        isMoney = true,
                        icone = obtenirIconeMontant(uiState),
                        estObligatoire = true,
                        tailleMontant = 20.sp,
                        couleurMontant = obtenirCouleurMontant(uiState)
                    )

                    // Sélection du compte
                    SelecteurGenerique(
                        options = uiState.comptesDisponibles,
                        optionSelectionnee = uiState.compteSelectionne,
                        onSelectionChange = viewModel::onCompteSelected,
                        libelle = "Compte",
                        obtenirTextePourOption = { it.nom },
                        icone = Icons.Default.AccountBalance
                    )

                    // Champ de tiers (pour Standard/Dépense ET tous les autres modes sauf Standard/Revenu)
                    val doitAfficherTiers = when (uiState.modeOperation) {
                        "Standard" -> uiState.typeTransaction == "Dépense"
                        else -> true
                    }
                    
                    if (doitAfficherTiers) {
                        val labelTiers = obtenirLabelTiers(uiState)
                        
                        ChampTiers(
                            valeur = uiState.tiers,
                            onValeurChange = viewModel::onTiersChanged,
                            libelle = labelTiers
                        )
                    }

                    // Sélection de l'enveloppe (uniquement pour les dépenses en mode Standard)
                    if (uiState.modeOperation == "Standard" && 
                        uiState.typeTransaction == "Dépense" && 
                        uiState.enveloppesFiltrees.isNotEmpty()) {
                        
                        SelecteurGenerique(
                            options = uiState.enveloppesFiltrees.values.flatten(),
                            optionSelectionnee = uiState.enveloppeSelectionnee,
                            onSelectionChange = viewModel::onEnveloppeSelected,
                            libelle = "Enveloppe à débiter",
                            obtenirTextePourOption = { "${it.nom} (${it.solde}$)" },
                            icone = Icons.Default.Category,
                            itemComposable = { enveloppe -> 
                                EnveloppeDropdownItem(enveloppeUi = enveloppe)
                            }
                        )
                    }

                    // Note
                    ChampNoteTransaction(
                        note = uiState.note,
                        onNoteChange = viewModel::onNoteChanged,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Affichage d'erreur si nécessaire
                    uiState.messageErreur?.let { message ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Bouton de sauvegarde (gardé comme dans votre version originale)
                if (!uiState.transactionReussie) {
                    Button(
                        onClick = { viewModel.sauvegarderTransaction() },
                        enabled = uiState.montant.isNotBlank() && 
                                 uiState.compteSelectionne != null &&
                                 (uiState.modeOperation != "Standard" || 
                                  uiState.typeTransaction != "Dépense" || 
                                  uiState.enveloppeSelectionnee != null),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("💾 Sauvegarder la transaction")
                    }
                } else {
                    // Indication de succès avec bouton pour créer une nouvelle transaction
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "✅ Transaction sauvegardée avec succès !",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.nouvelleTransaction() }
                        ) {
                            Text("➕ Nouvelle transaction")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Obtient le libellé du montant selon le mode et type sélectionnés.
 */
private fun obtenirLibelleMontant(uiState: AjoutTransactionUiState): String {
    return when (uiState.modeOperation) {
        "Standard" -> {
            if (uiState.typeTransaction == "Dépense") "Montant de la dépense"
            else "Montant du revenu"
        }
        "Prêt" -> {
            if (uiState.typePret == "Prêt accordé") "Montant du prêt accordé"
            else "Montant du remboursement reçu"
        }
        "Dette" -> {
            if (uiState.typeDette == "Dette contractée") "Montant de la dette contractée"
            else "Montant du remboursement donné"
        }
        "Paiement" -> "Montant du paiement"
        else -> "Montant"
    }
}

/**
 * Obtient l'icône du montant selon le mode et type sélectionnés.
 */
private fun obtenirIconeMontant(uiState: AjoutTransactionUiState) = when (uiState.modeOperation) {
    "Standard" -> {
        if (uiState.typeTransaction == "Dépense") Icons.Default.ShoppingCart
        else Icons.Default.TrendingUp
    }
    "Prêt" -> {
        if (uiState.typePret == "Prêt accordé") Icons.Default.CallMade
        else Icons.Default.CallReceived
    }
    "Dette" -> {
        if (uiState.typeDette == "Dette contractée") Icons.Default.CallReceived
        else Icons.Default.CallMade
    }
    "Paiement" -> Icons.Default.Payment
    else -> Icons.Default.AttachMoney
}

/**
 * Obtient la couleur du montant selon le mode et type sélectionnés.
 */
private fun obtenirCouleurMontant(uiState: AjoutTransactionUiState): Color {
    return when (uiState.modeOperation) {
        "Standard" -> {
            if (uiState.typeTransaction == "Dépense") Color(0xFFE57373)
            else Color(0xFF81C784)
        }
        "Prêt" -> {
            if (uiState.typePret == "Prêt accordé") Color(0xFFFF8A65)
            else Color(0xFF81C784)
        }
        "Dette" -> {
            if (uiState.typeDette == "Dette contractée") Color(0xFF81C784)
            else Color(0xFFE57373)
        }
        "Paiement" -> Color(0xFFE57373)
        else -> Color(0xFF81C784)
    }
}

/**
 * Obtient le label du champ tiers selon le mode et type sélectionnés.
 */
private fun obtenirLabelTiers(uiState: AjoutTransactionUiState): String {
    return when (uiState.modeOperation) {
        "Standard" -> "Payé à"
        "Prêt" -> {
            if (uiState.typePret == "Prêt accordé") "Prêté à"
            else "Remboursement de"
        }
        "Dette" -> {
            if (uiState.typeDette == "Dette contractée") "Emprunté à"
            else "Remboursement à"
        }
        "Paiement" -> "Payé à"
        else -> "Tiers"
    }
}