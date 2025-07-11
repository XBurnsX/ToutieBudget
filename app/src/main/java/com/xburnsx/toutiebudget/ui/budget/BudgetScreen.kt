// chemin/simule: /ui/budget/BudgetScreen.kt
package com.xburnsx.toutiebudget.ui.budget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.ui.budget.composants.EnveloppeItem
import com.xburnsx.toutiebudget.ui.budget.composants.PretAPlacerCarte

@Composable
fun BudgetScreen(viewModel: BudgetViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(uiState.messageChargement, color = Color.Gray)
                }
            } else if (uiState.erreur != null) {
                Text(
                    text = uiState.erreur!!,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Bandeaux "Prêt à placer" pour chaque compte avec solde > 0
                    items(uiState.bandeauxPretAPlacer, key = { it.compteId }) { bandeau ->
                        PretAPlacerCarte(
                            nomCompte = bandeau.nomCompte,
                            montant = bandeau.montant,
                            couleurCompte = bandeau.couleurCompte
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Liste des enveloppes
                    items(uiState.enveloppes, key = { it.id }) { enveloppe ->
                        EnveloppeItem(enveloppe = enveloppe)
                    }
                }
            }
        }
    }
}
