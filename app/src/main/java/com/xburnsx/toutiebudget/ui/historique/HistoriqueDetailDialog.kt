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
                if (transaction.montantTotalTransaction != null) {
                    Text("Montant total de l'achat : " +
                        AffichageMontantHistorique.formater(transaction.montantTotalTransaction))
                    Text("Portion de cette enveloppe : " +
                        AffichageMontantHistorique.formater(transaction.montant))
                } else {
                    Text("Montant de la transaction : " +
                        AffichageMontantHistorique.formater(transaction.montant))
                }
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
                        val totalBancaire = transaction.montantTotalTransaction ?: transaction.montant
                        if (!PortionsHistorique.verifierTotal(fractions, totalBancaire)) {
                            Text(
                                "Attention : les fractions ne totalisent pas le montant bancaire de l'achat.",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        val metadonnees = MetadonneesFractionsHistorique.lire(
                            transaction.sousItems, fractions.size
                        )
                        fractions.forEachIndexed { index, portion ->
                            val nom = transaction.nomsEnveloppesFractions.getOrNull(index)
                                ?: "Enveloppe historique (ID : ${portion.enveloppeId})"
                            val description = metadonnees[index].description
                            val note = metadonnees[index].note
                            Text(nom + " : " +
                                AffichageMontantHistorique.formater(portion.montantCentimes / 100.0))
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
