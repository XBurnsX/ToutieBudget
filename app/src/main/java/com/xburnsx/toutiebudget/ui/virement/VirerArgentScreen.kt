// chemin/simule: /ui/virement/VirerArgentScreen.kt
// Dépendances: Jetpack Compose, Material3, ChampArgent, ViewModel

package com.xburnsx.toutiebudget.ui.virement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel
import java.util.Date
import com.xburnsx.toutiebudget.ui.virement.composants.SelecteurCompteVirement
import com.xburnsx.toutiebudget.ui.virement.composants.SelecteurEnveloppeVirement
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.ui.budget.composants.SelecteurMoisAnnee

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VirerArgentScreen(
    viewModel: VirerArgentViewModel,
    onNavigateBack: () -> Unit = {},
    enveloppePreselectionnee: String? = null,
    montantPreselectionne: Double? = null,
    moisPreselectionne: Date? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Configuration automatique des paramètres pré-remplis
    LaunchedEffect(enveloppePreselectionnee, montantPreselectionne, moisPreselectionne) {
        if (enveloppePreselectionnee != null && montantPreselectionne != null && moisPreselectionne != null) {
            // Forcer le mode enveloppes pour les virements depuis GererSoldeNegatif
            viewModel.changerMode(VirementMode.ENVELOPPES)
            
            // Configurer le mois
            viewModel.changerMois(moisPreselectionne)
            
            // Configurer le montant (convertir en centimes)
            val montantCentimes = (montantPreselectionne * 100).toLong()
            viewModel.onMontantChange(montantCentimes.toString())
        }
    }
    
    // Recharger les données quand l'écran s'ouvre pour s'assurer d'avoir les dernières données
    LaunchedEffect(Unit) {
        viewModel.rechargerDonnees()
    }
    
    // Présélectionner l'enveloppe destination APRÈS le rechargement des données
    LaunchedEffect(uiState.destinationsDisponibles) {
        if (enveloppePreselectionnee != null && uiState.destinationsDisponibles.isNotEmpty()) {
            // Chercher l'enveloppe dans les destinations disponibles
            val enveloppeDestination = uiState.destinationsDisponibles
                .flatMap { (_, items) -> items }
                .filterIsInstance<ItemVirement.EnveloppeItem>()
                .find { it.enveloppe.id == enveloppePreselectionnee }
                ?.enveloppe
            
            // Présélectionner l'enveloppe destination
            enveloppeDestination?.let { enveloppe ->
                viewModel.onEnveloppeSelected(enveloppe, isSource = false)
            }
        }
    }
    
    // Maintenir la présélection de l'enveloppe destination
    LaunchedEffect(uiState.destinationSelectionnee) {
        if (enveloppePreselectionnee != null && uiState.destinationsDisponibles.isNotEmpty()) {
            // Vérifier si l'enveloppe est toujours sélectionnée
            val destinationActuelle = uiState.destinationSelectionnee as? ItemVirement.EnveloppeItem
            if (destinationActuelle?.enveloppe?.id != enveloppePreselectionnee) {
                // Re-présélectionner si elle a été désélectionnée
                val enveloppeDestination = uiState.destinationsDisponibles
                    .flatMap { (_, items) -> items }
                    .filterIsInstance<ItemVirement.EnveloppeItem>()
                    .find { it.enveloppe.id == enveloppePreselectionnee }
                    ?.enveloppe
                
                enveloppeDestination?.let { enveloppe ->
                    viewModel.onEnveloppeSelected(enveloppe, isSource = false)
                }
            }
        }
    }

    // 🚀 NAVIGATION **INSTANTANÉE** - ZÉRO DÉLAI
    LaunchedEffect(uiState.virementReussi) {
        if (uiState.virementReussi) {
            viewModel.onVirementReussiHandled() // 🧹 Nettoyage
            onNavigateBack()                   // ⚡ Navigation IMMÉDIATE
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { snackbarData ->
                    Snackbar(
                        snackbarData = snackbarData,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        actionColor = Color.White
                    )
                }
            )
        },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("Virer de l'argent", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onNavigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Sélecteur de mois - SEULEMENT pour le mode enveloppes
                    if (uiState.mode == VirementMode.ENVELOPPES) {
                        SelecteurMoisAnnee(
                            moisSelectionne = uiState.moisSelectionne,
                            onMoisChange = { viewModel.changerMois(it) },
                            modifier = Modifier.width(140.dp)
                        )
                    }
                },
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
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Sélecteur de mode - TOUJOURS VISIBLE
            ModeSelector(
                selectedMode = uiState.mode,
                onModeChange = { viewModel.changerMode(it) }
            )
            Spacer(modifier = Modifier.height(24.dp))

            // *** CHAMP D'ARGENT pour le montant du virement ***
            ChampUniversel(
                valeur = uiState.montant.toLongOrNull() ?: 0L,
                onValeurChange = { viewModel.onMontantChange(it.toString()) },
                libelle = "Montant à virer",
                utiliserClavier = true,
                isMoney = true,
                icone = Icons.Default.SwapHoriz,
                estObligatoire = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Champs de sélection source et destination
            if (uiState.mode == VirementMode.ENVELOPPES) {
                // INTERFACE ORIGINALE DES ENVELOPPES
                ContenuModeEnveloppesOriginal(uiState, viewModel)
            } else {
                // NOUVELLE INTERFACE POUR LES COMPTES
                ContenuModeComptes(uiState, viewModel)
            }

            Spacer(modifier = Modifier.weight(1f))

            

            // Bouton de virement
            Button(
                onClick = { viewModel.onVirementExecute() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                enabled = !uiState.isLoading && 
                        uiState.sourceSelectionnee != null &&
                        uiState.destinationSelectionnee != null &&
                        (uiState.montant.toLongOrNull() ?: 0L) > 0
            ) {
                if (uiState.isLoading) {
                    // 🔥 Affichage pendant le virement
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Virement en cours...",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                } else {
                    // Affichage normal
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Effectuer le virement",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // Affichage d'erreur avec dialogue pour les erreurs de provenance
            uiState.erreur?.let { erreur ->
                if (VirementErrorMessages.estErreurProvenance(erreur)) {
                    // Dialogue d'erreur pour les conflits de provenance
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
                    // Affichage normal pour les autres erreurs
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(
    selectedMode: VirementMode,
    onModeChange: (VirementMode) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
            selected = selectedMode == VirementMode.ENVELOPPES,
            onClick = { onModeChange(VirementMode.ENVELOPPES) },
            icon = {},
            label = { Text("Enveloppes") }
        )
        SegmentedButton(
            shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
            selected = selectedMode == VirementMode.COMPTES,
            onClick = { onModeChange(VirementMode.COMPTES) },
            icon = {},
            label = { Text("Comptes") }
        )
    }
}

@Composable
private fun ContenuModeEnveloppesOriginal(
    uiState: VirerArgentUiState,
    viewModel: VirerArgentViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sélecteur de source - LOGIQUE ORIGINALE
        val sourcesEnveloppes = uiState.sourcesDisponibles
            .flatMap { (
                           _, items) ->
                items.filterIsInstance<ItemVirement.EnveloppeItem>()
                    .map { it.enveloppe }
            }
            .filter { enveloppe ->
                // Cacher l'enveloppe si elle est sélectionnée dans la destination
                val destinationEnveloppe = (uiState.destinationSelectionnee as? ItemVirement.EnveloppeItem)?.enveloppe
                enveloppe.id != destinationEnveloppe?.id
            }
            .groupBy { enveloppe ->
                // Trouver la catégorie de l'enveloppe
                val categorie = uiState.sourcesDisponibles.entries
                    .find { (_, items) ->
                        items.any { item ->
                            item is ItemVirement.EnveloppeItem &&
                            item.enveloppe.id == enveloppe.id
                        }
                    }?.key ?: "Autre"
                categorie
            }

        // Extraire les comptes chèque avec montant "prêt à placer" positif
        val comptesPretAPlacer = uiState.sourcesDisponibles["Prêt à placer"]
            ?.filterIsInstance<ItemVirement.CompteItem>()
            ?.map { it.compte }
            ?.filterIsInstance<CompteCheque>()
            ?: emptyList()

        SelecteurEnveloppeVirement(
            enveloppes = sourcesEnveloppes,
            enveloppeSelectionnee = (uiState.sourceSelectionnee as? ItemVirement.EnveloppeItem)?.enveloppe,
            onEnveloppeChange = { enveloppeUi ->
                viewModel.onEnveloppeSelected(enveloppeUi, isSource = true)
            },
            obligatoire = true,
            comptesPretAPlacer = comptesPretAPlacer
        )

        // Flèche indicative
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = "Virement",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }

        // Sélecteur de destination - LOGIQUE ORIGINALE
        val destinationsEnveloppes = uiState.destinationsDisponibles
            .flatMap { (_, items) ->
                items.filterIsInstance<ItemVirement.EnveloppeItem>()
                    .map { it.enveloppe }
            }
            .filter { enveloppe ->
                // Cacher l'enveloppe si elle est sélectionnée dans la source
                val sourceEnveloppe = (uiState.sourceSelectionnee as? ItemVirement.EnveloppeItem)?.enveloppe
                enveloppe.id != sourceEnveloppe?.id
            }
            .groupBy { enveloppe ->
                // Trouver la catégorie de l'enveloppe
                val categorie = uiState.destinationsDisponibles.entries
                    .find { (_, items) ->
                        items.any { item ->
                            item is ItemVirement.EnveloppeItem &&
                            item.enveloppe.id == enveloppe.id
                        }
                    }?.key ?: "Autre"
                categorie
            }

        // Extraire les comptes chèque avec montant "prêt à placer" positif (destinations)
        val comptesPretAPlacerDestination = uiState.destinationsDisponibles["Prêt à placer"]
            ?.filterIsInstance<ItemVirement.CompteItem>()
            ?.map { it.compte }
            ?.filterIsInstance<CompteCheque>()
            ?: emptyList()

        SelecteurEnveloppeVirement(
            enveloppes = destinationsEnveloppes,
            enveloppeSelectionnee = (uiState.destinationSelectionnee as? ItemVirement.EnveloppeItem)?.enveloppe,
            onEnveloppeChange = { enveloppeUi ->
                viewModel.onEnveloppeSelected(enveloppeUi, isSource = false)
            },
            obligatoire = true,
            comptesPretAPlacer = comptesPretAPlacerDestination
        )
    }
}

@Composable
private fun ContenuModeComptes(
    uiState: VirerArgentUiState,
    viewModel: VirerArgentViewModel
) {
    // Aplatir la map de comptes en une seule liste pour les sélecteurs
    val sources = uiState.sourcesDisponibles
    val destinations = uiState.destinationsDisponibles

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SelecteurCompteVirement(
            label = "Compte source",
            groupesDeComptes = sources,
            itemSelectionne = uiState.sourceSelectionnee,
            onItemSelected = { item ->
                viewModel.onItemSelected(item)
            },
            onOuvrirSelecteur = { viewModel.ouvrirSelecteur(SelecteurOuvert.SOURCE) },
            selecteurOuvert = uiState.selecteurOuvert == SelecteurOuvert.SOURCE
        )

        Icon(
            imageVector = Icons.Default.SwapHoriz,
            contentDescription = "Virement",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.CenterHorizontally)
        )

        SelecteurCompteVirement(
            label = "Compte destination",
            groupesDeComptes = destinations,
            itemSelectionne = uiState.destinationSelectionnee,
            onItemSelected = { item ->
                viewModel.onItemSelected(item)
            },
            onOuvrirSelecteur = { viewModel.ouvrirSelecteur(SelecteurOuvert.DESTINATION) },
            selecteurOuvert = uiState.selecteurOuvert == SelecteurOuvert.DESTINATION
        )
    }
}

