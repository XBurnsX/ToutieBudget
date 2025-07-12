// chemin/simule: /ui/budget/composants/PretAPlacerCarte.kt
/*
package com.xburnsx.toutiebudget.ui.budget.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
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

/**
 * Un modèle de carte qui met l'accent sur le montant et utilise
 * la couleur du compte dans un "badge" de statut.
 *
 * @param nomCompte Le nom du compte d'investissement.
 * @param montant Le montant disponible pour l'investissement.
 * @param couleurCompte La couleur associée au compte (format hexadécimal, ex: "#4A90E2").
 */
@Composable
fun PretAPlacerCarte1(
    nomCompte: String,
    montant: Double,
    couleurCompte: String
) {
    // --- Analyse et préparation des couleurs ---
    val couleurBadge = try {
        Color(android.graphics.Color.parseColor(couleurCompte))
    } catch (e: Exception) {
        Color(0xFF4A90E2) // Couleur par défaut
    }
    // Couleur de fond de la carte, légèrement plus claire que le noir pur
    val couleurFondCarte = Color(0xFF2C2C2E)
    // Détermine si le texte sur le badge doit être clair ou foncé pour le contraste
    val couleurTexteBadge = if (couleurBadge.luminance() > 0.5) Color.Black else Color.White

    // --- Formatage du montant ---
    val montantFormatte = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).format(montant)

    // --- Structure du composant ---
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(couleurFondCarte)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // --- Partie 1: Nom du compte et Montant ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = nomCompte,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = montantFormatte,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- Partie 2: Icône et Badge de statut ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = "Tendance",
                tint = couleurBadge, // L'icône prend la couleur du compte
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.weight(1f)) // Pousse le badge vers la droite

            // Le badge
            Box(
                modifier = Modifier
                    .background(couleurBadge, shape = RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Prêt à placer",
                    color = couleurTexteBadge,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/**
 * Calcule la luminance perçue d'une couleur.
 * Utile pour déterminer si le texte superposé doit être blanc ou noir.
 * @return Une valeur entre 0 (noir) et 1 (blanc).
 */
//fun Color.luminance(): Float {
//    return (0.2126f * red + 0.7152f * green + 0.0722f * blue)
//}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun ApercuPretAPlacerCarte1() {
    Column(Modifier.padding(vertical = 16.dp)) {
        PretAPlacerCarte(
            nomCompte = "WealthSimple",
            montant = 8354.92,
            couleurCompte = "#4A90E2" // Bleu
        )
        PretAPlacerCarte(
            nomCompte = "Disnat",
            montant = 1250.10,
            couleurCompte = "#F5A623" // Orange (texte noir sur le badge)
        )
        PretAPlacerCarte(
            nomCompte = "Placements FNB",
            montant = 25440.00,
            couleurCompte = "#BD10E0" // Mauve
        )
    }
}
*/
