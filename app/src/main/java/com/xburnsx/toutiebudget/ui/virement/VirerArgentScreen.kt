// chemin/simule: /ui/virement/VirerArgentScreen.kt
package com.xburnsx.toutiebudget.ui.virement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.ui.composants_communs.ClavierNumerique
import com.xburnsx.toutiebudget.ui.virement.composants.SelecteurVirementSheet
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirerArgentScreen(viewModel: VirerArgentViewModel) {
    val uiState by viewModel.uiState.collectAsState()

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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212), titleContentColor = Color.White)
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            val montantDouble = (uiState.montant.toLongOrNull() ?: 0L) / 100.0
            Text(
                text = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).format(montantDouble),
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Green
            )
            Column(
                modifier = Modifier.weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ChampSelecteur(
                    label = "Source",
                    valeur = uiState.sourceSelectionnee?.nom ?: "",
                    onClick = { viewModel.ouvrirSelecteur(SelecteurOuvert.SOURCE) }
                )
                ChampSelecteur(
                    label = "Destination",
                    valeur = uiState.destinationSelectionnee?.nom ?: "",
                    onClick = { viewModel.ouvrirSelecteur(SelecteurOuvert.DESTINATION) }
                )
            }
            ClavierNumerique(onKeyPress = { key ->
                when (key) {
                    in "0".."9" -> {
                        val nouveauMontant = uiState.montant + key
                        viewModel.onMontantChange(nouveauMontant)
                    }
                    "del" -> {
                        if (uiState.montant.isNotEmpty()) {
                            viewModel.onMontantChange(uiState.montant.dropLast(1))
                        }
                    }
                    "." -> {
                        if (!uiState.montant.contains(".")) {
                            viewModel.onMontantChange(uiState.montant + key)
                        }
                    }
                    else -> {}
                }
            })
            Button(
                onClick = { viewModel.onVirementExecute() },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = uiState.sourceSelectionnee != null && 
                         uiState.destinationSelectionnee != null && 
                         uiState.montant.isNotEmpty()
            ) {
                Text("Virer")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChampSelecteur(label: String, valeur: String, onClick: () -> Unit) {
    OutlinedTextField(
        value = valeur,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}