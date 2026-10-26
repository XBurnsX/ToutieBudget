package com.xburnsx.toutiebudget.ui.historique

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ConfirmationSuppressionTransaction(
    transaction: TransactionUi?,
    onAnnuler: () -> Unit,
    onConfirmer: (TransactionUi) -> Unit
) {
    transaction ?: return
    val date = SimpleDateFormat("d MMMM yyyy 'à' HH:mm", Locale.FRENCH).format(transaction.date)
    val destination = transaction.nomEnveloppe
        ?: transaction.nomsEnveloppesFractions.takeIf { it.isNotEmpty() }?.joinToString()
        ?: "aucune enveloppe"
    val operationLiee = transaction.type.name.startsWith("Transfert") || transaction.type.name.startsWith("Paiement")
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text("Supprimer cette transaction?") },
        text = {
            Text(
                "${transaction.tiersUtiliser}\n${MoneyFormatter.formatAmount(transaction.montant)} • $date\n" +
                    "Enveloppe/contrepartie : $destination\n" +
                    if (operationLiee) "Les deux écritures liées et leurs soldes seront annulés ensemble." else
                        "Le solde du compte, le Prêt à placer et l'enveloppe seront remis à leur état précédent."
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirmer(transaction) }) { Text("Supprimer") }
        },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } }
    )
}
