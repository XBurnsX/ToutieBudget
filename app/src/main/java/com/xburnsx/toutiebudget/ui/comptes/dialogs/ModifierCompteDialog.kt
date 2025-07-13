// chemin/simule: /ui/comptes/dialogs/ModifierCompteDialog.kt
// Dépendances: Jetpack Compose, Material3, ChampArgent, CouleurSelecteur

package com.xburnsx.toutiebudget.ui.comptes.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.ui.comptes.CompteFormState
import com.xburnsx.toutiebudget.ui.comptes.composants.CouleurSelecteur
import com.xburnsx.toutiebudget.ui.composants_communs.ChampArgent

@Composable
fun ModifierCompteDialog(
    formState: CompteFormState,
    onDismissRequest: () -> Unit,
    onValueChange: (String?, String?, String?, String?) -> Unit,
    onSave: () -> Unit
) {
    val couleursDisponibles = listOf("#F44336", "#E91E63", "#9C27B0", "#2196F3", "#4CAF50", "#FFC107")
    
    // Conversion entre format centimes et format texte pour ChampArgent
    val soldeEnCentimes = (formState.solde.toDoubleOrNull() ?: 0.0) * 100

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Modifier le Compte") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Champ nom du compte
                OutlinedTextField(
                    value = formState.nom,
                    onValueChange = { onValueChange(it, null, null, null) },
                    label = { Text("Nom du compte") },
                    singleLine = true
                )
                
                // *** NOUVEAU : Champ argent pour le solde ***
                ChampArgent(
                    montant = soldeEnCentimes.toLong(),
                    onMontantChange = { nouveauMontantEnCentimes ->
                        val nouveauSoldeEnDollars = nouveauMontantEnCentimes / 100.0
                        onValueChange(null, null, nouveauSoldeEnDollars.toString(), null)
                    },
                    libelle = "Solde actuel",
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
                enabled = formState.nom.isNotBlank()
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