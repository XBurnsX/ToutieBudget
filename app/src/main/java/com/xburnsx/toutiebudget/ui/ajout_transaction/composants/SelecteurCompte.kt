// chemin/simule: /ui/ajout_transaction/composants/SelecteurCompte.kt
// Dépendances: Jetpack Compose, Material3, Compte.kt

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.ui.budget.composants.toColor
import java.text.NumberFormat
import java.util.Locale

/**
 * Composant pour sélectionner un compte parmi la liste disponible.
 * Affiche le compte sélectionné et permet d'ouvrir un dialog de sélection.
 */
@Composable
fun SelecteurCompte(
    comptes: List<Compte>,
    compteSelectionne: Compte?,
    onCompteChange: (Compte) -> Unit,
    modifier: Modifier = Modifier
) {
    var dialogOuvert by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Sélectionnez un compte",
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
                width = if (compteSelectionne != null) 1.dp else 2.dp,
                color = if (compteSelectionne != null) {
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
                if (compteSelectionne != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Indicateur de couleur du compte
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = compteSelectionne.couleur.toColor(),
                                    shape = RoundedCornerShape(6.dp)
                                )
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            Text(
                                text = compteSelectionne.nom,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            
                            Text(
                                text = obtenirLibelleTypeCompte(compteSelectionne) + " • " + 
                                      NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)
                                          .format(compteSelectionne.solde),
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Sélectionner un compte",
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
    
    // Dialog de sélection
    if (dialogOuvert) {
        DialogSelectionCompte(
            comptes = comptes,
            onCompteSelectionne = { compte ->
                onCompteChange(compte)
                dialogOuvert = false
            },
            onDismiss = { dialogOuvert = false }
        )
    }
}

/**
 * Dialog pour sélectionner un compte dans la liste.
 */
@Composable
private fun DialogSelectionCompte(
    comptes: List<Compte>,
    onCompteSelectionne: (Compte) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 800.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Choisir un compte",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Grouper les comptes par catégorie
                val comptesGroupes = comptes.groupBy { compte ->
                    when (compte) {
                        is CompteCheque -> "Comptes chèque"
                        is CompteCredit -> "Cartes de crédit"
                        is CompteDette -> "Dettes"
                        is CompteInvestissement -> "Investissements"
                        else -> "Autres"
                    }
                }
                
                LazyColumn {
                    comptesGroupes.forEach { (categorie, comptesCategorie) ->
                        // En-tête de catégorie
                        item {
                            Text(
                                text = categorie,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                            )
                        }
                        
                        // Items de la catégorie
                        items(comptesCategorie.sortedBy { it.ordre }) { compte ->
                            ItemCompte(
                                compte = compte,
                                onClick = { onCompteSelectionne(compte) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Item individuel de compte dans la liste.
 */
@Composable
private fun ItemCompte(
    compte: Compte,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
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
            // Indicateur de couleur
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = compte.couleur.toColor(),
                        shape = RoundedCornerShape(6.dp)
                    )
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = compte.nom,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                
                Text(
                    text = obtenirLibelleTypeCompte(compte),
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            
            Text(
                text = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)
                    .format(compte.solde),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (compte.solde >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
            )
        }
    }
}

/**
 * Retourne le libellé du type de compte pour l'affichage.
 */
private fun obtenirLibelleTypeCompte(compte: Compte): String {
    return when (compte) {
        is CompteCheque -> "Compte chèque"
        is CompteCredit -> "Carte de crédit"
        is CompteDette -> "Dette"
        is CompteInvestissement -> "Investissement"
        else -> "Compte"
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun PreviewSelecteurCompte() {
    val comptesTest = listOf(
        CompteCheque(
            id = "1",
            utilisateurId = "",
            nom = "Compte Desjardins",
            solde = 1500.50,
            pretAPlacerRaw = 800.0,
            couleur = "#10B981",
            estArchive = false,
            ordre = 0
        ),
        CompteCredit(
            id = "2",
            utilisateurId = "",
            nom = "Visa Desjardins",
            soldeUtilise = -250.75,
            couleur = "#EF4444",
            limiteCredit = 5000.0,
            tauxInteret = 19.99,
            estArchive = false,
            ordre = 1
        )
    )
    
    SelecteurCompte(
        comptes = comptesTest,
        compteSelectionne = comptesTest.first(),
        onCompteChange = { }
    )
}