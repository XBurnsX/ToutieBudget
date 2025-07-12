// chemin/simule: /ui/categories/dialogs/DefinirObjectifDialog.kt
package com.xburnsx.toutiebudget.ui.categories.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.ui.categories.ObjectifFormState
import com.xburnsx.toutiebudget.ui.categories.composants.SelecteurJourMois
import com.xburnsx.toutiebudget.ui.categories.composants.SelecteurJourSemaine
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DefinirObjectifDialog(
    nomEnveloppe: String,
    formState: ObjectifFormState,
    onValueChange: (TypeObjectif?, String?, Date?, Int?) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Définir un objectif") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Objectif pour : $nomEnveloppe", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoutonTypeObjectif("Mois", formState.type == TypeObjectif.Mensuel, { onValueChange(TypeObjectif.Mensuel, null, null, null) }, Modifier.weight(1f))
                    BoutonTypeObjectif("2 semaines", formState.type == TypeObjectif.Bihebdomadaire, { onValueChange(TypeObjectif.Bihebdomadaire, null, null, null) }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoutonTypeObjectif("Échéance", formState.type == TypeObjectif.Echeance, { onValueChange(TypeObjectif.Echeance, null, null, null) }, Modifier.weight(1f))
                    BoutonTypeObjectif("Année", formState.type == TypeObjectif.Annuel, { onValueChange(TypeObjectif.Annuel, null, null, null) }, Modifier.weight(1f))
                }
                when (formState.type) {
                    TypeObjectif.Echeance -> ChampDate(formState.date) { onValueChange(null, null, it, null) }
                    TypeObjectif.Mensuel -> SelecteurJourMois(formState.jour) { onValueChange(null, null, null, it) }
                    TypeObjectif.Bihebdomadaire -> SelecteurJourSemaine(formState.jour) { onValueChange(null, null, null, it) }
                    else -> {}
                }
                OutlinedTextField(
                    value = formState.montant,
                    onValueChange = { onValueChange(null, it, null, null) },
                    label = { Text("Montant de l'objectif (en $)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text("Annuler") } }
    )
}

@Composable
private fun ChampDate(date: Date?, onDateSelected: (Date) -> Unit) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.CANADA_FRENCH)
    OutlinedButton(onClick = { /* TODO: Ouvrir un DatePickerDialog */ }) {
        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (date != null) dateFormat.format(date) else "Choisir une date")
    }
}

@Composable
private fun BoutonTypeObjectif(texte: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray
        )
    ) { Text(texte) }
}
