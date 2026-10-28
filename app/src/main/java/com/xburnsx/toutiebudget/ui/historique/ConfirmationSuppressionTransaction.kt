package com.xburnsx.toutiebudget.ui.historique

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * HC1/HE3 : confirmation explicite; une fraction n'est jamais une transaction
 * bancaire indépendante. La suppression reste entre les mains du UseCase.
 */
@Composable
fun ConfirmationSuppressionTransaction(
    transaction: TransactionUi?,
    onAnnuler: () -> Unit,
    onConfirmer: (TransactionUi) -> Unit
) {
    transaction ?: return

    val date = SimpleDateFormat("d MMMM yyyy 'à' HH:mm", Locale.CANADA_FRENCH)
        .format(transaction.date)
    val destination = transaction.nomEnveloppe
        ?: transaction.nomsEnveloppesFractions.takeIf { it.isNotEmpty() }?.joinToString()
        ?: "Aucune enveloppe renseignée"
    val compte = transaction.nomCompte?.takeIf { it.isNotBlank() }
        ?: transaction.compteId?.takeIf { it.isNotBlank() }
        ?: "Non renseigné"
    val montantTotal = transaction.montantTotalTransaction ?: transaction.montant
    val montant = if (transaction.montantTotalTransaction != null) {
        "Achat complet : " + MoneyFormatter.formatAmount(montantTotal) +
            "\nPortion visible ici : " + MoneyFormatter.formatAmount(transaction.montant)
    } else "Montant : " + MoneyFormatter.formatAmount(montantTotal)
    val operationLiee = transaction.type.name.startsWith("Transfert") ||
        transaction.type.name.startsWith("Paiement")
    val impact = when {
        transaction.estFractionnee ->
            "La suppression retire l'achat COMPLET et toutes ses fractions, " +
                "pas seulement la portion de cette enveloppe."
        operationLiee ->
            "La suppression annule l'opération et ses écritures liées dans les comptes concernés."
        else ->
            "La suppression annule les effets comptables de cette transaction."
    }
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text("Supprimer cette transaction?") },
        text = {
            Text(
                transaction.tiersUtiliser + "\n" + montant + " • " + date +
                    "\nCompte : " + compte +
                    "\nEnveloppe / contrepartie : " + destination +
                    "\n\n" + impact
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirmer(transaction) }) { Text("Supprimer") }
        },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } }
    )
}
