// chemin/simule: /ui/categories/composants/SelecteurJourSemaine.kt
package com.xburnsx.toutiebudget.ui.categories.composants

import androidx.compose.material3.*
import androidx.compose.runtime.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelecteurJourSemaine(jourSelectionne: Int?, onJourSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val jours = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = if (jourSelectionne != null) jours[jourSelectionne - 1] else "Jour",
            onValueChange = {},
            readOnly = true,
            modifier = androidx.compose.ui.Modifier.menuAnchor(),
            label = { Text("Jour de la semaine") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            jours.forEachIndexed { index, jour ->
                DropdownMenuItem(
                    text = { Text(jour) },
                    onClick = {
                        onJourSelected(index + 1)
                        expanded = false
                    }
                )
            }
        }
    }
}
