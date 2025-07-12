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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.Enveloppe

@Composable
fun EnveloppeConfigItem(
    enveloppe: Enveloppe,
    onObjectifClick: () -> Unit,
    onSupprimerClick: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = enveloppe.nom,
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        
        TextButton(onClick = onObjectifClick) {
            Text(
                text = if (enveloppe.objectifMontant > 0) "${enveloppe.objectifMontant}$" else "Objectif",
                color = if (enveloppe.objectifMontant > 0) Color.Green else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Menu pour l'enveloppe
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Plus d'options",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Supprimer l'enveloppe") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        onSupprimerClick()
                        showMenu = false
                    }
                )
            }
        }
    }
}
