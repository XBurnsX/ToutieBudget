// chemin/simule: /ui/categories/composants/CategorieCard.kt
package com.xburnsx.toutiebudget.ui.categories.composants

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif

@Composable
fun CategorieCard(
    nomCategorie: String,
    enveloppes: List<Enveloppe>,
    isModeEdition: Boolean = false, // Nouveau paramètre pour le mode édition
    isDragging: Boolean = false, // Pour savoir si la carte est en cours de déplacement
    isDragMode: Boolean = false,
    onAjouterEnveloppeClick: () -> Unit,
    onObjectifClick: (Enveloppe) -> Unit,
    onSupprimerEnveloppe: (Enveloppe) -> Unit = {},
    onSupprimerObjectifEnveloppe: (Enveloppe) -> Unit = {}, // NOUVEAU : pour supprimer seulement l'objectif
    onSupprimerCategorie: (String) -> Unit = {},
    onStartDragEnveloppe: (String) -> Unit = {},
    draggedEnveloppeId: String? = null,
    modifier: Modifier = Modifier
) {
    var showCategorieMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = nomCategorie.uppercase(),
                    color = if (isModeEdition) Color.Red else MaterialTheme.colorScheme.primary, // Changer la couleur en mode édition
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isModeEdition) {
                        // Mode édition : Afficher directement le bouton de suppression
                        if (enveloppes.isEmpty()) {
                            IconButton(
                                onClick = { onSupprimerCategorie(nomCategorie) }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Supprimer la catégorie",
                                    tint = Color.Red
                                )
                            }
                        } else {
                            // Bouton grisé si la catégorie contient des enveloppes
                            IconButton(
                                onClick = { /* Ne rien faire */ },
                                enabled = false
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Impossible de supprimer",
                                    tint = Color.Gray
                                )
                            }
                        }
                    } else {
                        // Mode normal : Bouton d'ajout et menu
                        IconButton(onClick = onAjouterEnveloppeClick) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Ajouter une enveloppe", tint = Color.Gray)
                        }

                        // Menu pour la catégorie
                        Box {
                            IconButton(onClick = { showCategorieMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Plus d'options", tint = Color.Gray)
                            }

                            DropdownMenu(
                                expanded = showCategorieMenu,
                                onDismissRequest = { showCategorieMenu = false }
                            ) {
                                if (enveloppes.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Supprimer la catégorie") },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                        onClick = {
                                            onSupprimerCategorie(nomCategorie)
                                            showCategorieMenu = false
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Impossible de supprimer", color = Color.Gray) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Gray)
                                        },
                                        onClick = { showCategorieMenu = false },
                                        enabled = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Si la carte n'est PAS en cours de déplacement, on affiche les enveloppes ou le message vide
            if (!isDragging) {
                if (enveloppes.isEmpty()) {
                    Text(
                        text = if (isModeEdition)
                            "Catégorie vide - Aucune enveloppe à archiver"
                        else
                            "Aucune enveloppe. Cliquez sur '+' pour en ajouter une.",
                        color = Color.Gray,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    enveloppes.forEach { enveloppe ->
                        val isDraggedEnveloppe = draggedEnveloppeId == enveloppe.id

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isDraggedEnveloppe) {
                                        Modifier
                                            .zIndex(1f)
                                            .shadow(4.dp)
                                    } else {
                                        Modifier
                                    }
                                )
                                .then(
                                    if (isModeEdition && !isDragMode) {
                                        Modifier.pointerInput(enveloppe.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    onStartDragEnveloppe(enveloppe.id)
                                                },
                                                onDragEnd = {
                                                    // Le drag end est géré par le parent
                                                },
                                                onDrag = { _, _ ->
                                                    // Le drag visuel est géré par l'état isDraggedEnveloppe
                                                }
                                            )
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            EnveloppeConfigItem(
                                enveloppe = enveloppe,
                                isModeEdition = isModeEdition,
                                isDragMode = isDragMode,
                                isDragged = isDraggedEnveloppe,
                                onObjectifClick = if (!isDragMode) {
                                    { onObjectifClick(enveloppe) }
                                } else {
                                    { /* Ne rien faire en mode drag */ }
                                },
                                onSupprimerClick = if (isModeEdition && !isDragMode) {
                                    // En mode édition : supprimer l'enveloppe entière
                                    { onSupprimerEnveloppe(enveloppe) }
                                } else if (!isModeEdition && !isDragMode) {
                                    // En mode normal (menu 3 points) : supprimer seulement l'objectif
                                    { onSupprimerObjectifEnveloppe(enveloppe) }
                                } else {
                                    { /* Ne rien faire en mode drag */ }
                                }
                            )
                        }

                        if (!isDraggedEnveloppe) {
                            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CategorieCardPreview() {
    // Données d'exemple pour le preview
    val enveloppesExemple = listOf(
        Enveloppe(
            id = "1",
            utilisateurId = "user1",
            nom = "Courses",
            categorieId = "cat1",
            estArchive = false,
            ordre = 0,
            typeObjectif = TypeObjectif.Mensuel,
            objectifMontant = 500.0,
            dateObjectif = null,
            dateDebutObjectif = null,
            objectifJour = null
        ),
        Enveloppe(
            id = "2",
            utilisateurId = "user1",
            nom = "Essence",
            categorieId = "cat1",
            estArchive = false,
            ordre = 1,
            typeObjectif = TypeObjectif.Aucun,
            objectifMontant = 0.0,
            dateObjectif = null,
            dateDebutObjectif = null,
            objectifJour = null
        )
    )

    CategorieCard(
        nomCategorie = "Nécessités",
        enveloppes = enveloppesExemple,
        onAjouterEnveloppeClick = { },
        onObjectifClick = { },
        onSupprimerEnveloppe = { },
        onSupprimerCategorie = { }
    )
}

@Preview(showBackground = true)
@Composable
fun CategorieCardVidePreview() {
    CategorieCard(
        nomCategorie = "Catégorie Vide",
        enveloppes = emptyList(),
        onAjouterEnveloppeClick = { },
        onObjectifClick = { },
        onSupprimerEnveloppe = { },
        onSupprimerCategorie = { }
    )
}
