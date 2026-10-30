// chemin/simule: /ui/cartes_credit/dialogs/ModifierFraisDialog.kt
// Dépendances: Jetpack Compose, Material3, FormulaireCarteCredit

package com.xburnsx.toutiebudget.ui.cartes_credit.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.TypeFrais
import com.xburnsx.toutiebudget.ui.cartes_credit.FormulaireCarteCredit

/**
 * CC10 : frais mensuel, annuel ou ponctuel, avec sa vraie date d'application si connue.
 * Un frais déclaré ici sert aux projections ; il n'est jamais facturé automatiquement sur la carte.
 */
@Composable
fun ModifierFraisDialog(
    formulaire: FormulaireCarteCredit,
    onChange: (FormulaireCarteCredit.() -> FormulaireCarteCredit) -> Unit,
    onSauvegarder: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un frais") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Champ("Nom (ex. assurance, frais annuels)", formulaire.nomFrais, null, texte = true) { v -> onChange { copy(nomFrais = v) } }
                Champ("Montant ($)", formulaire.montantFrais, null, montant = true) { v -> onChange { copy(montantFrais = v) } }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(TypeFrais.MENSUEL to "Mensuel", TypeFrais.ANNUEL to "Annuel", TypeFrais.PONCTUEL to "Ponctuel").forEach { (type, libelle) ->
                        FilterChip(selected = formulaire.typeFrais == type, onClick = { onChange { copy(typeFrais = type) } }, label = { Text(libelle) })
                    }
                }
                Champ("Date d'application (AAAA-MM-JJ, optionnel)", formulaire.dateFrais, null, texte = true) { v ->
                    onChange { copy(dateFrais = v) }
                }
                Text(
                    "Un frais du même nom et du même type est remplacé, pas ajouté en double.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { TextButton(onClick = onSauvegarder) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
