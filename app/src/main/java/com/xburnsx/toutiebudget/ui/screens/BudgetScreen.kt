package com.xburnsx.toutiebudget.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Écran principal de l'application qui affiche le budget global
 */
@Composable
fun BudgetScreen() {
    // Contenu de l'écran Budget
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Budget",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Contenu de l'écran Budget à venir...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
