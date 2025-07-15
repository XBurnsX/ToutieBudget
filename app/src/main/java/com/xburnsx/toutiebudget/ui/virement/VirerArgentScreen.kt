// chemin/simule: /ui/virement/VirerArgentScreen.kt
// Dépendances: Jetpack Compose, Material3, ChampArgent, ViewModel

package com.xburnsx.toutiebudget.ui.virement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.ui.composants_communs.ChampMontantUniversel
import com.xburnsx.toutiebudget.ui.virement.composants.SelecteurEnveloppeVirement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirerArgentScreen(
    viewModel: VirerArgentViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Afficher message de succès et navigation automatique
    androidx.compose.runtime.LaunchedEffect(uiState.virementReussi) {
        if (uiState.virementReussi) {
            snackbarHostState.showSnackbar(
                message = "✅ Virement effectué avec succès !",
                duration = SnackbarDuration.Short
            )
            // Délai pour laisser le temps de voir le message
            kotlinx.coroutines.delay(1500)
            onNavigateBack()
            viewModel.resetVirementReussi() // Reset pour éviter la navigation en boucle
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
                title = { Text("Virer de l'argent", fontWeight = FontWeight.Bold) },
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
            
            // *** NOUVEAU : Champ d'argent pour le montant du virement ***
            ChampMontantUniversel(
                montant = uiState.montant.toLongOrNull() ?: 0L,
                onMontantChange = { nouveauMontantEnCentimes ->
                    viewModel.onMontantChange(nouveauMontantEnCentimes.toString())
                },
                libelle = "Montant à virer",
                icone = Icons.Default.SwapHoriz,
                estObligatoire = true,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Champs de sélection source et destination
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sélecteur de source - MÊME LOGIQUE QUE CELUI DU BAS
                val sourcesEnveloppes = uiState.destinationsDisponibles
                    .flatMap { (categorie, items) ->
                        items.filterIsInstance<com.xburnsx.toutiebudget.ui.virement.ItemVirement.EnveloppeItem>()
                            .map { it.enveloppe }
                    }
                    .filter { enveloppe ->
                        // Cacher l'enveloppe si elle est sélectionnée dans la destination
                        val destinationEnveloppe = (uiState.destinationSelectionnee as? com.xburnsx.toutiebudget.ui.virement.ItemVirement.EnveloppeItem)?.enveloppe
                        enveloppe.id != destinationEnveloppe?.id
                    }
                    .groupBy { enveloppe ->
                        // Trouver la catégorie de l'enveloppe
                        val categorie = uiState.destinationsDisponibles.entries
                            .find { (_, items) -> 
                                items.any { item -> 
                                    item is com.xburnsx.toutiebudget.ui.virement.ItemVirement.EnveloppeItem && 
                                    item.enveloppe.id == enveloppe.id 
                                }
                            }?.key ?: "Autre"
                        categorie
                    }
                
                // Extraire les comptes chèque avec montant "prêt à placer" positif
                val comptesPretAPlacer = uiState.sourcesDisponibles["Prêt à placer"]
                    ?.filterIsInstance<com.xburnsx.toutiebudget.ui.virement.ItemVirement.CompteItem>()
                    ?.map { it.compte }
                    ?.filterIsInstance<com.xburnsx.toutiebudget.data.modeles.CompteCheque>()
                    ?: emptyList()

                SelecteurEnveloppeVirement(
                    enveloppes = sourcesEnveloppes,
                    enveloppeSelectionnee = (uiState.sourceSelectionnee as? com.xburnsx.toutiebudget.ui.virement.ItemVirement.EnveloppeItem)?.enveloppe,
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
                
                // Sélecteur de destination - CELUI QUI MARCHE
                val destinationsEnveloppes = uiState.destinationsDisponibles
                    .flatMap { (categorie, items) ->
                        items.filterIsInstance<com.xburnsx.toutiebudget.ui.virement.ItemVirement.EnveloppeItem>()
                            .map { it.enveloppe }
                    }
                    .filter { enveloppe ->
                        // Cacher l'enveloppe si elle est sélectionnée dans la source
                        val sourceEnveloppe = (uiState.sourceSelectionnee as? com.xburnsx.toutiebudget.ui.virement.ItemVirement.EnveloppeItem)?.enveloppe
                        enveloppe.id != sourceEnveloppe?.id
                    }
                    .groupBy { enveloppe ->
                        // Trouver la catégorie de l'enveloppe
                        val categorie = uiState.destinationsDisponibles.entries
                            .find { (_, items) -> 
                                items.any { item -> 
                                    item is com.xburnsx.toutiebudget.ui.virement.ItemVirement.EnveloppeItem && 
                                    item.enveloppe.id == enveloppe.id 
                                }
                            }?.key ?: "Autre"
                        categorie
                    }
                
                // Extraire les comptes chèque avec montant "prêt à placer" positif (destinations)
                val comptesPretAPlacerDestination = uiState.destinationsDisponibles["Prêt à placer"]
                    ?.filterIsInstance<com.xburnsx.toutiebudget.ui.virement.ItemVirement.CompteItem>()
                    ?.map { it.compte }
                    ?.filterIsInstance<com.xburnsx.toutiebudget.data.modeles.CompteCheque>()
                    ?: emptyList()

                SelecteurEnveloppeVirement(
                    enveloppes = destinationsEnveloppes,
                    enveloppeSelectionnee = (uiState.destinationSelectionnee as? com.xburnsx.toutiebudget.ui.virement.ItemVirement.EnveloppeItem)?.enveloppe,
                    onEnveloppeChange = { enveloppeUi ->
                        viewModel.onEnveloppeSelected(enveloppeUi, isSource = false)
                    },
                    obligatoire = true,
                    comptesPretAPlacer = comptesPretAPlacerDestination
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Bouton de virement
            Button(
                onClick = { viewModel.onVirementExecute() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                enabled = uiState.sourceSelectionnee != null && 
                         uiState.destinationSelectionnee != null && 
                         (uiState.montant.toLongOrNull() ?: 0L) > 0
            ) {
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
            
            // Affichage d'erreur
            uiState.erreur?.let { erreur ->
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

/**
 * Composant pour les champs de sélection source/destination
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChampSelecteur(
    label: String, 
    valeur: String, 
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    OutlinedTextField(
        value = valeur,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        leadingIcon = { 
            Icon(
                imageVector = icone,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary
            ) 
        },
        trailingIcon = { 
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) 
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White.copy(alpha = 0.05f)
        )
    )
}