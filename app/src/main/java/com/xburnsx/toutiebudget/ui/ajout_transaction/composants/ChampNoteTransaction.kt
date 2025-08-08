// chemin/simule: /ui/ajout_transaction/composants/ChampNoteTransaction.kt
// Dépendances: Jetpack Compose, Material3

package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composant pour saisir une note facultative pour la transaction.
 * Utilise un TextField Material3 avec un style adapté au thème sombre.
 */
@Composable
fun ChampNoteTransaction(
    note: String,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Note (facultatif)",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Ex: Épicerie Metro, Essence, Restaurant...",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            maxLines = 3,
            singleLine = false,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences
            )
        )
        
        // Compteur de caractères
        Text(
            text = "${note.length}/200",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 4.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun PreviewChampNoteTransaction() {
    MaterialTheme {
        ChampNoteTransaction(
            note = "Épicerie Metro - Courses de la semaine",
            onNoteChange = { }
        )
    }
}