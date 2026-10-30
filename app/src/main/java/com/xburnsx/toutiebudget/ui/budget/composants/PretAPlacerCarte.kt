// chemin/simule: /ui/budget/composants/PretAPlacerCarte.kt
package com.xburnsx.toutiebudget.ui.budget.composants

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import androidx.core.graphics.toColorInt

/**
 * Bandeau « Prêt à placer » d'un compte : la couleur du compte en cadre, en halo
 * et en dégradé qui s'efface vers la droite.
 *
 * @param nomCompte Le nom du compte.
 * @param montant Le montant prêt à placer.
 * @param couleurCompte La couleur associée au compte (format hexadécimal, ex: "#4A90E2").
 */
@Composable
fun PretAPlacerCarte(
    nomCompte: String,
    montant: Double,
    couleurCompte: String
) {
    // --- Couleurs ---
    val couleurCible = if (montant < 0) {
        StyleBudget.Rouge
    } else {
        try {
            Color(couleurCompte.toColorInt())
        } catch (_: Exception) {
            Color(0xFF007BFF)
        }
    }
    val couleurCadre by animateColorAsState(couleurCible, tween(400), label = "cadrePretAPlacer")
    val forme = RoundedCornerShape(18.dp)

    // --- Le montant défile vers sa nouvelle valeur ---
    val montantAnime = remember { Animatable(montant.toFloat()) }
    val premierAffichage = remember { booleanArrayOf(true) }
    LaunchedEffect(montant) {
        if (premierAffichage[0]) {
            premierAffichage[0] = false
            montantAnime.snapTo(montant.toFloat())
        } else {
            montantAnime.animateTo(montant.toFloat(), tween(600, easing = FastOutSlowInEasing))
        }
    }
    val montantAffiche = if (montantAnime.isRunning) montantAnime.value.toDouble() else montant

    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .fillMaxWidth()
            .shadow(4.dp, forme, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(forme)
            .background(
                Brush.horizontalGradient(
                    listOf(couleurCadre.copy(alpha = 0.32f), couleurCadre.copy(alpha = 0.10f), StyleBudget.CarteBas)
                )
            )
            .border(
                width = 2.dp,
                brush = Brush.horizontalGradient(listOf(couleurCadre, couleurCadre.copy(alpha = 0.35f))),
                shape = forme
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Icône dans une pastille teintée
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(couleurCadre.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = "Portefeuille",
                    tint = couleurCadre,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nomCompte,
                    color = Color.White,
                    fontSize = 18.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "Prêt à placer",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Normal,
                )
            }

            Text(
                text = MoneyFormatter.formatAmount(montantAffiche),
                color = if (montant >= 0) Color.White else StyleBudget.Rouge,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
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
