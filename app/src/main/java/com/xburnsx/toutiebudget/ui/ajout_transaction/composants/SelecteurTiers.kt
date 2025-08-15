// filepath: c:\Users\XBurnsX\Desktop\Project\Kotlin\ToutieBudget2\app\src\main\java\com\xburnsx\toutiebudget\ui\ajout_transaction\composants\SelecteurTiers.kt
// chemin/simule: /ui/ajout_transaction/composants/SelecteurTiers.kt
// Dépendances: Jetpack Compose, Material3, Tiers.kt

package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.xburnsx.toutiebudget.data.modeles.Tiers

/**
 * Composant pour sélectionner ou créer un tiers.
 * Affiche un TextField avec dropdown filtrable et possibilité de création.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelecteurTiers(
    tiersDisponibles: List<Tiers>,
    tiersUtiliser: String,
    onTiersUtiliserChange: (String) -> Unit,
    onTiersSelectionne: (Tiers) -> Unit,
    onCreerNouveauTiers: (String) -> Unit,
    isLoading: Boolean = false,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    var dropdownVisible by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

                    // Filtrer les tiers selon le texte saisi (insensible à la casse)
                val tiersFiltres = remember(tiersDisponibles, tiersUtiliser) {
                    if (tiersUtiliser.isBlank()) {
                        tiersDisponibles
                    } else {
                        tiersDisponibles.filter { tiers ->
                            tiers.nom.contains(tiersUtiliser, ignoreCase = true)
                        }
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

        Box {
            // TextField principal
            OutlinedTextField(
                value = tiersUtiliser,
                onValueChange = { newValue ->
                    onTiersUtiliserChange(newValue)
                    // Ouvrir le dropdown quand on commence à taper
                    if (newValue.isNotBlank() && isFocused) {
                        dropdownVisible = true
                    }
                },
                placeholder = {
                    Text(
                        text = "Rechercher ou ajouter un tiers...",
                        color = Color.White.copy(alpha = 0.6f)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        // Ouvrir le dropdown quand on clique dans le TextField
                        if (focusState.isFocused) {
                            dropdownVisible = true
                        } else {
                            // Fermer le dropdown quand on perd le focus
                            dropdownVisible = false
                        }
                    },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (tiersUtiliser.isBlank()) {
                        MaterialTheme.colorScheme.primary // Rouge quand vide
                    } else {
                        Color(0xFF404040) // Gris quand il y a du contenu
                    },
                    unfocusedBorderColor = if (tiersUtiliser.isBlank()) {
                        MaterialTheme.colorScheme.primary // Rouge quand vide
                    } else {
                        Color(0xFF404040) // Gris quand il y a du contenu
                    },
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = Color(0xFF1F1F1F),
                    unfocusedContainerColor = Color(0xFF1F1F1F)
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { 
                        // Juste passer au champ suivant, garder le clavier ouvert
                    }
                ),
                trailingIcon = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    }
                }
            )

            // Dropdown avec les résultats
            DropdownMenu(
                expanded = dropdownVisible && (tiersFiltres.isNotEmpty() || tiersUtiliser.isNotBlank()),
                onDismissRequest = {
                    dropdownVisible = false
                },
                properties = PopupProperties(focusable = false),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                // Option pour créer un nouveau tiers si le texte n'est pas vide
                if (tiersUtiliser.isNotBlank() && tiersFiltres.none { it.nom.equals(tiersUtiliser, ignoreCase = true) }) {
                    DropdownMenuItem(
                        onClick = {
                            onCreerNouveauTiers(tiersUtiliser)
                            dropdownVisible = false
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Ajouter : $tiersUtiliser",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        },
                        modifier = Modifier.background(Color(0xFF1F1F1F))
                    )

                    if (tiersFiltres.isNotEmpty()) {
                        HorizontalDivider(color = Color(0xFF404040))
                    }
                }

                // Liste des tiers filtrés
                tiersFiltres.forEach { tiers ->
                    DropdownMenuItem(
                        onClick = {
                            onTiersSelectionne(tiers)
                            onTiersUtiliserChange(tiers.nom)
                            dropdownVisible = false
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = tiers.nom,
                                    color = Color.White
                                )
                            }
                        },
                        modifier = Modifier.background(Color(0xFF1F1F1F))
                    )
                }

                // Message si aucun résultat
                if (tiersFiltres.isEmpty() && tiersUtiliser.isNotBlank()) {
                    DropdownMenuItem(
                        onClick = { },
                        enabled = false,
                        text = {
                            Text(
                                text = "Aucun tiers trouvé",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier.background(Color(0xFF1F1F1F))
                    )
                }
            }
        }


    }
}
