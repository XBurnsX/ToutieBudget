// chemin/simule: /ui/ajout_transaction/composants/SelecteurEnveloppe.kt
// Dépendances: Jetpack Compose, Material3, EnveloppeUi.kt

package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.budget.StatutObjectif
import com.xburnsx.toutiebudget.ui.budget.composants.toColor
import java.text.NumberFormat
import java.util.Locale

/**
 * Composant pour sélectionner une enveloppe parmi la liste disponible.
 * Affiche l'enveloppe sélectionnée et permet d'ouvrir un dialog de sélection.
 */
@Composable
fun SelecteurEnveloppe(
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
            text = if (obligatoire) "Enveloppe à débiter" else "Enveloppe (optionnel)",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        // Carte cliquable pour ouvrir le sélecteur
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
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
                                    text = "Catégorie", // Note: nomCategorie n'existe plus dans EnveloppeUi
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                
                                Text(
                                    text = "•",
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                
                                Text(
                                    text = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)
                                        .format(enveloppeSelectionnee.solde),
                                    fontSize = 14.sp,
                                    color = if (enveloppeSelectionnee.solde >= 0) {
                                        Color(0xFF10B981)
                                    } else {
                                        Color(0xFFEF4444)
                                    },
                                    fontWeight = FontWeight.Medium
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
        
        // Message d'information pour les soldes négatifs
        if (enveloppeSelectionnee != null && enveloppeSelectionnee.solde < 0) {
            Text(
                text = "Cette enveloppe a un solde négatif. La dépense sera quand même enregistrée.",
                fontSize = 12.sp,
                color = Color(0xFFFBBF24),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
    
    // Dialog de sélection
    if (dialogOuvert) {
        DialogSelectionEnveloppe(
            enveloppes = enveloppes,
            onEnveloppeSelectionnee = { enveloppe ->
                onEnveloppeChange(enveloppe)
                dialogOuvert = false
            },
            onDismiss = { dialogOuvert = false }
        )
    }
}

/**
 * Dialog pour sélectionner une enveloppe dans la liste.
 */
@Composable
private fun DialogSelectionEnveloppe(
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
                            ItemEnveloppe(
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

/**
 * Item individuel d'enveloppe dans la liste.
 */
@Composable
private fun ItemEnveloppe(
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
                        text = "Objectif: ${NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).format(enveloppe.objectif)}",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            
            Text(
                text = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)
                    .format(enveloppe.solde),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (enveloppe.solde >= 0) {
                    Color(0xFF10B981)
                } else {
                    Color(0xFFEF4444)
                }
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun PreviewSelecteurEnveloppe() {
    val enveloppesTest = listOf(
        EnveloppeUi(
            id = "1",
            nom = "Épicerie",
            solde = 150.50,
            depense = 0.0,
            objectif = 300.0,
            couleurProvenance = "#10B981",
            statutObjectif = StatutObjectif.GRIS
        ),
        EnveloppeUi(
            id = "2",
            nom = "Restaurants",
            solde = -25.75,
            depense = 125.75,
            objectif = 100.0,
            couleurProvenance = "#EF4444",
            statutObjectif = StatutObjectif.GRIS
        )
    )
    
    SelecteurEnveloppe(
        enveloppes = mapOf("Alimentation" to enveloppesTest),
        enveloppeSelectionnee = enveloppesTest.first(),
        onEnveloppeChange = { }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun PreviewDialogSelectionEnveloppe() {
    val enveloppesTest = listOf(
        EnveloppeUi(
            id = "1",
            nom = "Épicerie",
            solde = 150.50,
            depense = 0.0,
            objectif = 300.0,
            couleurProvenance = "#10B981",
            statutObjectif = StatutObjectif.GRIS
        ),
        EnveloppeUi(
            id = "2",
            nom = "Restaurants",
            solde = -25.75,
            depense = 125.75,
            objectif = 100.0,
            couleurProvenance = "#EF4444",
            statutObjectif = StatutObjectif.GRIS
        ),
        EnveloppeUi(
            id = "3",
            nom = "Transport",
            solde = 75.25,
            depense = 24.75,
            objectif = 150.0,
            couleurProvenance = "#6366F1",
            statutObjectif = StatutObjectif.GRIS
        )
    )
    
    val enveloppesGroupes = mapOf(
        "Alimentation" to enveloppesTest.take(2),
        "Transport" to enveloppesTest.takeLast(1)
    )
    
    DialogSelectionEnveloppe(
        enveloppes = enveloppesGroupes,
        onEnveloppeSelectionnee = { },
        onDismiss = { }
    )
}