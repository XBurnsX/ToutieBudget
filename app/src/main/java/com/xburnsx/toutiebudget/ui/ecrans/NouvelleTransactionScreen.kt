package com.xburnsx.toutiebudget.ui.ecrans

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Écran qui permet d'ajouter une nouvelle transaction
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NouvelleTransactionScreen() {
    // Contenu de l'écran Nouvelle Transaction
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Nouvelle Transaction",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Formulaire d'ajout de transaction à venir...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
