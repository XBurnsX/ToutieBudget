// chemin/simule: /ui/ajout_transaction/composants/ActionsBoutons.kt
package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ActionsBoutons(
    onFractionnerClick: () -> Unit,
    onSauvegarderClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedButton(
            onClick = onFractionnerClick,
            modifier = Modifier.weight(1f)
        ) {
            Text("Fractionner", fontSize = 16.sp)
        }
        Button(
            onClick = onSauvegarderClick,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Sauvegarder", fontSize = 16.sp)
        }
    }
}
