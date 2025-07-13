// chemin/simule: /ui/ajout_transaction/composants/ChampNoteTransaction.kt
// Dépendances: Jetpack Compose, Material3

package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Ex: Épicerie Metro, Essence, Restaurant...",
                    color = Color.White.copy(alpha = 0.5f)
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF6366F1),
                unfocusedBorderColor = Color(0xFF404040),
                focusedContainerColor = Color(0xFF1F1F1F),
                unfocusedContainerColor = Color(0xFF1F1F1F),
                cursorColor = Color(0xFF6366F1)
            ),
            maxLines = 3,
            singleLine = false
        )
        
        // Compteur de caractères
        Text(
            text = "${note.length}/200",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 4.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun PreviewChampNoteTransaction() {
    ChampNoteTransaction(
        note = "Épicerie Metro - Courses de la semaine",
        onNoteChange = { }
    )
}