// chemin/simule: /ui/ajout_transaction/composants/ModesOperationSelector.kt
// Dépendances: Jetpack Compose, Material3

package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Sélecteur de mode d'opération pour les transactions.
 * Permet de choisir entre Standard, Prêt, Dette et Paiement.
 */
@Composable
fun ModesOperationSelector(
    modeSelectionne: String,
    onModeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf(
        "Standard" to "💳",
        "Prêt" to "🤝", 
        "Emprunt" to "📊",
        "Paiement" to "💰"
    )
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Type d'opération",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        // Ligne principale avec les 4 modes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            modes.forEach { (mode, emoji) ->
                val estSelectionne = mode == modeSelectionne
                
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = estSelectionne,
                            onClick = { onModeChange(mode) }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (estSelectionne) 
                            MaterialTheme.colorScheme.primary
                        else 
                            Color(0xFF2A2A2A)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = mode,
                            color = if (estSelectionne) Color.White else Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (estSelectionne) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}