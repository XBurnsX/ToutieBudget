// chemin/simule: /ui/budget/composants/EnveloppeItem.kt
package com.xburnsx.toutiebudget.ui.budget.composants

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
import java.text.NumberFormat
import java.util.Locale

fun String.toColor(): Color {
    return try {
        Color(android.graphics.Color.parseColor(this))
    } catch (e: Exception) {
        Color.Gray
    }
}

@Composable
fun EnveloppeItem(enveloppe: EnveloppeUi) {
    val formatteurMonetaire = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = enveloppe.nom,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
            Text(
                text = "Disponible : ${formatteurMonetaire.format(enveloppe.solde)}",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (enveloppe.depense > 0) {
                Text(
                    text = formatteurMonetaire.format(enveloppe.depense),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Yellow
                )
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(enveloppe.couleurProvenance?.toColor() ?: Color.DarkGray)
            )
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when (enveloppe.statutObjectif) {
                            StatutObjectif.GRIS -> Color.Gray
                            StatutObjectif.JAUNE -> Color(0xFFFFC107)
                            StatutObjectif.VERT -> Color(0xFF4CAF50)
                        }
                    )
            )
        }
    }
}
