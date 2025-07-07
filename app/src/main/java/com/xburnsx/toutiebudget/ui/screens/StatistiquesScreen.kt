package com.xburnsx.toutiebudget.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Écran qui affiche les statistiques de l'utilisateur
 */
@Composable
fun StatistiquesScreen() {
    // Contenu de l'écran Statistiques
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Statistiques",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Vos statistiques détaillées à venir...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
