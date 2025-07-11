// chemin/simule: /ui/categories/dialogs/AjoutEnveloppeDialog.kt
// Dépendances: Jetpack Compose

package com.xburnsx.toutiebudget.ui.categories.dialogs

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

/**
 * Dialogue simple pour créer une nouvelle enveloppe dans une catégorie.
 *
 * @param nomEnveloppe La valeur actuelle du champ de nom.
 * @param onNomChange Callback appelé quand le nom change.
 * @param onDismissRequest Appelé pour fermer le dialogue.
 * @param onSave Appelé pour sauvegarder la nouvelle enveloppe.
 */
@Composable
fun AjoutEnveloppeDialog(
    nomEnveloppe: String,
    onNomChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Nouvelle Enveloppe") },
        text = {
            OutlinedTextField(
                value = nomEnveloppe,
                onValueChange = onNomChange,
                label = { Text("Nom de l'enveloppe") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = onSave, enabled = nomEnveloppe.isNotBlank()) {
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