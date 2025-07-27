package com.xburnsx.toutiebudget.ui.virement.composants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.ui.virement.ItemVirement
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelecteurCompteVirement(
    label: String,
    groupesDeComptes: Map<String, List<ItemVirement>>,
    itemSelectionne: ItemVirement?,
    onItemSelected: (ItemVirement) -> Unit,
    onOuvrirSelecteur: () -> Unit,
    selecteurOuvert: Boolean
) {
    val nomAffiche = when (itemSelectionne) {
        is ItemVirement.CompteItem -> itemSelectionne.compte.nom
        is ItemVirement.EnveloppeItem -> itemSelectionne.enveloppe.nom
        else -> ""
    }

    ExposedDropdownMenuBox(
        expanded = selecteurOuvert,
        onExpandedChange = { onOuvrirSelecteur() },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = nomAffiche,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = selecteurOuvert) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = selecteurOuvert,
            onDismissRequest = { onOuvrirSelecteur() } // Ferme le menu
        ) {
            groupesDeComptes.forEach { (categorie, comptes) ->
                if (comptes.isNotEmpty()) {
                    // Afficher le nom de la catégorie comme un en-tête non cliquable
                    Text(
                        text = categorie,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    // Afficher les comptes de la catégorie
                    comptes.forEach { item ->
                        val compteItem = (item as? ItemVirement.CompteItem)?.compte
                        if (compteItem != null) {
                            val compteColor = try {
                                Color(android.graphics.Color.parseColor(compteItem.couleur))
                            } catch (e: IllegalArgumentException) {
                                MaterialTheme.colorScheme.onSurface
                            }
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(compteItem.nom, color = compteColor)
                                        Text(
                                            text = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).format(compteItem.solde),
                                            color = compteColor,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                },
                                onClick = {
                                    onItemSelected(item)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
