// chemin/simule: /ui/composants_communs/SelecteurGenerique.kt
// Dépendances: Jetpack Compose, Material3

package com.xburnsx.toutiebudget.ui.composants_communs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Composant générique pour la sélection d'options avec dropdown.
 * Peut être utilisé pour les comptes, enveloppes, etc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SelecteurGenerique(
    options: List<T>,
    optionSelectionnee: T?,
    onSelectionChange: (T) -> Unit,
    libelle: String,
    obtenirTextePourOption: (T) -> String,
    icone: ImageVector? = null,
    itemComposable: (@Composable (T) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Étiquette
        Text(
            text = libelle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Menu déroulant
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = optionSelectionnee?.let { obtenirTextePourOption(it) } ?: "Sélectionner...",
                onValueChange = {},
                readOnly = true,
                label = { Text(libelle) },
                leadingIcon = icone?.let { { Icon(it, contentDescription = libelle) } },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedContainerColor = Color.White.copy(alpha = 0.05f)
                )
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        onClick = {
                            onSelectionChange(option)
                            expanded = false
                        },
                        text = {
                            if (itemComposable != null) {
                                itemComposable(option)
                            } else {
                                Text(obtenirTextePourOption(option))
                            }
                        }
                    )
                }
            }
        }
    }
}