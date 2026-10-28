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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import com.xburnsx.toutiebudget.ui.historique.HistoriqueDetailDialog
import com.xburnsx.toutiebudget.ui.historique.PortionsHistorique
import com.xburnsx.toutiebudget.ui.historique.SensMontantHistorique
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onSupprimer: (TransactionUi) -> Unit = {},
    /** AA4 : remplir Ajouter transaction avec cette opération, sans l'enregistrer (null = option absente). */
    onReprendre: ((TransactionUi) -> Unit)? = null
) {
    var showMenu by remember(transaction.id) { mutableStateOf(false) }
    var showDetails by remember(transaction.id) { mutableStateOf(false) }

    val sens = SensMontantHistorique.determiner(transaction.type, transaction.collectionCompte)
    val couleurMontant = when (sens) {
        SensMontantHistorique.Sens.SORTIE -> MaterialTheme.colorScheme.error
        SensMontantHistorique.Sens.ENTREE -> Color(0xFF38A169)
        SensMontantHistorique.Sens.INCONNU -> MaterialTheme.colorScheme.onSurface
    }

    // ✅ Formater la date et l'heure complète avec texte spécial pour les virements
    val titreComplet = when (transaction.type) {
        TypeTransaction.TransfertSortant -> "Envoyé vers : ${transaction.tiersUtiliser}"
        TypeTransaction.TransfertEntrant -> "Reçu de : ${transaction.tiersUtiliser}"
        else -> transaction.tiersUtiliser
    }

    // Les montants de fractions sont lus par le même validateur strict que
    // les ViewModels. Jamais "montant absent = 0 $" pour un vrai achat.
    val fractions = remember(transaction.sousItems, transaction.estFractionnee) {
        if (transaction.estFractionnee) {
            runCatching {
                val portions = PortionsHistorique.lire(transaction.sousItems)
                val json = JsonParser.parseString(transaction.sousItems).asJsonArray
                portions.mapIndexed { index, portion ->
                    val description = json[index].asJsonObject.get("description")
                        ?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    Triple(description, portion.montantCentimes / 100.0, portion.enveloppeId)
                }
            }.getOrDefault(emptyList())
        } else emptyList()
    }
    val fractionsIllisibles = transaction.estFractionnee && fractions.isEmpty()

    // AT10 : les notes de chaque fraction restent visibles dans l'historique,
    // même après un chargement Room/PocketBase ou une modification.
    val notesFractions = remember(transaction.sousItems) {
        if (!transaction.estFractionnee || transaction.sousItems.isNullOrBlank()) emptyList()
        else runCatching {
            JsonParser.parseString(transaction.sousItems).asJsonArray.map { element ->
                element.asJsonObject.get("note")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            }
        }.getOrDefault(emptyList())
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
                .pointerInput(transaction.id) {
                    detectTapGestures(
                        onLongPress = {
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
                val accentColor = when (sens) {
                    SensMontantHistorique.Sens.SORTIE -> MaterialTheme.colorScheme.error
                    SensMontantHistorique.Sens.ENTREE -> MaterialTheme.colorScheme.tertiary
                    SensMontantHistorique.Sens.INCONNU -> MaterialTheme.colorScheme.primary
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
                
                // Ne jamais masquer une ventilation invalide ni afficher des montants fictifs.
                if (fractionsIllisibles) {
                    Text(
                        "Répartition indisponible — données invalides",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
                // 🎯 FRACTIONS pour transactions fractionnées
                if (transaction.estFractionnee && fractions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column {
                        fractionsAvecNoms.forEachIndexed { index, (nomEnveloppe, montant) ->
                            val noteFraction = notesFractions.getOrNull(index)?.trim().orEmpty()
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
                                    text = "$nomEnveloppe - ${MoneyFormatter.formatAmount(montant)}" +
                                        if (noteFraction.isNotEmpty()) " · $noteFraction" else "",
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
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = MoneyFormatter.formatAmount(transaction.montant),
                            fontSize = 16.sp,
                            color = couleurMontant,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            IconButton(
                                modifier = Modifier.size(38.dp),
                                onClick = { showDetails = true }
                            ) {
                                Icon(Icons.Default.Info, contentDescription = "Voir les détails")
                            }
                            IconButton(
                                modifier = Modifier.size(38.dp),
                                onClick = { showMenu = true }
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Actions sur la transaction")
                            }
                        }
                    }
                }
            }
        }

    // Le menu est ancré à la carte et reste dans les limites de l'écran,
    // même après rotation; pas de coordonnées écran calculées à la main.
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
        modifier = Modifier.width(210.dp)
    ) {
        DropdownMenuItem(
            text = { Text("Consulter les détails") },
            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
            onClick = { showMenu = false; showDetails = true }
        )
        DropdownMenuItem(
            text = { Text("Modifier") },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
            onClick = { showMenu = false; onModifier(transaction) }
        )
        if (onReprendre != null) {
            DropdownMenuItem(
                text = { Text("Reprendre") },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                onClick = { showMenu = false; onReprendre(transaction) }
            )
        }
        DropdownMenuItem(
            text = { Text("Supprimer") },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
            onClick = { showMenu = false; onSupprimer(transaction) }
        )
    }
    } // fin Box
    if (showDetails) HistoriqueDetailDialog(transaction, onFermer = { showDetails = false })
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
            tiersUtiliser = "Arbec",
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
            tiersUtiliser = "Entreprise ABC",
            nomEnveloppe = "Salaire",
            note = "Paie mensuelle",
            estFractionnee = false,
            sousItems = null,
            nomsEnveloppesFractions = emptyList()
        )
    )
}
