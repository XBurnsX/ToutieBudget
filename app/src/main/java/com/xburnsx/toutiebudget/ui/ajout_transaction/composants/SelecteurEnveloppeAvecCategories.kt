// chemin/simule: /ui/ajout_transaction/composants/SelecteurEnveloppeAvecCategories.kt
// Dépendances: Jetpack Compose, Material3

package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.budget.composants.toColor
import java.text.NumberFormat
import java.util.Locale

/**
 * Item de dropdown qui peut être soit une catégorie (header) soit une enveloppe
 */
sealed class ItemDropdown {
    data class Categorie(val nom: String) : ItemDropdown()
    data class Enveloppe(val enveloppeUi: EnveloppeUi) : ItemDropdown()
}

/**
 * Sélecteur d'enveloppe avec catégories intégrées dans le même dropdown
 *
 * IMPORTANT: Les enveloppes doivent être passées dans l'ordre défini par
 * OrganisationEnveloppesUtils.organiserEnveloppesParCategorie() pour assurer
 * un ordre cohérent avec l'écran CategoriesEnveloppesScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelecteurEnveloppeAvecCategories(
    enveloppesFiltrees: Map<String, List<EnveloppeUi>>,
    enveloppeSelectionnee: EnveloppeUi?,
    onEnveloppeSelected: (EnveloppeUi) -> Unit,
    libelle: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    // Créer la liste plate avec catégories et enveloppes
    val itemsDropdown = remember(enveloppesFiltrees) {
        buildList {
            enveloppesFiltrees.forEach { (nomCategorie, enveloppes) ->
                if (enveloppes.isNotEmpty()) {
                    // Ajouter le header de catégorie
                    add(ItemDropdown.Categorie(nomCategorie))
                    // Ajouter toutes les enveloppes de cette catégorie
                    enveloppes.forEach { enveloppe ->
                        add(ItemDropdown.Enveloppe(enveloppe))
                    }
                }
            }
        }
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Étiquette
        Text(
            text = libelle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Menu déroulant
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = enveloppeSelectionnee?.nom ?: "Sélectionner une enveloppe...",
                onValueChange = {},
                readOnly = true,
                label = { Text(libelle) },
                leadingIcon = { Icon(Icons.Default.Savings, contentDescription = libelle) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedContainerColor = Color.White.copy(alpha = 0.05f)
                )
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                itemsDropdown.forEach { item ->
                    when (item) {
                        is ItemDropdown.Categorie -> {
                            // Header de catégorie (non cliquable)
                            HeaderCategorie(nomCategorie = item.nom)
                        }
                        is ItemDropdown.Enveloppe -> {
                            // Item d'enveloppe (cliquable)
                            DropdownMenuItem(
                                onClick = {
                                    onEnveloppeSelected(item.enveloppeUi)
                                    expanded = false
                                },
                                text = {
                                    ItemEnveloppe(enveloppeUi = item.enveloppeUi)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Header de catégorie dans le dropdown
 */
@Composable
private fun HeaderCategorie(nomCategorie: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = nomCategorie,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary, // Couleur du thème primaire
            fontSize = 14.sp
        )
    }
}

/**
 * Item d'enveloppe dans le dropdown
 */
@Composable
private fun ItemEnveloppe(enveloppeUi: EnveloppeUi) {
    val formateurMonetaire = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Nom de l'enveloppe (en blanc)
        Text(
            text = enveloppeUi.nom,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface, // Blanc sur thème sombre
            modifier = Modifier.weight(1f)
        )
        
        // Montant (couleur du compte source)
        Text(
            text = formateurMonetaire.format(enveloppeUi.solde),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = enveloppeUi.couleurProvenance?.toColor() ?: MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Pastille de couleur du compte (optionnelle)
        if (enveloppeUi.couleurProvenance != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(enveloppeUi.couleurProvenance.toColor())
            )
        }
    }
}