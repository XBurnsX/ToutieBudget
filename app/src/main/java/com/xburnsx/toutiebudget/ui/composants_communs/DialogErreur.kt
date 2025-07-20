// chemin/simule: /ui/composants_communs/DialogErreur.kt
// Composant de dialogue d'erreur réutilisable pour toute l'application

package com.xburnsx.toutiebudget.ui.composants_communs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

/**
 * Dialogue d'erreur standard utilisé dans toute l'application.
 * Affiche les erreurs de manière cohérente avec le même design.
 */
@Composable
fun DialogErreur(
    messageErreur: String,
    titre: String = "Erreur",
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Erreur",
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = titre,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = messageErreur,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("OK")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.error,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
} 