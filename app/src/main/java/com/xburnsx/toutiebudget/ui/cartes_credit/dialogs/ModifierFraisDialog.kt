// chemin/simule: /ui/cartes_credit/dialogs/ModifierFraisDialog.kt
// Dépendances: Jetpack Compose, Material3, CompteCredit, FormulaireCarteCredit, MoneyFormatter

package com.xburnsx.toutiebudget.ui.cartes_credit.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.ui.cartes_credit.FormulaireCarteCredit
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel

/**
 * Dialog pour modifier les frais mensuels fixes d'une carte de crédit.
 * Permet de définir le nom et le montant des frais mensuels (assurance, AccordD, etc.)
 * Utilise les couleurs du thème MaterialTheme pour une cohérence visuelle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModifierFraisDialog(
    formulaire: FormulaireCarteCredit,
    onNomFraisChange: (String) -> Unit,
    onFraisChange: (String) -> Unit,
    onSauvegarder: () -> Unit,
    onDismiss: () -> Unit,
    onOpenKeyboard: (Long, (Long) -> Unit) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Frais mensuels fixes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Champ pour le nom des frais
                Column {
                    Text(
                        text = "Nom du frais mensuel",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = formulaire.nomFraisMensuels,
                        onValueChange = onNomFraisChange,
                        label = {
                            Text(
                                "Nom (ex: Assurance, AccordD)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        placeholder = {
                            Text(
                                "Assurance",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        singleLine = true,
                        isError = formulaire.erreurNomFrais != null,
                        supportingText = {
                            if (formulaire.erreurNomFrais != null) {
                                Text(
                                    text = formulaire.erreurNomFrais,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    "Nom du type de frais",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            errorBorderColor = MaterialTheme.colorScheme.error,
                            errorLabelColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Champ pour le montant des frais mensuels avec ChampUniversel
                Column {
                    Text(
                        text = "Montant des frais mensuels",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Conversion du montant string vers centimes pour ChampUniversel
                    val montantEnCentimes = remember(formulaire.fraisMensuelsFixes) {
                        if (formulaire.fraisMensuelsFixes.isBlank()) {
                            0L
                        } else {
                            try {
                                (formulaire.fraisMensuelsFixes.toDouble() * 100).toLong()
                            } catch (_: Exception) {
                                0L
                            }
                        }
                    }

                    ChampUniversel(
                        valeur = montantEnCentimes,
                        onValeurChange = { nouveauMontant ->
                            val nouveauMontantString = if (nouveauMontant == 0L) {
                                ""
                            } else {
                                (nouveauMontant / 100.0).toString()
                            }
                            onFraisChange(nouveauMontantString)
                        },
                        libelle = "Montant (ex: 15.50$)",
                        utiliserClavier = false, // Désactiver le clavier intégré
                        isMoney = true,
                        icone = Icons.Default.Receipt,
                        estObligatoire = false,
                        onClicPersonnalise = {
                            // Utiliser le système de clavier global
                            onOpenKeyboard(montantEnCentimes) { nouveauMontant ->
                                val nouveauMontantString = if (nouveauMontant == 0L) {
                                    ""
                                } else {
                                    (nouveauMontant / 100.0).toString()
                                }
                                onFraisChange(nouveauMontantString)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Message d'aide sous le champ
                    Text(
                        text = if (formulaire.erreurFrais != null) {
                            formulaire.erreurFrais
                        } else {
                            "Laissez vide pour supprimer les frais"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (formulaire.erreurFrais != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        },
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSauvegarder,
                enabled = formulaire.erreurFrais == null && formulaire.erreurNomFrais == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = null,
                    tint = if (formulaire.erreurFrais == null && formulaire.erreurNomFrais == null)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sauvegarder")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Annuler")
            }
        }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun ModifierFraisDialogPreview() {

    val formulaire = FormulaireCarteCredit(
        fraisMensuelsFixes = "15.50",
        nomFraisMensuels = "Assurance"
    )

    MaterialTheme {
        ModifierFraisDialog(
            formulaire = formulaire,
            onNomFraisChange = {},
            onFraisChange = {},
            onSauvegarder = {},
            onDismiss = {},
            onOpenKeyboard = { montant, onValeurChange ->
                // Simuler l'ouverture du clavier global
                // Dans un vrai cas d'utilisation, vous utiliseriez un composant de clavier
                // ou une fonction pour ouvrir le clavier natif.
                // Pour cet exemple, nous allons juste appeler la fonction de rappel.
                onValeurChange(montant)
            }
        )
    }
}