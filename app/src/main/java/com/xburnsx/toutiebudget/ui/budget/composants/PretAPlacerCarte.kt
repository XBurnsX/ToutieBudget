// chemin/simule: /ui/budget/composants/PretAPlacerCarte.kt
package com.xburnsx.toutiebudget.ui.budget.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import androidx.core.graphics.toColorInt

/**
 * Une version plus compacte du design "Cadre Coloré",
 * avec une hauteur réduite.
 *
 * @param nomCompte Le nom du compte d'investissement.
 * @param montant Le montant disponible pour l'investissement.
 * @param couleurCompte La couleur associée au compte (format hexadécimal, ex: "#4A90E2").
 */
@Composable
fun PretAPlacerCarte(
    nomCompte: String,
    montant: Double,
    couleurCompte: String
) {
    // --- Couleurs ---
    val couleurCadre = if (montant < 0) {
        MaterialTheme.colorScheme.error // Rouge si montant négatif
    } else {
        try {
            Color(couleurCompte.toColorInt())
        } catch (_: Exception) {
            Color(0xFF007BFF) // Couleur par défaut
        }
    }
    val couleurFond = Color(0xFF1C1C1E) // Fond gris foncé neutre

    // --- Formatage du montant ---
    val montantFormatte = MoneyFormatter.formatAmount(montant)

    // --- Structure ---
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 5.dp) // Espacement vertical réduit
            .fillMaxWidth()
            .height(74.dp) // Hauteur réduite
            // 1. La bordure colorée définit le cadre externe
            .border(
                width = 4.dp,
                color = couleurCadre,
                shape = RoundedCornerShape(18.dp)
            )
            // 2. On clip l'intérieur pour que le fond ne dépasse pas les coins arrondis
            .clip(RoundedCornerShape(18.dp))
            // 3. Le fond est appliqué à l'intérieur du cadre
            .background(couleurFond)
            // 4. Padding pour le contenu à l'intérieur du cadre
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = "Portefeuille",
                tint = couleurCadre, // Icône de la même couleur que le cadre
                modifier = Modifier.size(26.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Nom du compte et label
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nomCompte,
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Prêt à placer",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                )
            }

            // Montant
            Text(
                text = montantFormatte,
                color = if (montant >= 0) Color.White else MaterialTheme.colorScheme.error,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun ApercuPretAPlacerCarte() {
    Column(Modifier.padding(vertical = 16.dp)) {
        PretAPlacerCarte(
            nomCompte = "WealthSimple",
            montant = 8354.92,
            couleurCompte = "#007BFF" // Bleu vif
        )
        PretAPlacerCarte(
            nomCompte = "Disnat",
            montant = 1250.10,
            couleurCompte = "#28A745" // Vert
        )
        PretAPlacerCarte(
            nomCompte = "Placements",
            montant = 25440.00,
            couleurCompte = "#DC3545" // Rouge
        )
        PretAPlacerCarte(
            nomCompte = "FNB",
            montant = 950.00,
            couleurCompte = "#6f42c1" // Mauve
        )
    }
}