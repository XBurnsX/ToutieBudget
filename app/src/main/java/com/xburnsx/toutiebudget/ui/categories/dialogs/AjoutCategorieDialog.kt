// chemin/simule: /ui/categories/dialogs/AjoutCategorieDialog.kt
package com.xburnsx.toutiebudget.ui.categories.dialogs

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
fun AjoutCategorieDialog(
    nomCategorie: String,
    onNomChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Nouvelle Catégorie") },
        text = {
            OutlinedTextField(
                value = nomCategorie,
                onValueChange = onNomChange,
                label = { Text("Nom de la catégorie") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words
                )
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave()
                },
                enabled = nomCategorie.isNotBlank()
            ) {
                Text("Créer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Annuler")
            }
        }
    )
}
