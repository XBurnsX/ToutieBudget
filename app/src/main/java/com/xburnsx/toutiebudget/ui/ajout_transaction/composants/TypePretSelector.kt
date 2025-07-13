// chemin/simule: /ui/ajout_transaction/composants/TypePretSelector.kt
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
 * Sélecteur de type de prêt : Prêt accordé ou Remboursement reçu.
 * Affiché uniquement quand le mode "Prêt" est sélectionné.
 */
@Composable
fun TypePretSelector(
    typeSelectionne: String,
    onTypeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val types = listOf(
        "Prêt accordé" to "📤",
        "Remboursement reçu" to "📥"
    )
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Type de prêt",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            types.forEach { (type, emoji) ->
                val estSelectionne = type == typeSelectionne
                
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = estSelectionne,
                            onClick = { onTypeChange(type) }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (estSelectionne) {
                            if (type == "Prêt accordé") Color(0xFFFF8A65) else Color(0xFF10B981)
                        } else {
                            Color(0xFF2A2A2A)
                        }
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = type,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (estSelectionne) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}