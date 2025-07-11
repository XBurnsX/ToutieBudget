// chemin/simule: /ui/categories/composants/SelecteurJourMois.kt
package com.xburnsx.toutiebudget.ui.categories.composants

import androidx.compose.material3.*
import androidx.compose.runtime.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelecteurJourMois(jourSelectionne: Int?, onJourSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val jours = (1..31).toList()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = jourSelectionne?.toString() ?: "Jour",
            onValueChange = {},
            readOnly = true,
            modifier = androidx.compose.ui.Modifier.menuAnchor(),
            label = { Text("Jour du mois") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            jours.forEach { jour ->
                DropdownMenuItem(
                    text = { Text(jour.toString()) },
                    onClick = {
                        onJourSelected(jour)
                        expanded = false
                    }
                )
            }
        }
    }
}
