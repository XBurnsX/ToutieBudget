// chemin/simule: /ui/budget/BudgetScreen.kt
// Dépendances: ClavierBudgetEnveloppe, BudgetViewModel
// INTÉGRATION du clavier spécialisé dans la page Budget existante

package com.xburnsx.toutiebudget.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.xburnsx.toutiebudget.R
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.ui.budget.composants.ClavierBudgetEnveloppe
import com.xburnsx.toutiebudget.ui.budget.composants.CompteBudget
import com.xburnsx.toutiebudget.ui.budget.composants.EnveloppeItem
import com.xburnsx.toutiebudget.ui.budget.composants.PretAPlacerCarte
import com.xburnsx.toutiebudget.ui.budget.composants.SelecteurMoisAnnee
import com.xburnsx.toutiebudget.ui.virement.VirementErrorMessages
import java.util.Date
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Menu

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel,
    onCategoriesClick: (() -> Unit)? = null,
    onVirementClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    onPretPersonnelClick: (() -> Unit)? = null,
    onGererSoldeNegatifClick: (() -> Unit)? = null,
    onVoirHistoriqueEnveloppe: (String, String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    var moisSelectionne by remember { mutableStateOf(Date()) }

    // 🆕 ÉTATS POUR LE CLAVIER ENVELOPPE
    var enveloppeSelectionnee by remember { mutableStateOf<EnveloppeUi?>(null) }
    var showClavierEnveloppe by remember { mutableStateOf(false) }
    var comptesDisponibles by remember { mutableStateOf<List<CompteBudget>>(emptyList()) }

    // 🆕 CHARGER LES COMPTES POUR LE CLAVIER - CORRECTION
    LaunchedEffect(uiState.bandeauxPretAPlacer, uiState.isLoading) {
        // Mettre à jour la liste chaque fois que les données changent
        if (!uiState.isLoading) {
            comptesDisponibles = uiState.bandeauxPretAPlacer
                .filter { it.montant > 0.0 } // Seulement les comptes avec de l'argent
                .map { bandeau ->
                    CompteBudget(
                        id = bandeau.compteId,
                        nom = bandeau.nomCompte,
                        pretAPlacer = bandeau.montant,
                        couleur = bandeau.couleurCompte
                    )
                }
        }
    }

    val ctx = LocalContext.current
    // L'animation a été supprimée

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = {
                    SelecteurMoisAnnee(
                        moisSelectionne = moisSelectionne,
                        onMoisChange = {
                            moisSelectionne = it
                            viewModel.chargerDonneesBudget(it)
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White
                ),
                actions = {
                    // Menu hamburger (à droite, le plus à gauche des icônes)
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Prêt personnel") },
                                onClick = {
                                    menuExpanded = false
                                    onPretPersonnelClick?.invoke()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Virer argent") },
                                onClick = {
                                    menuExpanded = false
                                    onVirementClick?.invoke()
                                }
                            )
                        }
                    }

                    // Icône des catégories (séparée)
                    IconButton(onClick = { onCategoriesClick?.invoke() }) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.gerer_categorie),
                            contentDescription = "Catégories",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Icône Settings (ouvre une page)
                    IconButton(onClick = { onSettingsClick?.invoke() }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Paramètres",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        // Contenu de la page
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.figerPretAPlacer) {
                stickyHeader {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF121212))
                            .padding(vertical = 8.dp)
                    ) {
                        uiState.bandeauxPretAPlacer.forEach { bandeau ->
                            PretAPlacerCarte(
                                nomCompte = bandeau.nomCompte,
                                montant = bandeau.montant,
                                couleurCompte = bandeau.couleurCompte
                            )
                        }
                    }
                }
            } else {
                // Bandeaux non épinglés
                items(uiState.bandeauxPretAPlacer, key = { it.compteId }) { bandeau ->
                    PretAPlacerCarte(
                        nomCompte = bandeau.nomCompte,
                        montant = bandeau.montant,
                        couleurCompte = bandeau.couleurCompte
                    )
                }
            }

            // 🚨 CARTE D'ALERTE POUR ENVELOPPES NÉGATIVES
            val enveloppesNegatives = uiState.categoriesEnveloppes
                .flatMap { it.enveloppes }
                .filter { it.solde < -0.001 }
                .count()
            
            if (enveloppesNegatives > 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFDC2626) // Rouge vif pour l'alerte
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { onGererSoldeNegatifClick?.invoke() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Attention",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "$enveloppesNegatives enveloppe${if (enveloppesNegatives > 1) "s" else ""} ont besoin d'attention !",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            // Enveloppes groupées par catégorie
            items(uiState.categoriesEnveloppes, key = { it.nomCategorie }) { categorie ->
                Column {
                    // En-tête de catégorie avec flèche pliable
                    val estOuverte = uiState.categoriesOuvertes[categorie.nomCategorie] ?: true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF121212))
                            .clickable { viewModel.toggleCategorie(categorie.nomCategorie) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = categorie.nomCategorie,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Flèche pliable
                        Icon(
                            imageVector = if (estOuverte) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (estOuverte) "Fermer la catégorie" else "Ouvrir la catégorie",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 🆕 ENVELOPPES (affichées seulement si la catégorie est ouverte)
                    if (estOuverte) {
                        val clicActif = true
                        categorie.enveloppes.forEach { enveloppe ->
                            if (clicActif) {
                                Box(
                                    modifier = Modifier.clickable {
                                        enveloppeSelectionnee = enveloppe
                                        showClavierEnveloppe = true
                                    }
                                ) {
                                    EnveloppeItem(enveloppe = enveloppe)
                                }
                            } else {
                                // Affichage simple, sans action
                                EnveloppeItem(enveloppe = enveloppe)
                            }
                        }
                    }
                }
            }
        }
    }
    }

    // 🔄 FERMETURE AUTOMATIQUE DU CLAVIER EN CAS DE SUCCÈS
    LaunchedEffect(uiState.isLoading, uiState.erreur) {
        // Fermer le clavier seulement si :
        // 1. Plus en cours de chargement (opération terminée)
        // 2. Pas d'erreur (succès)
        // 3. Le clavier est ouvert
        if (!uiState.isLoading && uiState.erreur == null && showClavierEnveloppe) {
            showClavierEnveloppe = false
            enveloppeSelectionnee = null
        }
    }

    // 🆕 CLAVIER BUDGET ENVELOPPE
    if (showClavierEnveloppe && enveloppeSelectionnee != null) {
        ClavierBudgetEnveloppe(
            enveloppe = enveloppeSelectionnee!!,
            comptesDisponibles = comptesDisponibles,
            comptePreselectionne = comptesDisponibles.firstOrNull(), // Premier compte par défaut
            onAssigner = { montantCentimes, compteSourceId ->
                // 🎯 LOGIQUE D'ASSIGNATION - AVEC GESTION D'ERREUR

                viewModel.assignerArgentAEnveloppe(
                    enveloppeId = enveloppeSelectionnee!!.id,
                    compteSourceId = compteSourceId,
                    montantCentimes = montantCentimes
                )

                // NE PAS fermer le clavier ici - on attend le résultat dans LaunchedEffect
            },
            onFermer = {
                showClavierEnveloppe = false
                enveloppeSelectionnee = null
            },
            onVoirHistorique = { enveloppeId, nomEnveloppe ->
                onVoirHistoriqueEnveloppe(enveloppeId, nomEnveloppe)
                showClavierEnveloppe = false
                enveloppeSelectionnee = null
            }
        )
    }

    // 🚨 AFFICHAGE DES ERREURS DE VALIDATION - MÊME DIALOGUE QUE VIREMENT
    uiState.erreur?.let { erreur ->
        if (VirementErrorMessages.estErreurProvenance(erreur)) {
            // Dialogue d'erreur pour les conflits de provenance - IDENTIQUE À VIREMENT
            AlertDialog(
                onDismissRequest = { viewModel.effacerErreur() },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = VirementErrorMessages.obtenirTitreDialogue(erreur),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                text = {
                    Text(
                        text = erreur,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.effacerErreur() }
                    ) {
                        Text("Compris")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.error,
                textContentColor = MaterialTheme.colorScheme.onSurface
            )
        } else {
            // Affichage normal pour les autres erreurs - IDENTIQUE À VIREMENT
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = erreur,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BudgetScreenPreview() {
    // Preview simplifié sans ViewModel

    listOf(
        EnveloppeUi(
            id = "env1",
            nom = "Courses",
            solde = 320.50,
            depense = 80.25,
            alloue = 300.0, // Exemple d'allocation ce mois
            alloueCumulatif = 380.0, // ← NOUVEAU : Total alloué depuis le début
            objectif = 400.0,
            couleurProvenance = "#4CAF50",
            statutObjectif = StatutObjectif.JAUNE
        ),
        EnveloppeUi(
            id = "env2",
            nom = "Essence",
            solde = 150.0,
            depense = 45.0,
            alloue = 195.0, // Exemple d'allocation ce mois
            alloueCumulatif = 195.0, // ← NOUVEAU : Total alloué depuis le début
            objectif = 200.0,
            couleurProvenance = "#2196F3",
            statutObjectif = StatutObjectif.VERT
        )
    )

    // Interface simplifiée pour le preview
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Text(
            text = "Budget Preview",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}