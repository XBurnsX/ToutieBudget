// chemin/simule: /ui/ajout_transaction/composants/SelecteurTiers.kt
// Dépendances: Jetpack Compose, Material3, Tiers.kt

package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.xburnsx.toutiebudget.data.modeles.Tiers
import kotlinx.coroutines.delay

/**
 * Composant pour sélectionner ou créer un tiers avec autocomplete.
 * Permet de taper du texte et affiche les suggestions + option "Ajouter".
 */
@Composable
fun SelecteurTiers(
    tiersDisponibles: List<Tiers>,
    tiersSelectionne: Tiers?,
    onTiersChange: (Tiers?) -> Unit,
    onCreerTiers: (String) -> Unit,
    onRechercherTiers: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var texteRecherche by remember { mutableStateOf(tiersSelectionne?.nom ?: "") }
    var dropdownOuvert by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    
    // Filtrer les tiers selon le texte de recherche
    val tiersFiltres = remember(tiersDisponibles, texteRecherche) {
        if (texteRecherche.isBlank()) {
            tiersDisponibles
        } else {
            tiersDisponibles.filter { 
                it.nom.contains(texteRecherche, ignoreCase = true) 
            }
        }
    }
    
    // Déclencher la recherche quand le texte change
    LaunchedEffect(texteRecherche) {
        if (texteRecherche.isNotBlank()) {
            delay(300) // Délai pour éviter trop de requêtes
            onRechercherTiers(texteRecherche)
        }
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Tiers",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        // TextField avec dropdown intégré
        Box {
            OutlinedTextField(
                value = texteRecherche,
                onValueChange = { nouveauTexte ->
                    texteRecherche = nouveauTexte
                    dropdownOuvert = nouveauTexte.isNotBlank()
                    
                    // Si le texte ne correspond plus au tiers sélectionné, le désélectionner
                    if (tiersSelectionne != null && !tiersSelectionne.nom.equals(nouveauTexte, ignoreCase = true)) {
                        onTiersChange(null)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text("Nom du tiers") },
                leadingIcon = { 
                    Icon(
                        Icons.Default.Person, 
                        contentDescription = "Tiers",
                        tint = MaterialTheme.colorScheme.primary
                    ) 
                },
                trailingIcon = {
                    IconButton(onClick = { 
                        dropdownOuvert = !dropdownOuvert
                        if (dropdownOuvert && texteRecherche.isBlank()) {
                            onRechercherTiers("") // Charger tous les tiers
                        }
                    }) {
                        Icon(
                            imageVector = if (dropdownOuvert) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Ouvrir/Fermer dropdown",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                },
                placeholder = { Text("Ex: Katherine, Metro, Jean...") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color(0xFF404040),
                    focusedContainerColor = Color(0xFF1F1F1F),
                    unfocusedContainerColor = Color(0xFF1F1F1F),
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            
            // Dropdown avec suggestions
            if (dropdownOuvert) {
                DropdownMenu(
                    expanded = dropdownOuvert,
                    onDismissRequest = { dropdownOuvert = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Option "Ajouter" si le texte n'est pas vide et ne correspond à aucun tiers existant
                    if (texteRecherche.isNotBlank() && 
                        !tiersFiltres.any { it.nom.equals(texteRecherche, ignoreCase = true) }) {
                        DropdownMenuItem(
                            onClick = {
                                onCreerTiers(texteRecherche)
                                dropdownOuvert = false
                            },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Ajouter",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Ajouter : $texteRecherche",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        )
                        
                        if (tiersFiltres.isNotEmpty()) {
                            Divider()
                        }
                    }
                    
                    // Tiers existants filtrés
                    tiersFiltres.forEach { tiers ->
                        DropdownMenuItem(
                            onClick = {
                                texteRecherche = tiers.nom
                                onTiersChange(tiers)
                                dropdownOuvert = false
                            },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = "Tiers",
                                        tint = Color.White.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = tiers.nom,
                                        color = Color.White
                                    )
                                }
                            }
                        )
                    }
                    
                    // Message si aucun résultat
                    if (texteRecherche.isNotBlank() && tiersFiltres.isEmpty()) {
                        DropdownMenuItem(
                            onClick = { },
                            enabled = false,
                            text = {
                                Text(
                                    text = "Aucun tiers trouvé",
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}