// chemin/simule: /ui/ajout_transaction/composants/ModeOperationSelector.kt
package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun ModeOperationSelector(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BoutonMode("Prêt", Icons.Default.ArrowDownward, Color.Red, selectedMode == "Prêt", { onModeSelected("Prêt") }, Modifier.weight(1f))
        BoutonMode("Remboursement", Icons.Default.ArrowUpward, Color.Green, selectedMode == "Remboursement", { onModeSelected("Remboursement") }, Modifier.weight(1f))
        BoutonMode("Dette", Icons.Default.ArrowUpward, Color.Green, selectedMode == "Dette", { onModeSelected("Dette") }, Modifier.weight(1f))
        BoutonMode("Paiement", Icons.Default.ArrowDownward, Color.Red, selectedMode == "Paiement", { onModeSelected("Paiement") }, Modifier.weight(1f))
    }
}

@Composable
private fun BoutonMode(
    texte: String,
    icone: ImageVector,
    couleurIcone: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
            contentColor = Color.White
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            width = if (isSelected) 1.5.dp else 1.dp
        )
    ) {
        Icon(imageVector = icone, contentDescription = texte, tint = couleurIcone)
        Spacer(Modifier.width(4.dp))
        Text(texte, maxLines = 1)
    }
}
