// chemin/simule: /ui/ajout_transaction/composants/ChampTiers.kt
// Dépendances: Jetpack Compose, Material3

package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composant pour saisir le tiers (personne/entité) de la transaction.
 * Utilise un TextField Material3 avec un style adapté au thème sombre.
 */
@Composable
fun ChampTiers(
    valeur: String,
    onValeurChange: (String) -> Unit,
    libelle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = libelle,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        OutlinedTextField(
            value = valeur,
            onValueChange = onValeurChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Ex: Jean Dupont, Metro, Banque...",
                    color = Color.White.copy(alpha = 0.5f)
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF6366F1),
                unfocusedBorderColor = Color(0xFF404040),
                focusedContainerColor = Color(0xFF1F1F1F),
                unfocusedContainerColor = Color(0xFF1F1F1F),
                cursorColor = Color(0xFF6366F1)
            ),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun PreviewChampTiers() {
    ChampTiers(
        valeur = "Metro",
        onValeurChange = { },
        libelle = "Payé à"
    )
}