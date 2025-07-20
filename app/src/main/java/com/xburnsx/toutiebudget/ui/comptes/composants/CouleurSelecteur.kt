// chemin/simule: /ui/comptes/composants/CouleurSelecteur.kt
package com.xburnsx.toutiebudget.ui.comptes.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.ui.budget.composants.toColor

@Composable
fun CouleurSelecteur(
    couleurs: List<String>,
    couleurSelectionnee: String,
    onCouleurSelected: (String) -> Unit
) {
    // Organiser les couleurs en grille de 6 colonnes
    val couleursParsLigne = couleurs.chunked(6)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        couleursParsLigne.forEach { ligneCouleurs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ligneCouleurs.forEach { couleurHex ->
                    val isSelected = couleurHex == couleurSelectionnee
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(couleurHex.toColor())
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = Color.White,
                                shape = CircleShape
                            )
                            .clickable { onCouleurSelected(couleurHex) }
                    )
                }

                // Ajouter des espaces vides pour aligner les couleurs si la ligne n'est pas complète
                repeat(6 - ligneCouleurs.size) {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}
