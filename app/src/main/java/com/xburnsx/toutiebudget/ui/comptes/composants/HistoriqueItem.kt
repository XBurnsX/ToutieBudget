package com.xburnsx.toutiebudget.ui.comptes.composants

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.StickyNote2
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
import com.google.gson.JsonParser
import java.util.Date

@SuppressLint("ConfigurationScreenWidthHeight")
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
        TypeTransaction.RemboursementRecu -> Color.Green  // REMBOURSEMENT REÇU = VERT (argent qui rentre)
        TypeTransaction.RemboursementDonne -> Color.Red   // REMBOURSEMENT DONNÉ = ROUGE (argent qui sort)
        TypeTransaction.Paiement -> Color.Red    // PAIEMENT = ROUGE (argent qui sort)
        TypeTransaction.PaiementEffectue -> Color.Red    // PAIEMENT EFFECTUE = ROUGE (argent qui sort)
        else -> Color.Yellow
    }

    // ✅ Formater la date et l'heure complète
    val titreComplet =
        transaction.tiers //  Avec Date et heure  val titreComplet = "${transaction.tiers} - ${formateurDate.format(transaction.date)}"

    // Parser les fractions si c'est une transaction fractionnée
    val fractions = remember(transaction.sousItems) {
        if (transaction.estFractionnee && !transaction.sousItems.isNullOrBlank()) {
            try {
                val jsonArray = JsonParser.parseString(transaction.sousItems).asJsonArray
                jsonArray.mapNotNull { element ->
                    val obj = element.asJsonObject
                    val description = obj.get("description")?.asString ?: ""
                    val montant = obj.get("montant")?.asDouble ?: 0.0
                    val enveloppeId = obj.get("enveloppeId")?.asString ?: ""
                    Triple(description, montant, enveloppeId)
                }
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    // Récupérer les noms des enveloppes pour les fractions
    val fractionsAvecNoms = remember(fractions, transaction.nomsEnveloppesFractions) {
        if (transaction.nomsEnveloppesFractions.isNotEmpty()) {
            // Utiliser les vrais noms des enveloppes passés depuis le ViewModel
            fractions.zip(transaction.nomsEnveloppesFractions) { (_, montant, _), nomEnveloppe ->
                Pair(nomEnveloppe, montant)
            }
        } else {
            // Fallback : utiliser la description ou l'enveloppeId
            fractions.map { (description, montant, enveloppeId) ->
                val nomEnveloppe = if (description.contains("Fraction")) {
                    "Enveloppe $enveloppeId"
                } else {
                    description
                }
                Pair(nomEnveloppe, montant)
            }
        }
    }

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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .height(IntrinsicSize.Min)
            ) {
                // Barre d'accent verticale selon le type
                val accentColor = when (transaction.type) {
                    TypeTransaction.Depense, TypeTransaction.Pret -> MaterialTheme.colorScheme.error
                    TypeTransaction.Revenu, TypeTransaction.Emprunt -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(IntrinsicSize.Max)
                        .background(accentColor, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Ligne 1: 👤 TIERS avec icône (sans montant)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Tiers",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = titreComplet,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                // 📝 NOTE sur ligne séparée avec icône
                if (!transaction.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                            contentDescription = "Note",
                                tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = transaction.note,
                            fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // 🏷️ ENVELOPPE sur ligne séparée avec icône (pour transactions normales)
                if (transaction.nomEnveloppe != null && !transaction.estFractionnee) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalOffer,
                            contentDescription = "Enveloppe",
                                tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = transaction.nomEnveloppe,
                            fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
                
                // 🎯 FRACTIONS pour transactions fractionnées
                if (transaction.estFractionnee && fractions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column {
                        fractionsAvecNoms.forEach { (nomEnveloppe, montant) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 1.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalOffer,
                                    contentDescription = "Enveloppe",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$nomEnveloppe - ${MoneyFormatter.formatAmount(montant)}",
                                    fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Montant, centré verticalement et aligné à droite
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .wrapContentWidth(Alignment.End),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = MoneyFormatter.formatAmount(transaction.montant),
                        fontSize = 16.sp,
                        color = couleurMontant,
                        fontWeight = FontWeight.Bold
                    )
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
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Modifier", color = MaterialTheme.colorScheme.onSurface)
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
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Supprimer", color = MaterialTheme.colorScheme.onSurface)
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
            note = "Courses hebdomadaires",
            estFractionnee = false,
            sousItems = null,
            nomsEnveloppesFractions = emptyList()
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
            note = "Paie mensuelle",
            estFractionnee = false,
            sousItems = null,
            nomsEnveloppesFractions = emptyList()
        )
    )
}
