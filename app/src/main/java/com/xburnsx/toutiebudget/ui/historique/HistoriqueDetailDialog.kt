package com.xburnsx.toutiebudget.ui.historique

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParser
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.text.DateFormat
import java.util.Locale

/**
 * HC19/HE8 : consultation intégrale, sans formulaire de modification.
 * Le contenu défile pour les longs libellés, notes et fractions.
 */
@Composable
fun HistoriqueDetailDialog(transaction: TransactionUi, onFermer: () -> Unit) {
    val dateTexte = DateFormat.getDateTimeInstance(
        DateFormat.FULL, DateFormat.MEDIUM, Locale.CANADA_FRENCH
    ).format(transaction.date)
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Détail de la transaction") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Type : " + transaction.type.libelle)
                Text("Tiers : " + transaction.tiersUtiliser)
                Text("Montant : " + MoneyFormatter.formatAmount(transaction.montant))
                Text("Date et heure : " + dateTexte)
                Text(
                    "Compte : " + (transaction.nomCompte?.takeIf { it.isNotBlank() }
                        ?: transaction.compteId?.takeIf { it.isNotBlank() }
                        ?: "Compte non disponible")
                )
                if (!transaction.note.isNullOrBlank()) {
                    Text("Note complète : " + transaction.note)
                }
                if (!transaction.nomEnveloppe.isNullOrBlank() && !transaction.estFractionnee) {
                    Text("Enveloppe : " + transaction.nomEnveloppe)
                }
                if (transaction.estFractionnee) {
                    Text("Répartition", style = MaterialTheme.typography.titleSmall)
                    val fractions = runCatching {
                        PortionsHistorique.lire(transaction.sousItems)
                    }.getOrNull()
                    if (fractions == null) {
                        Text(
                            "Fractions indisponibles ou invalides. Aucun montant inventé.",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        val objets = runCatching {
                            JsonParser.parseString(transaction.sousItems).asJsonArray
                        }.getOrNull()
                        fractions.forEachIndexed { index, portion ->
                            val nom = transaction.nomsEnveloppesFractions.getOrNull(index)
                                ?: portion.enveloppeId
                            val objet = objets?.get(index)?.takeIf { it.isJsonObject }?.asJsonObject
                            val description = objet?.get("description")
                                ?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                            val note = objet?.get("note")
                                ?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                            Text(nom + " : " +
                                MoneyFormatter.formatAmount(portion.montantCentimes / 100.0))
                            if (description.isNotBlank()) Text("Description : " + description)
                            if (note.isNotBlank()) Text("Note : " + note)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onFermer) { Text("Fermer") }
        }
    )
}
