// chemin/simule: /ui/ajout_transaction/composants/TypeTransactionSelector.kt
// Dépendances: Jetpack Compose, Material3

package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Sélecteur de type de transaction (Dépense/Revenu) pour le mode Standard.
 */
@Composable
fun TypeTransactionSelector(
    typeSelectionne: String,
    onTypeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Type de transaction",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Bouton Dépense
            Card(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = typeSelectionne == "Dépense",
                        onClick = { onTypeChange("Dépense") }
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (typeSelectionne == "Dépense") 
                        Color(0xFFE57373)  // Rouge clair
                    else 
                        Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Dépense",
                        tint = if (typeSelectionne == "Dépense") Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Dépense",
                        color = if (typeSelectionne == "Dépense") Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (typeSelectionne == "Dépense") FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            
            // Bouton Revenu
            Card(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = typeSelectionne == "Revenu",
                        onClick = { onTypeChange("Revenu") }
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (typeSelectionne == "Revenu") 
                        Color(0xFF81C784)  // Vert clair
                    else 
                        Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "Revenu",
                        tint = if (typeSelectionne == "Revenu") Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Revenu",
                        color = if (typeSelectionne == "Revenu") Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (typeSelectionne == "Revenu") FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}