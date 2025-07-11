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
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AffichageMontant(
    valeurEnCentimes: String,
    typeTransaction: String
) {
    val montantDouble = (valeurEnCentimes.toLongOrNull() ?: 0L) / 100.0
    val couleurMontant = if (typeTransaction == "Dépense") Color.Red else Color.Green

    Text(
        text = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).format(montantDouble),
        fontSize = 52.sp,
        fontWeight = FontWeight.Bold,
        color = couleurMontant,
        modifier = Modifier.padding(vertical = 16.dp)
    )
}
