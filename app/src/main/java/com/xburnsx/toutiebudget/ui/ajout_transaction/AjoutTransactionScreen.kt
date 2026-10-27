// chemin/simule: /ui/ajout_transaction/AjoutTransactionScreen.kt
// Dépendances: Jetpack Compose, ViewModel, Composants communs, ChampMontantUniversel

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
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

    // États pour le clavier numérique - NOMS CORRIGÉS
    var showKeyboard by remember { mutableStateOf(false) }
    var montantClavierInitial by remember { mutableStateOf(0L) }
    var onMontantChangeCallback by remember { mutableStateOf<((Long) -> Unit)?>(null) }

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


    // Fonction pour ouvrir le clavier - NOMS CORRIGÉS
    val ouvrirClavier = { montantInitial: Long, onMontantChange: (Long) -> Unit ->
        montantClavierInitial = montantInitial
        onMontantChangeCallback = onMontantChange
        showKeyboard = true
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
                windowInsets = WindowInsets(0),
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
                            (uiState.comptePaiementSelectionne as? com.xburnsx.toutiebudget.data.modeles.CompteDette)
                                ?.let { dette ->
                                    val centsDus = kotlin.math.round(
                                        kotlin.math.max(0.0, -dette.soldeDette) * 100.0
                                    ).toLong()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Maximum dû : ${com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(centsDus / 100.0)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        TextButton(
                                            onClick = { viewModel.onMontantChanged(centsDus.toString()) },
                                            enabled = centsDus > 0 && !uiState.estEnTrainDeSauvegarder
                                        ) { Text("Payer le solde") }
                                    }
                                }
                        }
                        else -> {
                            // Pour les autres modes, utiliser le sélecteur de tiers normal
                            SelecteurTiers(
                                tiersDisponibles = uiState.tiersDisponibles,
                                tiersUtiliser = uiState.tiersUtiliser,
                                onTiersUtiliserChange = viewModel::onTexteTiersSaisiChange,
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

                        // AT21 : soldes illisibles = le dire, pas afficher 0 $
                        if (uiState.allocationsIndisponibles) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Soldes des enveloppes indisponibles pour ce mois.",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = viewModel::rechargerDonnees) { Text("Réessayer") }
                                }
                            }
                        }

                        // AT12 : l'argent vient d'un autre compte (avertissement seulement)
                        uiState.avertissementProvenance?.let { avertissement ->
                            Text(
                                text = "⚠ $avertissement",
                                color = Color(0xFFFFB74D),
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // AA2 : enveloppes déjà utilisées avec ce tiers, proposées seulement (un appui pour choisir)
                    if (uiState.modeOperation == "Standard" && uiState.typeTransaction == TypeTransaction.Depense &&
                        uiState.suggestionsEnveloppes.isNotEmpty() && !uiState.fractionnementEffectue
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Déjà utilisé :", color = Color.Gray, fontSize = 12.sp)
                            uiState.suggestionsEnveloppes.forEach { suggestion ->
                                FilterChip(
                                    selected = uiState.enveloppeSelectionnee?.id == suggestion.id,
                                    onClick = { viewModel.onEnveloppeChanged(suggestion) },
                                    label = { Text(suggestion.nom, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    // AA3 : plusieurs dossiers pour ce tiers → choisir lequel rembourser
                    val enRemboursement = (uiState.modeOperation == "Prêt" && uiState.typePret == "Remboursement reçu") ||
                        (uiState.modeOperation == "Emprunt" && uiState.typeDette == "Remboursement donné")
                    if (enRemboursement && uiState.dossiersDisponibles.size > 1) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Dossier à rembourser", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = uiState.dossierChoisiId == null,
                                    onClick = { viewModel.onDossierChoisi(null) },
                                    label = { Text("Du plus ancien", fontSize = 12.sp) }
                                )
                                uiState.dossiersDisponibles.forEach { dossier ->
                                    FilterChip(
                                        selected = uiState.dossierChoisiId == dossier.id,
                                        onClick = { viewModel.onDossierChoisi(dossier.id) },
                                        label = {
                                            Text(
                                                "${com.xburnsx.toutiebudget.utils.MoneyFormatter.formatAmount(kotlin.math.abs(dossier.solde))}" +
                                                    (dossier.created?.take(10)?.let { " · $it" } ?: ""),
                                                fontSize = 12.sp
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // AA1 : aperçu court du résultat
                    uiState.apercu?.let { apercu ->
                        Text(text = apercu, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                    }

                    // AA6 : date future permise, mais signalée
                    if (uiState.dateFuture) {
                        Text(
                            text = "Date dans le futur : ${uiState.dateTransaction}",
                            color = Color(0xFFFFB74D),
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth()
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
                        // Bouton Fractionner (visible uniquement en Dépense, pas pour Pret/Emprunt)
                        if (uiState.typeTransaction == TypeTransaction.Depense && uiState.modeOperation == "Standard") {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (uiState.peutFractionner && !uiState.estEnTrainDeSauvegarder) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        Color(0xFF404040)
                                    }
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.ouvrirFractionnement() },
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
                                            text = when (uiState.modeOperation) {
                                                "Standard" -> when (uiState.typeTransaction) {
                                                    TypeTransaction.Depense -> "Enregistrer la dépense"
                                                    TypeTransaction.Revenu -> "Enregistrer le revenu"
                                                    else -> "Enregistrer"
                                                }
                                                "Prêt" -> when (uiState.typePret) {
                                                    "Prêt accordé" -> "Effectuer le prêt"
                                                    "Remboursement reçu" -> "Effectuer le remboursement"
                                                    else -> "Effectuer le prêt"
                                                }
                                                "Emprunt" -> when (uiState.typeDette) {
                                                    "Dette contractée" -> "Contracter la dette"
                                                    "Remboursement donné" -> "Rembourser la dette"
                                                    else -> "Contracter la dette"
                                                }
                                                else -> "Enregistrer"
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

                // Snackbar/bandeau de confirmation en bas
                uiState.messageConfirmation?.let { message ->
                    // S'assurer que le bandeau s'affiche au-dessus des boutons
                    // Auto-disparition après 3 secondes
                    LaunchedEffect(message) {
                        kotlinx.coroutines.delay(3000)
                        viewModel.effacerConfirmation()
                        onTransactionSuccess()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                            .navigationBarsPadding()
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = message, fontSize = 14.sp)
                                uiState.statutSynchronisation?.let { statut ->
                                    Text(
                                        text = statut.libelle,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Clavier numérique
    if (showKeyboard) {
        // AA7 : aucun espacement figé selon la hauteur d'écran ; la barre système est gérée par systemBarsPadding()
        val paddingBottom = 16.dp

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
/**
 * Obtient la couleur du montant selon le mode et type de transaction sélectionnés.
 */
fun obtenirCouleurMontant(uiState: AjoutTransactionUiState): Color {
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
