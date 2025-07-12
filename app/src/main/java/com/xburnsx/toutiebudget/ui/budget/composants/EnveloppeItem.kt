// chemin/simule: /ui/budget/composants/EnveloppeItem.kt
package com.xburnsx.toutiebudget.ui.budget.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
    val montant = enveloppe.solde
    val couleurBulle = if (montant > 0 && enveloppe.couleurProvenance != null) enveloppe.couleurProvenance.toColor() else Color(0xFF444444)
    val couleurTexteBulle = if (montant > 0) Color.White else Color.LightGray
    val arrondi = 18.dp

    // Couleur de la ligne verticale à droite
    val couleurLigne = when {
        enveloppe.objectif > 0 && montant >= enveloppe.objectif -> Color(0xFF4CAF50) // Objectif atteint (vert)
        enveloppe.objectif > 0 && montant > 0 -> Color(0xFFFFC107) // En cours (jaune)
        montant > 0 -> Color(0xFF4CAF50) // Pas d'objectif mais argent placé (vert)
        else -> Color.Gray // Rien placé
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF232323)
            ),
            shape = RoundedCornerShape(arrondi),
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max), // <-- Correction ici
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = enveloppe.nom,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(couleurBulle)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = formatteurMonetaire.format(montant),
                                color = couleurTexteBulle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                    if (enveloppe.objectif > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val progression = (enveloppe.solde / enveloppe.objectif).coerceIn(0.0, 1.0)
                        val estDepenseComplete = enveloppe.depense == enveloppe.objectif && enveloppe.objectif > 0
                        val couleurCompte = enveloppe.couleurProvenance?.toColor() ?: Color(0xFF4CAF50)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatteurMonetaire.format(enveloppe.objectif) + " pour le 31", // TODO: date réelle si dispo
                                color = Color.LightGray,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (estDepenseComplete) {
                                Text(
                                    text = "Dépensé ✓",
                                    color = couleurCompte,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            } else {
                                Text(
                                    text = "${(progression * 100).toInt()} %",
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF333333))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(1f)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (estDepenseComplete) couleurCompte else Color(0xFF4CAF50))
                            )
                        }
                    }
                }
                // Ligne verticale à droite
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(8.dp)
                        .clip(RoundedCornerShape(topEnd = arrondi, bottomEnd = arrondi))
                        .background(couleurLigne)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ApercuEnveloppeItem() {
    Column(
        modifier = Modifier.background(Color(0xFF121212)).padding(8.dp)
    ) {
        // Avec objectif atteint, bulle colorée
        EnveloppeItem(
            enveloppe = com.xburnsx.toutiebudget.ui.budget.EnveloppeUi(
                id = "1",
                nom = "🏠 Loyer",
                solde = 300.0,
                depense = 0.0,
                objectif = 300.0,
                couleurProvenance = "#4CAF50",
                statutObjectif = StatutObjectif.VERT
            )
        )
        // Sans objectif, bulle grise
        EnveloppeItem(
            enveloppe = com.xburnsx.toutiebudget.ui.budget.EnveloppeUi(
                id = "2",
                nom = "🏡 Assurance maison",
                solde = 0.0,
                depense = 0.0,
                objectif = 0.0,
                couleurProvenance = null,
                statutObjectif = StatutObjectif.GRIS
            )
        )
        // Avec objectif partiel, bulle colorée
        EnveloppeItem(
            enveloppe = com.xburnsx.toutiebudget.ui.budget.EnveloppeUi(
                id = "3",
                nom = "🚗 Auto",
                solde = 50.0,
                depense = 10.0,
                objectif = 200.0,
                couleurProvenance = "#2196F3",
                statutObjectif = StatutObjectif.JAUNE
            )
        )
        // Avec objectif partiel, bulle colorée
        EnveloppeItem(
            enveloppe = com.xburnsx.toutiebudget.ui.budget.EnveloppeUi(
                id = "4",
                nom = "Affirm",
                solde = 0.0,
                depense = 50.0,
                objectif = 50.0,
                couleurProvenance = "#2196F3",
                statutObjectif = StatutObjectif.JAUNE
            )
        )
    }
}
