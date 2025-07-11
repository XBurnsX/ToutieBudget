// chemin/simule: /ui/categories/composants/EnveloppeConfigItem.kt
package com.xburnsx.toutiebudget.ui.categories.composants

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.Enveloppe

@Composable
fun EnveloppeConfigItem(
    enveloppe: Enveloppe,
    onObjectifClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = enveloppe.nom,
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onObjectifClick) {
            Text(
                text = if (enveloppe.objectifMontant > 0) "${enveloppe.objectifMontant}$" else "Objectif",
                color = if (enveloppe.objectifMontant > 0) Color.Green else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
