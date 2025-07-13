// chemin/simule: /ui/ajout_transaction/composants/AffichageMontant.kt
package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxWidth
import com.xburnsx.toutiebudget.utils.formatToCurrency

@Composable
fun AffichageMontant(
    valeurEnCentimes: String,
    typeTransaction: String,
    modifier: Modifier = Modifier
) {
    val couleurMontant = if (typeTransaction == "Dépense") Color.Red else Color.Green

    Text(
        text = formatToCurrency(valeurEnCentimes),
        fontSize = 52.sp,
        fontWeight = FontWeight.Bold,
        color = couleurMontant,
        modifier = modifier
            .padding(vertical = 16.dp)
            .fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}
