package com.xburnsx.toutiebudget.ui.comptes.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import com.xburnsx.toutiebudget.data.modeles.TypeTransaction
import com.xburnsx.toutiebudget.ui.historique.TransactionUi
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoriqueItem(
    transaction: TransactionUi,
    onLongPress: (TransactionUi) -> Unit = {},
    onModifier: (TransactionUi) -> Unit = {},
    onSupprimer: (TransactionUi) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var tapPosition by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    val couleurMontant = when (transaction.type) {
        TypeTransaction.Depense -> Color.Red
        TypeTransaction.Pret -> Color.Red        // PRET = ROUGE (argent qui sort)
        TypeTransaction.Revenu -> Color.Green
        TypeTransaction.Emprunt -> Color.Green   // EMPRUNT = VERT (argent qui entre)
        else -> Color.Yellow
    }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { offset -> 
                            tapPosition = offset
                            showMenu = true
                            onLongPress(transaction)
                        }
                    )
                },
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
                    // 👤 TIERS avec icône
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Tiers",
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = transaction.tiers,
                            fontSize = 16.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Montant
                    Text(
                        text = MoneyFormatter.formatAmount(transaction.montant),
                        fontSize = 16.sp,
                        color = couleurMontant,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 📝 NOTE sur ligne séparée avec icône
                if (!transaction.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.StickyNote2,
                            contentDescription = "Note",
                            tint = Color(0xFFFB7185),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = transaction.note,
                            fontSize = 14.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // 🏷️ ENVELOPPE sur ligne séparée avec icône
                if (transaction.nomEnveloppe != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalOffer,
                            contentDescription = "Enveloppe",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = transaction.nomEnveloppe,
                            fontSize = 14.sp,
                            color = Color.LightGray,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
    
    // Menu contextuel en dehors du Box
    if (showMenu) {
        Popup(
            onDismissRequest = { showMenu = false },
            properties = PopupProperties(focusable = true),
            offset = with(density) {
                IntOffset(
                    tapPosition.x.toDp().roundToPx(),
                    tapPosition.y.toDp().roundToPx()
                )
            }
        ) {
            Card(
                modifier = Modifier
                    .background(Color(0xFF2C2C2E))
                    .padding(4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .clickable { 
                                showMenu = false
                                onModifier(transaction)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Modifier", color = Color.White)
                    }
                    
                    Row(
                        modifier = Modifier
                            .clickable { 
                                showMenu = false
                                onSupprimer(transaction)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Supprimer", color = Color.White)
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
