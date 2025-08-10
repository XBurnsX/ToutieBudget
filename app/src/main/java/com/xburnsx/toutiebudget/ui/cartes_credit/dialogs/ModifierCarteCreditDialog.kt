package com.xburnsx.toutiebudget.ui.cartes_credit.dialogs

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CreditScore
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xburnsx.toutiebudget.ui.cartes_credit.FormulaireCarteCredit
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel
import com.xburnsx.toutiebudget.ui.composants_communs.ClavierNumerique

@Composable
fun ModifierCarteCreditDialog(
    formulaire: FormulaireCarteCredit,
    onNomChange: (String) -> Unit,
    onLimiteChange: (String) -> Unit,
    onTauxChange: (String) -> Unit,
    onSoldeChange: (String) -> Unit,
    onPaiementMinimumChange: (String) -> Unit,
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
                    // Champ limite de crédit avec ChampUniversel
                    ChampUniversel(
                        valeur = (formulaire.limiteCredit.toDoubleOrNull()?.times(100) ?: 0.0).toLong(),
                        onValeurChange = { nouveauMontant ->
                            onLimiteChange((nouveauMontant / 100.0).toString())
                        },
                        libelle = "Limite de crédit",
                        utiliserClavier = false,
                        isMoney = true,
                        suffix = "", // ENLEVÉ le suffixe "$"
                        icone = Icons.Default.CreditScore,
                        estObligatoire = true,
                        couleurValeur = MaterialTheme.colorScheme.onSurface,
                        onClicPersonnalise = {
                            montantClavierInitial = (formulaire.limiteCredit.toDoubleOrNull()?.times(100) ?: 0.0).toLong()
                            nomDialogClavier = "Limite de crédit"
                            onMontantChangeCallback = { nouveauMontant ->
                                onLimiteChange((nouveauMontant / 100.0).toString())
                            }
                            showKeyboard = true
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Champ taux d'intérêt avec ChampUniversel pour permettre les décimales
                    ChampUniversel(
                        valeur = if (formulaire.tauxInteret.isEmpty()) 0L else (formulaire.tauxInteret.toDoubleOrNull()?.times(100) ?: 0.0).toLong(),
                        onValeurChange = { nouveauTaux ->
                            onTauxChange((nouveauTaux / 100.0).toString())
                        },
                        libelle = "Taux d'intérêt (%)",
                        utiliserClavier = false,
                        isMoney = false,
                        suffix = "%",
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
                    suffix = "", // ENLEVÉ le suffixe "$"
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

                Spacer(modifier = Modifier.height(12.dp))

                // Champ paiement minimum avec ChampUniversel
                ChampUniversel(
                    valeur = (formulaire.paiementMinimum.toDoubleOrNull()?.times(100) ?: 0.0).toLong(),
                    onValeurChange = { nouveauMontant ->
                        onPaiementMinimumChange((nouveauMontant / 100.0).toString())
                    },
                    libelle = "Paiement minimum",
                    utiliserClavier = false,
                    isMoney = true,
                    suffix = "",
                    icone = Icons.Default.Payment,
                    estObligatoire = false,
                    couleurValeur = MaterialTheme.colorScheme.onSurface,
                    onClicPersonnalise = {
                        montantClavierInitial = (formulaire.paiementMinimum.toDoubleOrNull()?.times(100) ?: 0.0).toLong()
                        nomDialogClavier = "Paiement minimum"
                        onMontantChangeCallback = { nouveauMontant ->
                            onPaiementMinimumChange((nouveauMontant / 100.0).toString())
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
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                showKeyboard = false
                                onMontantChangeCallback = null
                            }
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.ime)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        ClavierNumerique(
                            montantInitial = montantClavierInitial,
                            isMoney = nomDialogClavier != "Taux d'intérêt", // false pour taux d'intérêt, true pour le reste
                            suffix = when (nomDialogClavier) {
                                "Limite de crédit" -> "$"
                                "Dette actuelle" -> "$"
                                "Paiement minimum" -> "$"
                                else -> ""
                            },
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
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun ModifierCarteCreditDialogPreview() {
    val formulaire = FormulaireCarteCredit(
        nom = "Carte Visa",
        limiteCredit = "10000.0",
        tauxInteret = "19.99",
        soldeActuel = "2500.0",
        paiementMinimum = "500.0",
        couleur = "#2196F3"
    )
    
    ModifierCarteCreditDialog(
        formulaire = formulaire,
        onNomChange = {},
        onLimiteChange = {},
        onTauxChange = {},
        onSoldeChange = {},
        onPaiementMinimumChange = {},
        onSauvegarder = {},
        onDismiss = {}
    )
}
