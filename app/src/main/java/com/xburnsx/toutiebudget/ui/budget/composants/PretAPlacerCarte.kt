// chemin/simule: /ui/budget/composants/PretAPlacerCarte.kt
package com.xburnsx.toutiebudget.ui.budget.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

@Composable
fun PretAPlacerCarte(
    nomCompte: String,
    montant: Double,
    couleurCompte: String
) {
    val formattedAmount = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).format(montant)
    val couleurFond = try {
        Color(android.graphics.Color.parseColor(couleurCompte))
    } catch (e: Exception) {
        Color(0xFF2E7D32) // Couleur par défaut si erreur
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(couleurFond)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Prêt à placer",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = nomCompte,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = formattedAmount,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ApercuPretAPlacerCarte() {
    // Utilisation du composant avec une valeur d'exemple
    PretAPlacerCarte(
        nomCompte = "Compte Principal",
        montant = 1234.56,
        couleurCompte = "#2196F3"
    )
}