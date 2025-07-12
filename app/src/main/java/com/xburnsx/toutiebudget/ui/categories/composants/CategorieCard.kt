// chemin/simule: /ui/categories/composants/CategorieCard.kt
package com.xburnsx.toutiebudget.ui.categories.composants

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif

@Composable
fun CategorieCard(
    nomCategorie: String,
    enveloppes: List<Enveloppe>,
    onAjouterEnveloppeClick: () -> Unit,
    onObjectifClick: (Enveloppe) -> Unit,
    onSupprimerEnveloppe: (Enveloppe) -> Unit = {},
    onSupprimerCategorie: (String) -> Unit = {}
) {
    var showCategorieMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
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
            if (enveloppes.isEmpty()) {
                Text(
                    text = "Aucune enveloppe. Cliquez sur '+' pour en ajouter une.",
                    color = Color.Gray,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                enveloppes.forEach { enveloppe ->
                    EnveloppeConfigItem(
                        enveloppe = enveloppe, 
                        onObjectifClick = { onObjectifClick(enveloppe) },
                        onSupprimerClick = { onSupprimerEnveloppe(enveloppe) }
                    )
                    Divider(color = Color.DarkGray, thickness = 0.5.dp)
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
            objectifType = TypeObjectif.Mensuel,
            objectifMontant = 500.0,
            objectifDate = null,
            objectifJour = null
        ),
        Enveloppe(
            id = "2",
            utilisateurId = "user1",
            nom = "Essence",
            categorieId = "cat1",
            estArchive = false,
            ordre = 1,
            objectifType = TypeObjectif.Aucun,
            objectifMontant = 0.0,
            objectifDate = null,
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
