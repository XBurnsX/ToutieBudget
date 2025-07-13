// chemin/simule: /ui/ajout_transaction/AjoutTransactionScreen.kt
// Dépendances: Jetpack Compose, ViewModel, Composants communs, ChampArgent

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
import com.xburnsx.toutiebudget.ui.composants_communs.ChampArgent
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

                    // *** CHAMP D'ARGENT UNIVERSEL ***
                    ChampArgent(
                        montant = uiState.montant.toLongOrNull() ?: 0L,
                        onMontantChange = { nouveauMontant ->
                            viewModel.onMontantDirectChange(nouveauMontant.toString())
                        },
                        libelle = obtenirLibelleMontant(uiState),
                        icone = obtenirIconeMontant(uiState),
                        estObligatoire = true,
                        tailleMontant = 32.sp,  // Gros montant pour bien voir
                        couleurMontant = obtenirCouleurMontant(uiState),
                        modifier = Modifier.fillMaxWidth()
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

                    // Sélection de l'enveloppe (uniquement pour les dépenses en mode Standard)
                    if (uiState.modeOperation == "Standard" && 
                        uiState.typeTransaction == "Dépense" && 
                        uiState.compteSelectionne != null) {
                        
                        // Titre des enveloppes
                        Text(
                            text = "Enveloppes disponibles",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                        
                        // Affichage des enveloppes groupées par catégorie
                        uiState.enveloppesFiltrees.forEach { (nomCategorie, enveloppes) ->
                            if (enveloppes.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // En-tête de catégorie
                                    Text(
                                        text = nomCategorie,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    // Liste des enveloppes dans cette catégorie
                                    SelecteurGenerique(
                                        options = enveloppes,
                                        optionSelectionnee = uiState.enveloppeSelectionnee,
                                        onSelectionChange = viewModel::onEnveloppeSelected,
                                        libelle = "Choisir une enveloppe",
                                        obtenirTextePourOption = { it.nom },
                                        icone = Icons.Default.Folder,
                                        itemComposable = { enveloppeUi ->
                                            EnveloppeDropdownItem(enveloppeUi = enveloppeUi)
                                        }
                                    )
                                }
                            }
                        }
                        
                        // Message si aucune enveloppe disponible
                        if (uiState.enveloppesFiltrees.isEmpty() || 
                            uiState.enveloppesFiltrees.all { it.value.isEmpty() }) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = "Aucune enveloppe disponible pour ce compte.\nVeuillez d'abord allouer de l'argent aux enveloppes.",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // Champ de tiers (pour tous les modes sauf Standard)
                    if (uiState.modeOperation != "Standard") {
                        val labelTiers = obtenirLabelTiers(uiState)
                        
                        ChampTiers(
                            valeur = uiState.tiers,
                            onValeurChange = viewModel::onTiersChanged,
                            libelle = labelTiers
                        )
                    }

                    // Champ de note optionnel (toujours affiché)
                    ChampNote(
                        valeur = uiState.note,
                        onValeurChange = viewModel::onNoteChanged
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Boutons d'actions
                ActionsBoutons(
                    onFractionnerClick = { /* TODO: Implémenter le fractionnement */ },
                    onSauvegarderClick = { viewModel.sauvegarderTransaction() },
                    estSauvegardeActive = peutSauvegarder(uiState)
                )
            }
        }
    }

    // Gestion des messages d'erreur
    if (uiState.erreur != null) {
        LaunchedEffect(uiState.erreur) {
            // TODO: Afficher une Snackbar avec l'erreur
            // snackbarHostState.showSnackbar(uiState.erreur)
        }
    }

    // Gestion du succès de transaction
    if (uiState.transactionReussie) {
        LaunchedEffect(uiState.transactionReussie) {
            // Réinitialiser le formulaire après un délai
            viewModel.reinitialiserFormulaire()
            // TODO: Afficher un message de succès ou naviguer vers un autre écran
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
        if (uiState.typePret == "Prêt accordé") Icons.Default.CallMade  // Flèche sortante
        else Icons.Default.CallReceived  // Flèche entrante
    }
    "Dette" -> {
        if (uiState.typeDette == "Dette contractée") Icons.Default.CallReceived  // Flèche entrante
        else Icons.Default.CallMade  // Flèche sortante
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
            if (uiState.typeTransaction == "Dépense") Color(0xFFE57373) // Rouge
            else Color(0xFF81C784) // Vert
        }
        "Prêt" -> {
            if (uiState.typePret == "Prêt accordé") Color(0xFFFF8A65) // Orange (sortie)
            else Color(0xFF81C784) // Vert (entrée)
        }
        "Dette" -> {
            if (uiState.typeDette == "Dette contractée") Color(0xFF81C784) // Vert (entrée)
            else Color(0xFFE57373) // Rouge (sortie)
        }
        "Paiement" -> Color(0xFFE57373) // Rouge (sortie)
        else -> Color(0xFF81C784) // Vert par défaut
    }
}

/**
 * Obtient le label du champ tiers selon le mode et type sélectionnés.
 */
private fun obtenirLabelTiers(uiState: AjoutTransactionUiState): String {
    return when (uiState.modeOperation) {
        "Prêt" -> {
            if (uiState.typePret == "Prêt accordé") "Prêté à"
            else "Remboursé par"
        }
        "Dette" -> {
            if (uiState.typeDette == "Dette contractée") "Emprunté à"
            else "Remboursé à"
        }
        "Paiement" -> "Payé à"
        else -> "Payé à / Reçu de"
    }
}

/**
 * Détermine si le bouton sauvegarder peut être activé.
 */
private fun peutSauvegarder(uiState: AjoutTransactionUiState): Boolean {
    val montantValide = (uiState.montant.toLongOrNull() ?: 0L) > 0
    val compteSelectionne = uiState.compteSelectionne != null
    
    return when (uiState.modeOperation) {
        "Standard" -> {
            if (uiState.typeTransaction == "Dépense") {
                montantValide && compteSelectionne && uiState.enveloppeSelectionnee != null
            } else {
                montantValide && compteSelectionne
            }
        }
        "Prêt", "Dette" -> {
            montantValide && compteSelectionne && uiState.tiers.isNotBlank()
        }
        "Paiement" -> {
            montantValide && compteSelectionne && uiState.tiers.isNotBlank()
        }
        else -> {
            montantValide && compteSelectionne
        }
    }
}