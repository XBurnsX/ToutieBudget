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
import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.domain.usecases.ModifierTransactionUseCase
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.*
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

/**
 * Écran pour modifier une transaction existante.
 * Réutilise l'écran d'ajout de transaction avec des données pré-remplies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModifierTransactionScreen(
    transactionId: String,
    viewModel: ModifierTransactionViewModel,
    onTransactionModified: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    // Recharger les données quand l'écran s'ouvre
    LaunchedEffect(Unit) {
        viewModel.chargerTransaction(transactionId)
    }

    // Détecter le succès de la modification
    LaunchedEffect(uiState.transactionModifiee) {
        if (uiState.transactionModifiee) {
            onTransactionModified()
            // Réinitialiser immédiatement pour éviter les déclenchements multiples
            viewModel.reinitialiserTransactionModifiee()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modifier Transaction", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        println("DEBUG: Bouton retour cliqué")
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Retour")
                    }
                },
                actions = {
                    // Sélecteur de date en haut à droite
                    SelecteurDate(
                        dateSelectionnee = uiState.dateTransaction,
                        onDateChange = viewModel::onDateChanged,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
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
                        onModeChange = { /* Pas de changement de mode pour la modification */ }
                    )

                    // Sélecteur de sous-type selon le mode choisi
                    when (uiState.modeOperation) {
                        "Standard" -> {
                            SelecteurTypeTransaction(
                                typeSelectionne = uiState.typeTransaction,
                                onTypeChange = viewModel::mettreAJourTypeTransaction,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "Prêt" -> {
                            TypePretSelector(
                                typeSelectionne = uiState.typePret,
                                onTypeChange = { /* Pas de changement pour la modification */ }
                            )
                        }
                        "Emprunt" -> {
                            TypeDetteSelector(
                                typeSelectionne = uiState.typeDette,
                                onTypeChange = { /* Pas de changement pour la modification */ }
                            )
                        }
                        // "Paiement" n'a pas de sous-types
                    }

                    // Champ de montant avec clavier personnalisé
                    ChampUniversel(
                        valeur = uiState.montant.toLongOrNull() ?: 0L,
                        onValeurChange = { nouveauMontant ->
                            viewModel.onMontantChanged(nouveauMontant.toString())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        libelle = "Montant de la transaction",
                        utiliserClavier = true,
                        isMoney = true,
                        couleurValeur = obtenirCouleurMontant(uiState),
                        tailleValeur = 30.sp
                    )

                    // Sélecteur de tiers
                    SelecteurTiers(
                        tiersDisponibles = uiState.tiersDisponibles,
                        tiersSelectionne = uiState.tiersSelectionne,
                        texteSaisi = uiState.texteTiersSaisi,
                        onTexteSaisiChange = viewModel::onTexteTiersSaisiChange,
                        onTiersSelectionne = { /* Pas de changement pour la modification */ },
                        onCreerNouveauTiers = { /* Pas de création pour la modification */ },
                        isLoading = uiState.isLoadingTiers,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Sélecteur de compte
                    SelecteurCompte(
                        comptes = uiState.comptesDisponibles,
                        compteSelectionne = uiState.compteSelectionne,
                        onCompteChange = viewModel::onCompteChanged,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Sélecteur d'enveloppe (seulement pour Standard/Dépense)
                    if (uiState.modeOperation == "Standard" && uiState.typeTransaction == TypeTransaction.Depense) {
                        SelecteurEnveloppe(
                            enveloppes = uiState.enveloppesFiltrees,
                            enveloppeSelectionnee = uiState.enveloppeSelectionnee,
                            onEnveloppeChange = {
                                viewModel.onEnveloppeChanged(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            obligatoire = true
                        )
                    }

                    // Champ note facultatif
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

                // Bouton de sauvegarde fixé en bas
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.peutSauvegarder && !uiState.estEnTrainDeSauvegarder) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color(0xFF404040)
                        }
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                                         Button(
                         onClick = {
                             viewModel.modifierTransaction()
                             // Retourner immédiatement après avoir lancé la modification
                             onTransactionModified()
                         },
                        enabled = uiState.peutSauvegarder && !uiState.estEnTrainDeSauvegarder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = Color.White.copy(alpha = 0.5f)
                        )
                    ) {
                        if (uiState.estEnTrainDeSauvegarder) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Modification...",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null
                                )
                                Text(
                                    text = "Modifier la transaction",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Obtient la couleur du montant selon le mode et type de transaction sélectionnés.
 */
private fun obtenirCouleurMontant(uiState: AjoutTransactionUiState): Color {
    return when (uiState.modeOperation) {
        "Standard" -> {
            when (uiState.typeTransaction) {
                TypeTransaction.Depense -> Color(0xFFEF4444) // Rouge pour dépense
                TypeTransaction.Revenu -> Color(0xFF10B981) // Vert pour revenu
                else -> Color(0xFF10B981) // Vert par défaut
            }
        }
        "Prêt" -> {
            when (uiState.typePret) {
                "Prêt accordé" -> Color(0xFFEF4444) // Rouge pour prêt accordé
                "Remboursement reçu" -> Color(0xFF10B981) // Vert pour remboursement reçu
                else -> Color(0xFF10B981) // Vert par défaut
            }
        }
        "Emprunt" -> {
            when (uiState.typeDette) {
                "Dette contractée" -> Color(0xFF10B981) // Vert pour dette contractée
                "Remboursement donné" -> Color(0xFFEF4444) // Rouge pour remboursement donné
                else -> Color(0xFF10B981) // Vert par défaut
            }
        }
        "Paiement" -> Color(0xFFEF4444) // Rouge pour paiement
        else -> Color(0xFF10B981) // Vert par défaut
    }
} 