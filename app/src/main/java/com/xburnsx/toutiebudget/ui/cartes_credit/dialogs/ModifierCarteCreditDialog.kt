package com.xburnsx.toutiebudget.ui.cartes_credit.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.xburnsx.toutiebudget.ui.cartes_credit.FormulaireCarteCredit
import com.xburnsx.toutiebudget.ui.comptes.COULEURS_COMPTES
import com.xburnsx.toutiebudget.ui.comptes.composants.CouleurSelecteur
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel
import com.xburnsx.toutiebudget.ui.composants_communs.ClavierNumerique

@Composable
fun ModifierCarteCreditDialog(
    formulaire: FormulaireCarteCredit,
    onNomChange: (String) -> Unit,
    onLimiteChange: (String) -> Unit,
    onTauxChange: (String) -> Unit,
    onSoldeChange: (String) -> Unit,
    onCouleurChange: (String) -> Unit,
    onSauvegarder: () -> Unit,
    onDismiss: () -> Unit
) {
    // États pour le clavier numérique
    var showKeyboard by remember { mutableStateOf(false) }
    var montantClavierInitial by remember { mutableStateOf(0L) }
    var nomDialogClavier by remember { mutableStateOf("") }
    var onMontantChangeCallback by remember { mutableStateOf<((Long) -> Unit)?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2C2C2E)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // En-tête
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Modifier la carte de crédit",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Champ nom
                OutlinedTextField(
                    value = formulaire.nom,
                    onValueChange = onNomChange,
                    label = { Text("Nom de la carte") },
                    placeholder = { Text("Ex: Visa Principal") },
                    leadingIcon = {
                        Icon(Icons.Default.CreditCard, contentDescription = null)
                    },
                    isError = formulaire.erreurNom != null,
                    supportingText = formulaire.erreurNom?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Champs limite et taux sur la même ligne
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = formulaire.limiteCredit,
                        onValueChange = onLimiteChange,
                        label = { Text("Limite de crédit") },
                        placeholder = { Text("5000") },
                        leadingIcon = {
                            Icon(Icons.Default.CreditScore, contentDescription = null)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = formulaire.erreurLimite != null,
                        supportingText = formulaire.erreurLimite?.let { { Text(it) } },
                        modifier = Modifier.weight(1f)
                    )

                    // Champ taux d'intérêt avec ChampUniversel
                    ChampUniversel(
                        valeur = if (formulaire.tauxInteret.isEmpty()) 0L else (formulaire.tauxInteret.toDoubleOrNull()?.times(100) ?: 0.0).toLong(),
                        onValeurChange = { nouveauTaux ->
                            onTauxChange((nouveauTaux / 100.0).toString())
                        },
                        libelle = "Taux d'intérêt (%)",
                        utiliserClavier = false,
                        isMoney = false, // FALSE pour les taux d'intérêt !
                        icone = Icons.Default.Percent,
                        estObligatoire = false,
                        couleurValeur = MaterialTheme.colorScheme.onSurface,
                        onClicPersonnalise = {
                            montantClavierInitial = if (formulaire.tauxInteret.isEmpty()) 0L else (formulaire.tauxInteret.toDoubleOrNull()?.times(100) ?: 0.0).toLong()
                            nomDialogClavier = "Taux d'intérêt"
                            onMontantChangeCallback = { nouveauTaux ->
                                onTauxChange((nouveauTaux / 100.0).toString())
                            }
                            showKeyboard = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Champ solde actuel avec ChampUniversel
                ChampUniversel(
                    valeur = (formulaire.soldeActuel.toDoubleOrNull()?.times(100) ?: 0.0).toLong(),
                    onValeurChange = { nouveauMontant ->
                        onSoldeChange((nouveauMontant / 100.0).toString())
                    },
                    libelle = "Dette actuelle",
                    utiliserClavier = false,
                    isMoney = true,
                    icone = Icons.Default.AttachMoney,
                    estObligatoire = true,
                    couleurValeur = MaterialTheme.colorScheme.onSurface,
                    onClicPersonnalise = {
                        montantClavierInitial = (formulaire.soldeActuel.toDoubleOrNull()?.times(100) ?: 0.0).toLong()
                        nomDialogClavier = "Dette actuelle"
                        onMontantChangeCallback = { nouveauMontant ->
                            onSoldeChange((nouveauMontant / 100.0).toString())
                        }
                        showKeyboard = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Boutons d'action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Annuler")
                    }

                    Button(
                        onClick = onSauvegarder,
                        enabled = formulaire.estValide,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sauvegarder")
                    }
                }
            }
        }

        // Clavier numérique par-dessus tout - utiliser Dialog pour garantir le z-index maximal
        if (showKeyboard) {
            Dialog(
                onDismissRequest = {
                    showKeyboard = false
                    onMontantChangeCallback = null
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                // Le Dialog garantit que le clavier sera au-dessus de tout
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Permet de cliquer à travers la Box pour fermer le dialogue
                        .pointerInput(Unit) {
                            detectTapGestures {
                                showKeyboard = false
                                onMontantChangeCallback = null
                            }
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Le clavier lui-même
                    ClavierNumerique(
                        montantInitial = montantClavierInitial,
                        isMoney = nomDialogClavier != "Taux d'intérêt", // false pour taux d'intérêt, true pour le reste
                        suffix = "",
                        onMontantChange = { nouveauMontant ->
                            onMontantChangeCallback?.invoke(nouveauMontant)
                        },
                        onFermer = {
                            showKeyboard = false
                            onMontantChangeCallback = null
                        }
                    )
                }
            }
        }
    }
}
