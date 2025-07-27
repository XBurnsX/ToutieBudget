package com.xburnsx.toutiebudget.ui.virement.composants

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.xburnsx.toutiebudget.ui.virement.ItemVirement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelecteurCompteVirement(
    label: String,
    comptes: List<ItemVirement.CompteItem>,
    itemSelectionne: ItemVirement?,
    onItemSelected: (ItemVirement) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val nomAffiche = (itemSelectionne as? ItemVirement.CompteItem)?.compte?.nom ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = nomAffiche,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            comptes.forEach { compteItem ->
                // Ne pas afficher le compte déjà sélectionné dans l'autre sélecteur
                if (itemSelectionne != compteItem) {
                    DropdownMenuItem(
                        text = { Text(compteItem.compte.nom) },
                        onClick = {
                            onItemSelected(compteItem)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

