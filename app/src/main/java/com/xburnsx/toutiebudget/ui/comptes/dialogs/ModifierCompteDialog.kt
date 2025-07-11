// chemin/simule: /ui/comptes/dialogs/ModifierCompteDialog.kt
package com.xburnsx.toutiebudget.ui.comptes.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.ui.comptes.CompteFormState
import com.xburnsx.toutiebudget.ui.comptes.composants.CouleurSelecteur

@Composable
fun ModifierCompteDialog(
    formState: CompteFormState,
    onDismissRequest: () -> Unit,
    onValueChange: (String?, String?, String?, String?) -> Unit,
    onSave: () -> Unit
) {
    val couleursDisponibles = listOf("#F44336", "#E91E63", "#9C27B0", "#2196F3", "#4CAF50", "#FFC107")

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Modifier le Compte") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = formState.nom,
                    onValueChange = { onValueChange(it, null, null, null) },
                    label = { Text("Nom du compte") }
                )
                OutlinedTextField(
                    value = formState.solde,
                    onValueChange = { onValueChange(null, null, it, null) },
                    label = { Text("Solde") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                CouleurSelecteur(
                    couleurs = couleursDisponibles,
                    couleurSelectionnee = formState.couleur,
                    onCouleurSelected = { onValueChange(null, null, null, it) }
                )
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("Sauvegarder") } },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text("Annuler") } }
    )
}
