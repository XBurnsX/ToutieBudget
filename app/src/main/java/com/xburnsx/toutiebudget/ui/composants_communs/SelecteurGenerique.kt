package com.xburnsx.toutiebudget.ui.composants_communs

import androidx.compose.foundation.layout.padding

// chemin/simule: /ui/composants_communs/SelecteurGenerique.kt
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SelecteurGenerique(
    label: String,
    icone: ImageVector,
    itemsGroupes: Map<String, List<T>>,
    itemSelectionne: T?,
    onItemSelected: (T) -> Unit,
    itemToString: (T) -> String,
    enabled: Boolean = true,
    customItemContent: @Composable (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = if (itemSelectionne != null) itemToString(itemSelectionne) else "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            label = { Text(label) },
            leadingIcon = { Icon(imageVector = icone, contentDescription = label) },
            trailingIcon = { if (enabled) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                disabledTextColor = Color.Gray,
                disabledLabelColor = Color.DarkGray,
                disabledLeadingIconColor = Color.DarkGray
            )
        )
        if (enabled) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                itemsGroupes.forEach { (categorie, items) ->
                    Text(
                        text = categorie.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    items.forEach { item ->
                        DropdownMenuItem(
                            text = { customItemContent(item) },
                            onClick = {
                                onItemSelected(item)
                                expanded = false
                            },
                            contentPadding = PaddingValues(0.dp)
                        )
                    }
                }
            }
        }
    }
}
