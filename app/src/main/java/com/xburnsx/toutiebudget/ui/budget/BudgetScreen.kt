// chemin/simule: /ui/budget/BudgetScreen.kt
package com.xburnsx.toutiebudget.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.ui.budget.composants.EnveloppeItem
import com.xburnsx.toutiebudget.ui.budget.composants.PretAPlacerCarte

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel,
    onCategoriesClick: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                title = { Text("Budget", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212), titleContentColor = Color.White),
                actions = {
                    IconButton(onClick = { onCategoriesClick?.invoke() }) {
                        Icon(Icons.Default.Category, contentDescription = "Catégories", tint = Color.White)
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
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF121212))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    // Enveloppes de cette catégorie
                    categorie.enveloppes.forEach { enveloppe ->
                        EnveloppeItem(enveloppe = enveloppe)
                    }
                }
            }
        }
    }
}


