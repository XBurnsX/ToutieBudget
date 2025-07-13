// chemin/simule: /ui/ajout_transaction/composants/ChampTiers.kt
// Dépendances: Jetpack Compose, Material3

package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun ChampTiers(
    valeur: String,
    onValeurChange: (String) -> Unit,
    libelle: String = "Payé à / Reçu de",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = valeur,
        onValueChange = onValeurChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(libelle) },
        leadingIcon = { Icon(Icons.Default.Person, "Tiers") },
        placeholder = { 
            Text(
                when (libelle) {
                    "Prêté à" -> "Ex: Katherine"
                    "Remboursé par" -> "Ex: Katherine"
                    "Emprunté à" -> "Ex: Papa"
                    "Remboursé à" -> "Ex: Papa"
                    else -> "Nom de la personne"
                }
            ) 
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White.copy(alpha = 0.05f)
        )
    )
}