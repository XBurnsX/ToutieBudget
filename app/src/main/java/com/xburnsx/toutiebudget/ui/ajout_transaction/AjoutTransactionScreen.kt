// chemin/simule: /ui/ajout_transaction/AjoutTransactionScreen.kt
// Dépendances: Jetpack Compose, ViewModel, Composants communs, ChampMontantUniversel

package com.xburnsx.toutiebudget.ui.ajout_transaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.ChampNoteTransaction
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.DiagnosticConnexionButton
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.FractionnementDialog
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.ModesOperationSelector
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.SelecteurCompte
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.SelecteurComptePaiement
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.SelecteurDate
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.SelecteurEnveloppe
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.SelecteurTiers
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.SelecteurTypeTransaction
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.TypeDetteSelector
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.TypePretSelector
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel
import com.xburnsx.toutiebudget.ui.composants_communs.ClavierNumerique

/**
 * Écran principal pour ajouter une nouvelle transaction.
 * Utilise une hiérarchie de sélecteurs : Mode principal puis sous-types.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjoutTransactionScreen(
    viewModel: AjoutTransactionViewModel, 
    onTransactionSuccess: () -> Unit = {},
    modePreselectionne: String? = null,
    carteCreditPreselectionnee: com.xburnsx.toutiebudget.data.modeles.CompteCredit? = null
) {

    val uiState by viewModel.uiState.collectAsState()
    
    // État du clavier global
    var afficherClavier by remember { mutableStateOf(false) }
    var montantClavier by remember { mutableStateOf(0L) }
    var onMontantChangeClavier by remember { mutableStateOf<(Long) -> Unit>({}) }

    // Recharger les données quand l'écran s'ouvre pour s'assurer d'avoir les dernières données
    LaunchedEffect(Unit) {
        viewModel.rechargerDonnees()
    }

    // Présélectionner le mode et la carte de crédit si fournis
    LaunchedEffect(modePreselectionne, carteCreditPreselectionnee) {
        if (modePreselectionne == "Paiement" && carteCreditPreselectionnee != null) {
            viewModel.onModeOperationChanged("Paiement")
            viewModel.onComptePaiementChanged(carteCreditPreselectionnee)
        }
    }

    // Détecter le succès de la transaction
    LaunchedEffect(uiState.transactionReussie) {
        if (uiState.transactionReussie) {

            onTransactionSuccess()
        }
    }

    // Fonction pour ouvrir le clavier
    val ouvrirClavier = { montantInitial: Long, onMontantChange: (Long) -> Unit ->
        montantClavier = montantInitial
        onMontantChangeClavier = onMontantChange
        afficherClavier = true
    }

    // Afficher le dialog de fractionnement si nécessaire
    if (uiState.estEnModeFractionnement) {
        val montantTotalEnCents = uiState.montant.toLongOrNull() ?: 0L
        val montantTotal = montantTotalEnCents / 100.0 // Convertir en dollars
        FractionnementDialog(
            montantTotal = montantTotal,
            enveloppesDisponibles = uiState.enveloppesFiltrees,
            fractionsInitiales = uiState.fractionsSauvegardees, // Passer les fractions sauvegardées
            onFractionnementConfirme = { fractions ->
                viewModel.confirmerFractionnement(fractions)
            },
            onDismiss = {
                viewModel.fermerFractionnement()
            },
            onOpenKeyboard = ouvrirClavier
        )
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

                    // 3. Sélecteur selon le mode
                    when (uiState.modeOperation) {
                        "Paiement" -> {
                            // Pour le mode Paiement, utiliser SelecteurComptePaiement au lieu du tiers
                            SelecteurComptePaiement(
                                comptes = uiState.comptesDisponibles.filter { compte ->
                                    compte is com.xburnsx.toutiebudget.data.modeles.CompteCredit || 
                                    compte is com.xburnsx.toutiebudget.data.modeles.CompteDette
                                },
                                compteSelectionne = uiState.comptePaiementSelectionne,
                                onCompteChange = viewModel::onComptePaiementChanged,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> {
                            // Pour les autres modes, utiliser le sélecteur de tiers normal
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
                        }
                    }

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

                    // Aucun espace supplémentaire sous le contenu scrollable
                    Spacer(modifier = Modifier.height(0.dp))
                }

                // Bouton de diagnostic (seulement si il y a une erreur)
                if (uiState.messageErreur != null) {
                    DiagnosticConnexionButton(
                        viewModel = viewModel,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Boutons de sauvegarde et fractionnement fixés en bas (collés au bas)
                if (uiState.modeOperation == "Paiement") {
                    // Mode Paiement : un seul bouton "Effectuer le paiement"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 0.dp)
                            .navigationBarsPadding()
                            .offset(y = 12.dp),
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
                                        text = "Paiement en cours...",
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
                                        text = "Effectuer le paiement",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Mode Standard : deux boutons (Fractionner + Enregistrer)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 0.dp)
                            .navigationBarsPadding()
                            .offset(y = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Bouton Fractionner (à gauche)
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = if (uiState.peutFractionner && !uiState.estEnTrainDeSauvegarder) {
                                    MaterialTheme.colorScheme.secondary // Couleur secondaire du thème
                                } else {
                                    Color(0xFF404040)
                                }
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.ouvrirFractionnement()
                                },
                                enabled = uiState.peutFractionner && !uiState.estEnTrainDeSauvegarder,
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
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                     Icon(
                                        imageVector = Icons.AutoMirrored.Filled.CallSplit,
                                        contentDescription = null
                                    )
                                    Text(
                                        text = "Fractionner",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        
                        // Bouton Enregistrer (à droite)
                        Card(
                            modifier = Modifier.weight(1f),
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
                // Aucun espace supplémentaire: coller au bas de la zone de contenu
            }
        }
    }
    
    // Clavier numérique global
    if (afficherClavier) {
        Dialog(
            onDismissRequest = {
                afficherClavier = false
                onMontantChangeClavier = {}
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            // Le Dialog garantit que le clavier sera au-dessus de tout
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Le clavier lui-même
                ClavierNumerique(
                    montantInitial = montantClavier,
                    isMoney = true,
                    suffix = "",
                    onMontantChange = { nouveauMontant ->
                        onMontantChangeClavier(nouveauMontant)
                    },
                    onFermer = {
                        afficherClavier = false
                        onMontantChangeClavier = {}
                    }
                )
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
