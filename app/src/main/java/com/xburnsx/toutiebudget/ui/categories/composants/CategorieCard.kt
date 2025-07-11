// chemin/simule: /ui/categories/composants/CategorieCard.kt
package com.xburnsx.toutiebudget.ui.categories.composants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.Enveloppe

@Composable
fun CategorieCard(
    nomCategorie: String,
    enveloppes: List<Enveloppe>,
    onAjouterEnveloppeClick: () -> Unit,
    onObjectifClick: (Enveloppe) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = nomCategorie.uppercase(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                IconButton(onClick = onAjouterEnveloppeClick) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Ajouter une enveloppe", tint = Color.Gray)
                }
            }
            if (enveloppes.isEmpty()) {
                Text(
                    text = "Aucune enveloppe. Cliquez sur '+' pour en ajouter une.",
                    color = Color.Gray,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                enveloppes.forEach { enveloppe ->
                    EnveloppeConfigItem(enveloppe = enveloppe, onObjectifClick = { onObjectifClick(enveloppe) })
                    Divider(color = Color.DarkGray, thickness = 0.5.dp)
                }
            }
        }
    }
}
