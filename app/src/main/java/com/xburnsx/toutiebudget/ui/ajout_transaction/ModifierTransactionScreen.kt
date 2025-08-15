package com.xburnsx.toutiebudget.ui.ajout_transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.ChampNoteTransaction
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.FractionnementDialog
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.ModesOperationSelector
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.SelecteurCompte
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.SelecteurDate
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.SelecteurEnveloppe
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.SelecteurTiers
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.SelecteurComptePaiement
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.SelecteurTypeTransaction
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.TypeDetteSelector
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.TypePretSelector
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel
import com.xburnsx.toutiebudget.ui.composants_communs.ClavierNumerique
import com.xburnsx.toutiebudget.ui.historique.HistoriqueNavigationEvent

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

    // Observer les événements de navigation pour déclencher les actions appropriées
    val navigationEvents by viewModel.navigationEvents.collectAsState()
    LaunchedEffect(navigationEvents) {
        navigationEvents?.let { event ->
            when (event) {
                is HistoriqueNavigationEvent.TransactionModifiee -> {
                    onTransactionModified()
                    viewModel.effacerNavigationEvent()
                }

                else -> {
                    // Ignorer les autres événements
                }
            }
        }
    }

    // États pour le clavier numérique - NOMS CORRIGÉS
    var showKeyboard by remember { mutableStateOf(false) }
    var montantClavierInitial by remember { mutableStateOf(0L) }
    var onMontantChangeCallback by remember { mutableStateOf<((Long) -> Unit)?>(null) }

    // Fonction pour ouvrir le clavier - NOMS CORRIGÉS
    val ouvrirClavier = { montantInitial: Long, onMontantChange: (Long) -> Unit ->
        montantClavierInitial = montantInitial
        onMontantChangeCallback = onMontantChange
        showKeyboard = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modifier Transaction", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
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
                            // Le nouveauMontant est déjà en centimes, on le convertit en string
                            viewModel.onMontantChanged(nouveauMontant.toString())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        libelle = "Montant de la transaction",
                        utiliserClavier = false, // Désactiver le clavier intégré
                        isMoney = true,
                        couleurValeur = obtenirCouleurMontant(uiState),
                        tailleValeur = 30.sp,
                        onClicPersonnalise = {
                            // Ouvrir le clavier global quand on clique sur le champ
                            ouvrirClavier(
                                uiState.montant.toLongOrNull() ?: 0L,
                                { nouveauMontant ->
                                    viewModel.onMontantChanged(nouveauMontant.toString())
                                }
                            )
                        }
                    )

                    // Sélecteur selon le mode
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
                                tiersUtiliser = uiState.tiersUtiliser,
                                onTiersUtiliserChange = viewModel::onTexteTiersSaisiChange,
                                onTiersSelectionne = viewModel::onTiersSelectionne,
                                onCreerNouveauTiers = { /* Pas de création pour la modification */ },
                                isLoading = uiState.isLoadingTiers,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

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

                // Boutons d'action fixés en bas
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Bouton Fractionner (gauche)
                        Button(
                            onClick = { viewModel.ouvrirFractionnement() },
                            enabled = uiState.peutFractionner && !uiState.estEnTrainDeSauvegarder,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.peutFractionner) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    Color(0xFF404040)
                                },
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFF404040),
                                disabledContentColor = Color.White.copy(alpha = 0.5f)
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.CallSplit,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Fractionner",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Bouton Modifier (droite)
                        Button(
                            onClick = {
                                viewModel.modifierTransaction()
                                // L'événement de navigation se chargera du retour automatiquement
                            },
                            enabled = uiState.peutSauvegarder && !uiState.estEnTrainDeSauvegarder,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.peutSauvegarder) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color(0xFF404040)
                                },
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFF404040),
                                disabledContentColor = Color.White.copy(alpha = 0.5f)
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
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
                                        fontSize = 14.sp,
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
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Modifier",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Dialog de fractionnement
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

            // 🎯 CLAVIER NUMÉRIQUE OPTIMISÉ POUR PIXEL 7 & 8 PRO
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
    }
}
