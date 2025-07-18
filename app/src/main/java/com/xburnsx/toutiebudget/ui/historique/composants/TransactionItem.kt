// chemin/simule: /ui/historique/composants/TransactionItem.kt
package com.xburnsx.toutiebudget.ui.historique.composants

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.ui.historique.TransactionUi
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionItem(transaction: TransactionUi) {
    val formatteurMonetaire = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)

    val (icone, couleur) = when (transaction.type) {
        TypeTransaction.Depense -> Icons.Default.ArrowDownward to Color.Red
        TypeTransaction.Revenu -> Icons.Default.ArrowUpward to Color.Green
        else -> Icons.Default.ArrowDownward to Color.Yellow
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icone,
                contentDescription = transaction.type.name,
                tint = couleur,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = transaction.tiers,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = formatteurMonetaire.format(transaction.montant),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = couleur
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (transaction.nomEnveloppe != null) {
                        Text(
                            text = transaction.nomEnveloppe,
                            fontSize = 14.sp,
                            color = Color.LightGray,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (transaction.note != null) {
                            Text(" • ", color = Color.Gray)
                        }
                    }
                    if (transaction.note != null) {
                        Text(
                            text = transaction.note,
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
private fun TransactionItemPreview() {
    TransactionItem(
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