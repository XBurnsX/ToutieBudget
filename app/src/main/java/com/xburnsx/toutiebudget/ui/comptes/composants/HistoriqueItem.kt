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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun HistoriqueItem(
    transaction: TransactionUi,
    onLongPress: (TransactionUi) -> Unit = {},
    onModifier: (TransactionUi) -> Unit = {},
    onSupprimer: (TransactionUi) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var tapPosition by remember { mutableStateOf(Offset.Zero) }
    var cardPosition by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val couleurMontant = when (transaction.type) {
        TypeTransaction.Depense -> Color.Red
        TypeTransaction.Pret -> Color.Red        // PRET = ROUGE (argent qui sort)
        TypeTransaction.Revenu -> Color.Green
        TypeTransaction.Emprunt -> Color.Green   // EMPRUNT = VERT (argent qui entre)
        else -> Color.Yellow
    }

    // ✅ Formater la date et l'heure complète
    val formateurDate = remember { 
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault() // Utiliser le fuseau horaire local
        }
    }
    val titreComplet = "${transaction.tiers} - ${formateurDate.format(transaction.date)}"

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .onGloballyPositioned { coordinates ->
                    cardPosition = Offset(
                        coordinates.positionInWindow().x,
                        coordinates.positionInWindow().y
                    )
                }
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
                // Ligne principale avec tiers+date et montant
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 👤 TIERS + DATE avec icône
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
                            text = titreComplet,
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
                val screenWidth = configuration.screenWidthDp.dp.toPx()
                val screenHeight = configuration.screenHeightDp.dp.toPx()
                val menuWidth = 160.dp.toPx()
                val menuHeight = 120.dp.toPx()

                // Position exacte du tap avec décalage de -250dp vers le haut
                val exactX = cardPosition.x + tapPosition.x
                val exactY = cardPosition.y + tapPosition.y - 170.dp.toPx()

                // Ajuster uniquement si le menu sort de l'écran
                val adjustedX = when {
                    exactX + menuWidth > screenWidth ->
                        (exactX - menuWidth).coerceAtLeast(0f)
                    else -> exactX
                }

                val adjustedY = when {
                    exactY + menuHeight > screenHeight ->
                        (exactY - menuHeight).coerceAtLeast(0f)
                    exactY < 0f -> 0f
                    else -> exactY
                }

                IntOffset(
                    adjustedX.toInt(),
                    adjustedY.toInt()
                )
            }
        ) {
            Card(
                modifier = Modifier
                    .width(160.dp)
                    .background(Color(0xFF2C2C2E))
                    .padding(4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
            tiers = "Arbec",
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
