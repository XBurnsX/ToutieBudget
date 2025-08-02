// chemin/simule: /ui/comptes/dialogs/ModifierCompteDialog.kt
// Dépendances: Jetpack Compose, Material3, ChampArgent, CouleurSelecteur

package com.xburnsx.toutiebudget.ui.comptes.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.ui.comptes.COULEURS_COMPTES
import com.xburnsx.toutiebudget.ui.comptes.CompteFormState
import com.xburnsx.toutiebudget.ui.comptes.composants.CouleurSelecteur
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel

@Composable
fun ModifierCompteDialog(
    formState: CompteFormState,
    onDismissRequest: () -> Unit,
    onValueChange: (String?, String?, String?, String?, String?) -> Unit,
    onSave: () -> Unit,
    onOpenKeyboard: (Long, (Long) -> Unit) -> Unit
) {
    val couleursDisponibles = COULEURS_COMPTES

    // Conversion entre format centimes et format texte pour ChampArgent
    val soldeEnCentimes = remember(formState.solde) {
        (formState.solde.toDoubleOrNull() ?: 0.0) * 100
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Modifier le Compte") },
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
                
                // *** NOUVEAU : Champ argent pour le solde ***
                ChampUniversel(
                    valeur = soldeEnCentimes.toLong(),
                    onValeurChange = { nouveauMontant ->
                        val nouveauSolde = (nouveauMontant / 100.0).toString()
                        onValueChange(null, null, nouveauSolde, null, null)
                    },
                    libelle = "Solde actuel",
                    utiliserClavier = false, // Désactiver le clavier intégré
                    isMoney = true,
                    icone = Icons.Default.AccountBalance,
                    estObligatoire = false,
                    onClicPersonnalise = {
                        // Utiliser le système de clavier global
                        onOpenKeyboard(soldeEnCentimes.toLong()) { nouveauMontant ->
                            val nouveauSolde = (nouveauMontant / 100.0).toString()
                            onValueChange(null, null, nouveauSolde, null, null)
                        }
                    },
                    modifier = Modifier
                )

                // *** NOUVEAU : Champ prêt à placer (seulement pour les comptes chèques) ***
                if (formState.type == "Compte chèque") {
                    val pretAPlacerEnCentimes = remember(formState.pretAPlacer) {
                        (formState.pretAPlacer.toDoubleOrNull() ?: 0.0) * 100
                    }

                    ChampUniversel(
                        valeur = pretAPlacerEnCentimes.toLong(),
                        onValeurChange = { nouveauMontant ->
                            val nouveauPretAPlacer = (nouveauMontant / 100.0).toString()
                            onValueChange(null, null, null, nouveauPretAPlacer, null)
                        },
                        libelle = "Prêt à placer",
                        utiliserClavier = false,
                        isMoney = true,
                        icone = Icons.Default.AccountBalance,
                        estObligatoire = false,
                        onClicPersonnalise = {
                            onOpenKeyboard(pretAPlacerEnCentimes.toLong()) { nouveauMontant ->
                                val nouveauPretAPlacer = (nouveauMontant / 100.0).toString()
                                onValueChange(null, null, null, nouveauPretAPlacer, null)
                            }
                        },
                        modifier = Modifier
                    )
                }
                
                // Sélecteur de couleur (sauf pour les dettes qui sont toujours rouges)
                if (formState.type != "Dette") {
                    CouleurSelecteur(
                        couleurs = couleursDisponibles,
                        couleurSelectionnee = formState.couleur,
                        onCouleurSelected = { onValueChange(null, null, null, null, it) }
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

    // SUPPRIMÉ : Le clavier est maintenant géré par ComptesScreen
}