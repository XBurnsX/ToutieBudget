// chemin/simule: /ui/virement/VirerArgentScreen.kt
// Dépendances: Jetpack Compose, Material3, ChampArgent, ViewModel

package com.xburnsx.toutiebudget.ui.virement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.ui.composants_communs.ChampArgent
import com.xburnsx.toutiebudget.ui.virement.composants.SelecteurVirementSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirerArgentScreen(viewModel: VirerArgentViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Affichage du sélecteur en bas de page si ouvert
    if (uiState.selecteurOuvert != SelecteurOuvert.AUCUN) {
        val (titre, items) = if (uiState.selecteurOuvert == SelecteurOuvert.SOURCE) {
            "Sélectionner une source" to uiState.sourcesDisponibles
        } else {
            "Sélectionner une destination" to uiState.destinationsDisponibles
        }
        SelecteurVirementSheet(
            titre = titre,
            itemsGroupes = items,
            onItemSelected = { item ->
                viewModel.onItemSelected(item)
            },
            onDismiss = { viewModel.fermerSelecteur() }
        )
    }

    Scaffold(
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
            ChampArgent(
                montant = uiState.montant.toLongOrNull() ?: 0L,
                onMontantChange = { nouveauMontantEnCentimes ->
                    viewModel.onMontantChange(nouveauMontantEnCentimes.toString())
                },
                libelle = "Montant à virer",
                icone = Icons.Default.SwapHoriz,
                estObligatoire = true,
                couleurFond = Color.White.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth()
            )
            
            // Champs de sélection source et destination
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sélecteur de source
                ChampSelecteur(
                    label = "Source (d'où vient l'argent)",
                    valeur = uiState.sourceSelectionnee?.nom ?: "Sélectionner une source",
                    icone = Icons.Default.TrendingDown,
                    onClick = { viewModel.ouvrirSelecteur(SelecteurOuvert.SOURCE) }
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
                
                // Sélecteur de destination
                ChampSelecteur(
                    label = "Destination (où va l'argent)",
                    valeur = uiState.destinationSelectionnee?.nom ?: "Sélectionner une destination",
                    icone = Icons.Default.TrendingUp,
                    onClick = { viewModel.ouvrirSelecteur(SelecteurOuvert.DESTINATION) }
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