// chemin/simule: /ui/ajout_transaction/composants/ActionsBoutons.kt
// Dépendances: Jetpack Compose, Material3

package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Boutons d'actions pour l'écran d'ajout de transaction.
 * Contient les boutons Fractionner et Sauvegarder.
 */
@Composable
fun ActionsBoutons(
    onFractionnerClick: () -> Unit,
    onSauvegarderClick: () -> Unit,
    estSauvegardeActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Bouton Fractionner (pour diviser la transaction)
        OutlinedButton(
            onClick = onFractionnerClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.CallSplit,
                contentDescription = "Fractionner",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Fractionner la transaction")
        }
        
        // Bouton Sauvegarder principal
        Button(
            onClick = onSauvegarderClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            enabled = estSauvegardeActive
        ) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = "Sauvegarder",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Sauvegarder la transaction",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}