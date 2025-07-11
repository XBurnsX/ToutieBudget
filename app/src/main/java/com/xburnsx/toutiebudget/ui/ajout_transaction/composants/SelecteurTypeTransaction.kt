// chemin/simule: /ui/ajout_transaction/composants/SelecteurTypeTransaction.kt
package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SelecteurTypeTransaction(
    typeSelectionne: String,
    onTypeSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .clip(RoundedCornerShape(50))
            .background(Color.DarkGray)
            .padding(4.dp)
    ) {
        Option("- Dépense", typeSelectionne == "Dépense", Color(0xFFB71C1C), { onTypeSelected("Dépense") }, Modifier.weight(1f))
        Option("+ Revenu", typeSelectionne == "Revenu", Color(0xFF1B5E20), { onTypeSelected("Revenu") }, Modifier.weight(1f))
    }
}

@Composable
private fun Option(
    texte: String,
    isSelected: Boolean,
    couleurSelection: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) couleurSelection else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texte,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}
