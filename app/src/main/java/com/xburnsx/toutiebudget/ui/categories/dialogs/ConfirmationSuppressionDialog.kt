// chemin/simule: /ui/categories/dialogs/ConfirmationSuppressionDialog.kt
package com.xburnsx.toutiebudget.ui.categories.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun ConfirmationSuppressionDialog(
    titre: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = titre) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text("Confirmer", color = Color.Red)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Annuler")
            }
        }
    )
}
