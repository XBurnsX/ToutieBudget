// chemin/simule: /ui/composants_communs/EnveloppeDropdownItem.kt
// Dépendances: Jetpack Compose, EnveloppeUi, StatutObjectif

package com.xburnsx.toutiebudget.ui.composants_communs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.budget.StatutObjectif
import com.xburnsx.toutiebudget.ui.budget.composants.toColor
import java.text.NumberFormat
import java.util.Locale

/**
 * Composant d'affichage d'une enveloppe dans un dropdown/menu déroulant.
 * Affiche le nom, le solde disponible, et la couleur de provenance de l'argent.
 *
 * @param enveloppeUi Les données de l'enveloppe à afficher
 */
@Composable
fun EnveloppeDropdownItem(enveloppeUi: EnveloppeUi) {
    val formateurMonetaire = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Barre colorée de statut à gauche
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    when (enveloppeUi.statutObjectif) {
                        StatutObjectif.GRIS -> Color.Gray
                        StatutObjectif.JAUNE -> Color(0xFFFFC107)
                        StatutObjectif.VERT -> Color(0xFF4CAF50)
                    }
                )
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Contenu principal de l'enveloppe
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = enveloppeUi.nom,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Disponible: ${formateurMonetaire.format(enveloppeUi.solde)}",
                fontSize = 14.sp,
                color = if (enveloppeUi.solde > 0) Color.DarkGray else Color.Gray
            )
        }

        // Pastille de couleur de provenance si elle existe
        if (enveloppeUi.couleurProvenance != null) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(enveloppeUi.couleurProvenance.toColor())
            )
        }
    }
}