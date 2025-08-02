// chemin/simule: /ui/comptes/dialogs/AjoutCompteDialog.kt
// Dépendances: Jetpack Compose, Material3, ChampMontantUniversel, CouleurSelecteur

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
import com.xburnsx.toutiebudget.ui.comptes.COULEURS_COMPTES
import com.xburnsx.toutiebudget.ui.comptes.composants.CouleurSelecteur
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjoutCompteDialog(
    formState: CompteFormState,
    onDismissRequest: () -> Unit,
    onValueChange: (String?, String?, String?, String?, String?) -> Unit, // AJOUT DU 5ème PARAMÈTRE
    onSave: () -> Unit,
    onOpenKeyboard: (Long, (Long) -> Unit) -> Unit
) {
    val typesDeCompte = listOf("Compte chèque", "Carte de crédit", "Dette", "Investissement")
    val couleursDisponibles = COULEURS_COMPTES
    var expanded by remember { mutableStateOf(false) }

    // État du clavier
    val montantEnCentimes = remember(formState.solde) {
        if (formState.solde.isBlank()) {
            0L
        } else {
            try {
                (formState.solde.toDouble() * 100).toLong()
            } catch (e: Exception) {
                0L
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Nouveau Compte") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Champ nom du compte
                OutlinedTextField(
                    value = formState.nom,
                    onValueChange = { onValueChange(it, null, null, null, null) },
                    label = { Text("Nom du compte") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words
                    )
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
                                    onValueChange(null, type, null, null, null)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                ChampUniversel(
                    valeur = montantEnCentimes,
                    onValeurChange = { nouveauMontant ->
                        val nouveauSolde = (nouveauMontant / 100.0).toString()
                        onValueChange(null, null, nouveauSolde, null, null)
                    },
                    libelle = "Solde initial",
                    utiliserClavier = false, // Désactiver le clavier intégré
                    isMoney = true,
                    icone = Icons.Default.AccountBalance,
                    estObligatoire = false,
                    onClicPersonnalise = {
                        // Utiliser le système de clavier global
                        onOpenKeyboard(montantEnCentimes) { nouveauMontant ->
                            val nouveauSolde = (nouveauMontant / 100.0).toString()
                            onValueChange(null, null, nouveauSolde, null, null)
                        }
                    },
                    modifier = Modifier
                )
                
                // Sélecteur de couleur (sauf pour les dettes qui sont toujours rouges)
                if (formState.type != "Dette") {
                    CouleurSelecteur(
                        couleurs = couleursDisponibles,
                        couleurSelectionnee = formState.couleur,
                        onCouleurSelected = { onValueChange(null, null, null, null, it) } // CORRECTION : 5ème PARAMÈTRE
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