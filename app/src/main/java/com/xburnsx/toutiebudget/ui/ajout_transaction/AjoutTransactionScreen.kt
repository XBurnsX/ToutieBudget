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
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.TypePretSelector
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.TypeDetteSelector
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.SelecteurCompte
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.SelecteurEnveloppe
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.SelecteurTiers
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.ChampNoteTransaction
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.*
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.DiagnosticConnexionButton

/**
 * Écran principal pour ajouter une nouvelle transaction.
 * Utilise une hiérarchie de sélecteurs : Mode principal puis sous-types.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjoutTransactionScreen(viewModel: AjoutTransactionViewModel, onTransactionSuccess: () -> Unit = {}) {

    val uiState by viewModel.uiState.collectAsState()

    // Recharger les données quand l'écran s'ouvre pour s'assurer d'avoir les dernières données
    LaunchedEffect(Unit) {
        viewModel.rechargerDonnees()
    }

    // Détecter le succès de la transaction
    LaunchedEffect(uiState.transactionReussie) {
        if (uiState.transactionReussie) {

            onTransactionSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajouter Transaction", fontWeight = FontWeight.Bold) },
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
                            SelecteurTypeTransaction(
                                typeSelectionne = uiState.typeTransaction,
                                onTypeChange = viewModel::onTypeTransactionChanged,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "Prêt" -> {
                            TypePretSelector(
                                typeSelectionne = uiState.typePret,
                                onTypeChange = viewModel::onTypePretChanged
                            )
                        }
                        "Emprunt" -> {
                            TypeDetteSelector(
                                typeSelectionne = uiState.typeDette,
                                onTypeChange = viewModel::onTypeDetteChanged
                            )
                        }
                        // "Paiement" n'a pas de sous-types
                    }

                    // 2. Champ de montant avec clavier personnalisé
                    ChampUniversel(
                        valeur = {
                            println("DEBUG SCREEN: uiState.montant = '${uiState.montant}'")
                            val valeurLong = uiState.montant.toLongOrNull() ?: 0L
                            println("DEBUG SCREEN: valeurLong = $valeurLong")
                            valeurLong
                        }(),
                        onValeurChange = { nouveauMontant ->
                            println("DEBUG SCREEN: onValeurChange appelé avec nouveauMontant = $nouveauMontant")
                            viewModel.onMontantChanged(nouveauMontant.toString())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        libelle = "Montant de la transaction",
                        utiliserClavier = true,
                        isMoney = true,
                        couleurValeur = obtenirCouleurMontant(uiState),
                        tailleValeur = 30.sp
                    )

                    // 3. Sélecteur de tiers (avant le compte)
                    SelecteurTiers(
                        tiersDisponibles = uiState.tiersDisponibles,
                        tiersSelectionne = uiState.tiersSelectionne,
                        texteSaisi = uiState.texteTiersSaisi,
                        onTexteSaisiChange = viewModel::onTexteTiersSaisiChange,
                        onTiersSelectionne = viewModel::onTiersSelectionne,
                        onCreerNouveauTiers = viewModel::onCreerNouveauTiers,
                        isLoading = uiState.isLoadingTiers,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 4. Sélecteur de compte
                    SelecteurCompte(
                        comptes = uiState.comptesDisponibles,
                        compteSelectionne = uiState.compteSelectionne,
                        onCompteChange = viewModel::onCompteChanged,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 5. Sélecteur d'enveloppe (seulement pour Standard/Dépense)
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

                    // 6. Champ note facultatif
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

                // Bouton de diagnostic (seulement si il y a une erreur)
                if (uiState.messageErreur != null) {
                    DiagnosticConnexionButton(
                        viewModel = viewModel,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
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
                            viewModel.sauvegarderTransaction()
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
                                    text = "Sauvegarde...",
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
                                    text = if (uiState.typeTransaction == TypeTransaction.Depense) {
                                        "Enregistrer la dépense"
                                    } else {
                                        "Enregistrer le revenu"
                                    },
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
