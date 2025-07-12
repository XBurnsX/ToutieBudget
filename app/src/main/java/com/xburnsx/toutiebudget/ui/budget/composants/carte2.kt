// chemin/simule: /ui/budget/composants/PretAPlacerCarte.kt
/*
package com.xburnsx.toutiebudget.ui.budget.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

/**
 * Un design unique avec une "pastille" flottante pour l'icône
 * et une ligne de force colorée pour un look moderne et structuré.
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
    val couleurLigne = try {
        Color(android.graphics.Color.parseColor(couleurCompte))
    } catch (e: Exception) {
        Color(0xFF007BFF) // Couleur par défaut
    }
    val couleurFondCarte = Color(0xFF1F1F1F) // Un gris très foncé
    val couleurPastille = Color(0xFF2C2C2E) // Un gris légèrement plus clair

    // --- Formatage du montant ---
    val montantFormatte = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).format(montant)

    // --- Structure ---
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
    ) {
        // La carte principale en arrière-plan
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(couleurFondCarte)
                .padding(start = 56.dp), // Espace pour la pastille
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ligne de force colorée
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(couleurLigne)
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
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        // La pastille flottante par-dessus
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                .shadow(elevation = 8.dp, shape = CircleShape) // Ombre pour l'effet flottant
                .size(48.dp)
                .clip(CircleShape)
                .background(couleurPastille),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = "Portefeuille",
                tint = couleurLigne,
                modifier = Modifier.size(24.dp)
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
*/