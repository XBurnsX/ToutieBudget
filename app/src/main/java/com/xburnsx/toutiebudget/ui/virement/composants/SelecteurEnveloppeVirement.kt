package com.xburnsx.toutiebudget.ui.virement.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import java.text.NumberFormat
import java.util.Locale

/**
 * Sélecteur d'enveloppe dédié à la page de virement.
 */
@Composable
fun SelecteurEnveloppeVirement(
    enveloppes: Map<String, List<EnveloppeUi>>,
    enveloppeSelectionnee: EnveloppeUi?,
    onEnveloppeChange: (EnveloppeUi) -> Unit,
    modifier: Modifier = Modifier,
    obligatoire: Boolean = true
) {
    var dialogOuvert by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (obligatoire) "Enveloppe à utiliser" else "Enveloppe (optionnel)",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { dialogOuvert = true },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1F1F1F)
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = if (enveloppeSelectionnee != null) 1.dp else 2.dp,
                color = if (enveloppeSelectionnee != null) {
                    Color(0xFF404040)
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (enveloppeSelectionnee != null) {
                    Column {
                        Text(
                            text = enveloppeSelectionnee.nom,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)
                                    .format(enveloppeSelectionnee.solde),
                                fontSize = 14.sp,
                                color = when {
                                    enveloppeSelectionnee.solde < 0 -> Color(0xFFEF4444)
                                    enveloppeSelectionnee.solde == 0.0 -> Color.Gray
                                    else -> Color(0xFF10B981)
                                },
                                fontWeight = FontWeight.Medium
                            )
                            if (enveloppeSelectionnee.objectif > 0) {
                                Text(
                                    text = "Objectif: " + NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).format(enveloppeSelectionnee.objectif),
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = if (obligatoire) "Sélectionner une enveloppe" else "Aucune enveloppe",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Ouvrir sélecteur",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
    }

    if (dialogOuvert) {
        DialogSelectionEnveloppeVirement(
            enveloppes = enveloppes,
            onEnveloppeSelectionnee = {
                onEnveloppeChange(it)
                dialogOuvert = false
            },
            onDismiss = { dialogOuvert = false }
        )
    }
}

@Composable
private fun DialogSelectionEnveloppeVirement(
    enveloppes: Map<String, List<EnveloppeUi>>,
    onEnveloppeSelectionnee: (EnveloppeUi) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Choisir une enveloppe",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LazyColumn {
                    enveloppes.forEach { (nomCategorie, enveloppesCategorie) ->
                        item {
                            Text(
                                text = nomCategorie,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(enveloppesCategorie) { enveloppe ->
                            ItemEnveloppeVirement(
                                enveloppe = enveloppe,
                                onClick = { onEnveloppeSelectionnee(enveloppe) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemEnveloppeVirement(
    enveloppe: EnveloppeUi,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = enveloppe.nom,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                if (enveloppe.objectif > 0) {
                    Text(
                        text = "Objectif: " + NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).format(enveloppe.objectif),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            Text(
                text = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).format(enveloppe.solde),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = when {
                    enveloppe.solde < 0 -> Color(0xFFEF4444)
                    enveloppe.solde == 0.0 -> Color.Gray
                    else -> Color(0xFF10B981)
                }
            )
        }
    }
} 