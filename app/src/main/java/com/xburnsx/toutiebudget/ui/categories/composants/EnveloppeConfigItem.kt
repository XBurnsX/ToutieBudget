// chemin/simule: /ui/categories/composants/EnveloppeConfigItem.kt
package com.xburnsx.toutiebudget.ui.categories.composants

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.Enveloppe

@Composable
fun EnveloppeConfigItem(
    enveloppe: Enveloppe,
    isModeEdition: Boolean = false,
    isDragMode: Boolean = false,
    isDragged: Boolean = false,
    onObjectifClick: () -> Unit,
    onSupprimerClick: () -> Unit = {},
    onSupprimerEnveloppe: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(
                if (isDragged) {
                    Modifier.alpha(0.7f) // Rendre semi-transparent pendant le drag
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = enveloppe.nom,
            color = when {
                isDragged -> Color.Yellow // Couleur spéciale pendant le drag
                isModeEdition -> Color.Red
                else -> Color.White
            },
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        
        if (!isModeEdition && !isDragMode) {
            // Mode normal : Bouton objectif
            TextButton(onClick = onObjectifClick) {
                Text(
                    text = if (enveloppe.objectifMontant > 0) "${enveloppe.objectifMontant}$" else "Objectif",
                    color = if (enveloppe.objectifMontant > 0) Color.Green else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        if (isModeEdition && !isDragMode) {
            // Mode édition : Afficher directement le bouton de suppression
            IconButton(
                onClick = onSupprimerClick
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Supprimer l'enveloppe",
                    tint = Color.Red
                )
            }
        } else if (!isModeEdition && !isDragMode) {
            // Mode normal : Menu avec options
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Plus d'options", tint = Color.Gray)
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Supprimer l'objectif") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            onSupprimerClick()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer l'enveloppe") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            onSupprimerEnveloppe()
                            showMenu = false
                        }
                    )
                }
            }
        }

        if (isDragMode && !isDragged) {
            // Pendant le mode drag, afficher un indicateur visuel pour les zones de drop
            Spacer(modifier = Modifier.width(48.dp))
        }
    }
}
