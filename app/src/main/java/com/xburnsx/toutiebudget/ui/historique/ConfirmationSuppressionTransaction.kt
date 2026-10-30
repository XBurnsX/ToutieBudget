package com.xburnsx.toutiebudget.ui.historique

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

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
        "Achat complet : " + AffichageMontantHistorique.formater(montantTotal) +
            "\nPortion visible ici : " + AffichageMontantHistorique.formater(transaction.montant)
    } else "Montant : " + AffichageMontantHistorique.formater(montantTotal)
    // HE3 : les autres enveloppes touchées par le même achat, nommées une à une
    val autresEnveloppes = transaction.nomsEnveloppesFractions
        .filter { it.isNotBlank() && it != transaction.nomEnveloppe }
        .distinct()
    val autres = if (transaction.estFractionnee) {
        "\nAutres enveloppes affectées : " +
            (autresEnveloppes.takeIf { it.isNotEmpty() }?.joinToString() ?: "aucune autre")
    } else ""
    val operationLiee = transaction.type.name.startsWith("Transfert") ||
        transaction.type.name.startsWith("Paiement")
    val impact = when {
        transaction.estFractionnee ->
            "La suppression retire l'achat COMPLET et toutes ses fractions, " +
                "pas seulement la portion de cette enveloppe. Pour changer une seule fraction, " +
                "ouvre plutôt Modifier transaction."
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
                    "\nEnveloppe / contrepartie : " + destination + autres +
                    "\n\n" + impact
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirmer(transaction) }) { Text("Supprimer") }
        },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } }
    )
}
