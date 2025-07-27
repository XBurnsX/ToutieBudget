package com.xburnsx.toutiebudget.ui.comptes.composants

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.ui.historique.TransactionUi
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoriqueItem(transaction: TransactionUi) {
    val formatteurMonetaire = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)

    val couleurMontant = when (transaction.type) {
        TypeTransaction.Depense -> Color.Red
        TypeTransaction.Pret -> Color.Red        // PRET = ROUGE (argent qui sort)
        TypeTransaction.Revenu -> Color.Green
        TypeTransaction.Emprunt -> Color.Green   // EMPRUNT = VERT (argent qui entre)
        else -> Color.Yellow
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Ligne principale avec tiers et montant
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tiers
                Text(
                    text = transaction.tiers,
                    fontSize = 16.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Montant
                Text(
                    text = formatteurMonetaire.format(transaction.montant),
                    fontSize = 16.sp,
                    color = couleurMontant,
                    fontWeight = FontWeight.Bold
                )
            }

            // Informations secondaires (enveloppe et note)
            if (transaction.nomEnveloppe != null || transaction.note != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (transaction.nomEnveloppe != null) {
                        Text(
                            text = transaction.nomEnveloppe,
                            fontSize = 14.sp,
                            color = Color.LightGray,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    if (transaction.note != null) {
                        Text(
                            text = "• ${transaction.note}",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun HistoriqueItemPreview() {
    // Preview pour une dépense
    HistoriqueItem(
        transaction = TransactionUi(
            id = "rec_1234567890",
            type = TypeTransaction.Depense,
            montant = 25.99,
            date = Date(),
            tiers = "Épicerie Metro",
            nomEnveloppe = "Alimentation",
            note = "Courses hebdomadaires"
        )
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun HistoriqueItemRevenuPreview() {
    // Preview pour un revenu
    HistoriqueItem(
        transaction = TransactionUi(
            id = "rec_0987654321",
            type = TypeTransaction.Revenu,
            montant = 2500.00,
            date = Date(),
            tiers = "Entreprise ABC",
            nomEnveloppe = "Salaire",
            note = "Paie mensuelle"
        )
    )
}
