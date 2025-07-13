// chemin/simule: /ui/comptes/dialogs/AjoutCompteDialog.kt
// Dépendances: Jetpack Compose, Material3, ChampArgent, CouleurSelecteur

package com.xburnsx.toutiebudget.ui.comptes.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.ui.comptes.CompteFormState
import com.xburnsx.toutiebudget.ui.comptes.composants.CouleurSelecteur
import com.xburnsx.toutiebudget.ui.composants_communs.ChampArgent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjoutCompteDialog(
    formState: CompteFormState,
    onDismissRequest: () -> Unit,
    onValueChange: (String?, String?, String?, String?) -> Unit,
    onSave: () -> Unit
) {
    val typesDeCompte = listOf("Compte chèque", "Carte de crédit", "Dette", "Investissement")
    val couleursDisponibles = listOf("#F44336", "#E91E63", "#9C27B0", "#2196F3", "#4CAF50", "#FFC107")
    var expanded by remember { mutableStateOf(false) }

    // Conversion entre format centimes et format texte pour ChampArgent
    val soldeEnCentimes = (formState.solde.toDoubleOrNull() ?: 0.0) * 100
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Nouveau Compte") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Champ nom du compte
                OutlinedTextField(
                    value = formState.nom,
                    onValueChange = { onValueChange(it, null, null, null) },
                    label = { Text("Nom du compte") },
                    singleLine = true
                )
                
                // Sélecteur de type de compte
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = formState.type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type de compte") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        typesDeCompte.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    onValueChange(null, type, null, null)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                // *** NOUVEAU : Champ argent pour le solde initial ***
                ChampArgent(
                    montant = soldeEnCentimes.toLong(),
                    onMontantChange = { nouveauMontantEnCentimes ->
                        val nouveauSoldeEnDollars = nouveauMontantEnCentimes / 100.0
                        onValueChange(null, null, nouveauSoldeEnDollars.toString(), null)
                    },
                    libelle = "Solde initial",
                    icone = Icons.Default.AccountBalance,
                    estObligatoire = false,
                    modifier = Modifier
                )
                
                // Sélecteur de couleur (sauf pour les dettes qui sont toujours rouges)
                if (formState.type != "Dette") {
                    CouleurSelecteur(
                        couleurs = couleursDisponibles,
                        couleurSelectionnee = formState.couleur,
                        onCouleurSelected = { onValueChange(null, null, null, it) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = formState.nom.isNotBlank() && formState.type.isNotBlank()
            ) {
                Text("Sauvegarder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Annuler")
            }
        }
    )
}