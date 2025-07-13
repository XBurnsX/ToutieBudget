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
    val typesPret = listOf(
        "Prêt accordé" to "💸",
        "Remboursement reçu" to "💰"
    )
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Type de prêt",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            typesPret.forEach { (type, emoji) ->
                val estSelectionne = type == typeSelectionne
                
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = estSelectionne,
                            onClick = { onTypeChange(type) }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (estSelectionne) 
                            MaterialTheme.colorScheme.secondary 
                        else 
                            Color.White.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(6.dp)
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = type,
                            color = if (estSelectionne) 
                                MaterialTheme.colorScheme.onSecondary 
                            else 
                                MaterialTheme.colorScheme.onSurface,
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