// chemin/simule: /ui/budget/BudgetScreen.kt
// Dépendances: ClavierBudgetEnveloppe, BudgetViewModel
// INTÉGRATION du clavier spécialisé dans la page Budget existante

package com.xburnsx.toutiebudget.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.ui.budget.composants.EnveloppeItem
import com.xburnsx.toutiebudget.ui.budget.composants.PretAPlacerCarte
import com.xburnsx.toutiebudget.ui.budget.composants.SelecteurMoisAnnee
import com.xburnsx.toutiebudget.ui.budget.composants.ClavierBudgetEnveloppe
import com.xburnsx.toutiebudget.ui.budget.composants.CompteBudget
import com.xburnsx.toutiebudget.ui.theme.CouleurTheme
import com.xburnsx.toutiebudget.ui.virement.VirementErrorMessages
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel,
    onCategoriesClick: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    onVirementClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
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

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
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
                    // Icône de virement
                    IconButton(onClick = { onVirementClick?.invoke() }) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = "Virement d'argent",
                            tint = Color.White
                        )
                    }

                    // Icône des catégories (séparée)
                    IconButton(onClick = { onCategoriesClick?.invoke() }) {
                        Icon(
                            Icons.Default.Category,
                            contentDescription = "Catégories",
                            tint = Color.White
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Bandeaux "Prêt à placer" pour chaque compte avec solde > 0
            items(uiState.bandeauxPretAPlacer, key = { it.compteId }) { bandeau ->
                PretAPlacerCarte(
                    nomCompte = bandeau.nomCompte,
                    montant = bandeau.montant,
                    couleurCompte = bandeau.couleurCompte
                )
            }

            // Enveloppes groupées par catégorie
            items(uiState.categoriesEnveloppes, key = { it.nomCategorie }) { categorie ->
                Column {
                    // En-tête de catégorie
                    Text(
                        text = categorie.nomCategorie,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF121212))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    // 🆕 ENVELOPPES CLIQUABLES POUR OUVRIR LE CLAVIER
                    categorie.enveloppes.forEach { enveloppe ->
                        Box(
                            modifier = Modifier.clickable {
                                enveloppeSelectionnee = enveloppe
                                showClavierEnveloppe = true
                            }
                        ) {
                            EnveloppeItem(enveloppe = enveloppe)
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
    val bandeauxExemple = listOf(
        PretAPlacerUi(
            compteId = "compte1",
            nomCompte = "Compte Courant",
            montant = 1250.75,
            couleurCompte = "#4CAF50"
        ),
        PretAPlacerUi(
            compteId = "compte2",
            nomCompte = "Livret A",
            montant = 850.00,
            couleurCompte = "#2196F3"
        )
    )

    val enveloppesExemple = listOf(
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

    val categoriesExemple = listOf(
        CategorieEnveloppesUi(
            nomCategorie = "Nécessités",
            enveloppes = enveloppesExemple
        ),
        CategorieEnveloppesUi(
            nomCategorie = "Loisirs",
            enveloppes = listOf(
                EnveloppeUi(
                    id = "env3",
                    nom = "Cinéma",
                    solde = 0.0,
                    depense = 0.0,
                    alloue = 0.0, // Exemple d'allocation ce mois
                    alloueCumulatif = 0.0, // ← NOUVEAU : Total alloué depuis le début
                    objectif = 50.0,
                    couleurProvenance = null,
                    statutObjectif = StatutObjectif.GRIS
                )
            )
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