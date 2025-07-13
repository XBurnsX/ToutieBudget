// chemin/simule: /ui/composants_communs/EnveloppeDropdownItem.kt
// Dépendances: Jetpack Compose, EnveloppeUi, StatutObjectif

package com.xburnsx.toutiebudget.ui.composants_communs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
 * Version adaptée pour les thèmes sombres et l'utilisation dans SelecteurGenerique.
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
            .padding(horizontal = 12.dp, vertical = 8.dp), // Padding réduit pour dropdown
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Barre colorée de statut à gauche
        Box(
            modifier = Modifier
                .width(3.dp) // Plus fine pour dropdown
                .height(32.dp) // Plus petite pour dropdown  
                .clip(RoundedCornerShape(2.dp))
                .background(
                    when (enveloppeUi.statutObjectif) {
                        StatutObjectif.GRIS -> Color.Gray
                        StatutObjectif.JAUNE -> Color(0xFFFFC107)
                        StatutObjectif.VERT -> Color(0xFF4CAF50)
                    }
                )
        )

        Spacer(modifier = Modifier.width(8.dp)) // Espacement réduit

        // Contenu principal de l'enveloppe
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = enveloppeUi.nom,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, // Police plus petite pour dropdown
                color = MaterialTheme.colorScheme.onSurface // Adapté au thème
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Disponible: ${formateurMonetaire.format(enveloppeUi.solde)}",
                fontSize = 12.sp, // Police plus petite
                color = if (enveloppeUi.solde > 0) 
                    MaterialTheme.colorScheme.onSurfaceVariant 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        // Pastille de couleur de provenance si elle existe
        if (enveloppeUi.couleurProvenance != null) {
            Box(
                modifier = Modifier
                    .size(12.dp) // Plus petite pour dropdown
                    .clip(CircleShape)
                    .background(enveloppeUi.couleurProvenance.toColor())
            )
        }
    }
}