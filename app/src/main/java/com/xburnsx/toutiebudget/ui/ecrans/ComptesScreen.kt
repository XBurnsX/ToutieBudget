package com.xburnsx.toutiebudget.ui.ecrans

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Écran qui affiche la liste des comptes de l'utilisateur
 */
@Composable
fun ComptesScreen() {
    // Contenu de l'écran Comptes
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Comptes",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Liste de vos comptes à venir...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
