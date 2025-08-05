package com.xburnsx.toutiebudget.ui.cartes_credit.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.ui.cartes_credit.CartesCreditUiState
import com.xburnsx.toutiebudget.ui.cartes_credit.FormulaireCarteCredit
import com.xburnsx.toutiebudget.utils.MoneyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModifierFraisDialog(
    carte: CompteCredit,
    formulaire: FormulaireCarteCredit,
    onNomFraisChange: (String) -> Unit,
    onFraisChange: (String) -> Unit,
    onSauvegarder: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = null,
                    tint = Color(0xFF64B5F6),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Frais mensuels fixes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Informations sur la carte
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF5F5F5)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Carte: ${carte.nom}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Limite: ${MoneyFormatter.formatAmount(carte.limiteCredit)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF666666)
                        )
                    }
                }

                // Champ pour le nom des frais
                Column {
                    Text(
                        text = "Nom du frais mensuel",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    OutlinedTextField(
                        value = formulaire.nomFraisMensuels,
                        onValueChange = onNomFraisChange,
                        label = { Text("Nom (ex: Assurance, AccordD)") },
                        placeholder = { Text("Assurance") },
                        singleLine = true,
                        isError = formulaire.erreurNomFrais != null,
                        supportingText = {
                            if (formulaire.erreurNomFrais != null) {
                                Text(
                                    text = formulaire.erreurNomFrais!!,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text("Nom du type de frais")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Champ pour le montant des frais mensuels
                Column {
                    Text(
                        text = "Montant des frais mensuels",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    OutlinedTextField(
                        value = formulaire.fraisMensuelsFixes,
                        onValueChange = onFraisChange,
                        label = { Text("Montant (ex: 15.50)") },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        isError = formulaire.erreurFrais != null,
                        supportingText = {
                            if (formulaire.erreurFrais != null) {
                                Text(
                                    text = formulaire.erreurFrais!!,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text("Laissez vide pour supprimer les frais")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Exemples de frais
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE3F2FD)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Exemples de frais mensuels:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "• Assurance: 15-25€",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF666666)
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "• AccordD: 5-10€",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF666666)
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "• Frais de gestion: 2-5€",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF666666)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSauvegarder,
                enabled = formulaire.erreurFrais == null && formulaire.erreurNomFrais == null
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sauvegarder")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Annuler")
            }
        }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun ModifierFraisDialogPreview() {
    val carteCredit = CompteCredit(
        id = "1",
        utilisateurId = "user1",
        nom = "Carte Visa",
        soldeUtilise = -2500.0,
        couleur = "#2196F3",
        estArchive = false,
        ordre = 1,
        limiteCredit = 10000.0,
        tauxInteret = 19.99,
        fraisMensuelsFixes = 15.50,
        nomFraisMensuels = "Assurance"
    )
    
    val formulaire = FormulaireCarteCredit(
        fraisMensuelsFixes = "15.50",
        nomFraisMensuels = "Assurance"
    )
    
    ModifierFraisDialog(
        carte = carteCredit,
        formulaire = formulaire,
        onNomFraisChange = {},
        onFraisChange = {},
        onSauvegarder = {},
        onDismiss = {}
    )
} 