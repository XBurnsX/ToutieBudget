// chemin/simule: /ui/categories/dialogs/DefinirObjectifDialog.kt
// Dépendances: Jetpack Compose, Material3, ChampArgent, TypeObjectif

package com.xburnsx.toutiebudget.ui.categories.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.ui.categories.ObjectifFormState
import com.xburnsx.toutiebudget.ui.categories.composants.SelecteurJourMois
import com.xburnsx.toutiebudget.ui.categories.composants.SelecteurJourSemaine
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun DefinirObjectifDialog(
    nomEnveloppe: String,
    formState: ObjectifFormState,
    onValueChange: (TypeObjectif?, String?, Date?, Int?) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: () -> Unit,
    onOpenKeyboard: (Long, (Long) -> Unit) -> Unit // ✅ REMIS LE PARAMÈTRE
) {
    // Conversion du montant pour ChampUniversel
    val montantEnCentimes = remember(formState.montant) {
        if (formState.montant.isBlank()) {
            0L
        } else {
            try {
                (formState.montant.toDouble() * 100).toLong()
            } catch (e: Exception) {
                0L
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Définir un objectif") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Nom de l'enveloppe
                Text(
                    text = "Objectif pour : $nomEnveloppe", 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // *** CHAMP MONTANT AVEC CALLBACK VERS CLAVIER GLOBAL ***
                ChampUniversel(
                    valeur = montantEnCentimes,
                    onValeurChange = { nouveauMontant ->
                        val nouveauMontantString = (nouveauMontant / 100.0).toString()
                        onValueChange(null, nouveauMontantString, null, null)
                    },
                    libelle = "Montant objectif",
                    utiliserClavier = false, // ✅ DÉSACTIVER le clavier intégré
                    isMoney = true,
                    icone = Icons.Default.Flag,
                    estObligatoire = true,
                    onClicPersonnalise = {
                        // ✅ UTILISER le callback vers le clavier global
                        onOpenKeyboard(montantEnCentimes) { nouveauMontant ->
                            val nouveauMontantString = (nouveauMontant / 100.0).toString()
                            onValueChange(null, nouveauMontantString, null, null)
                        }
                    },
                    modifier = Modifier
                )
                
                // Sélection du type d'objectif
                Text(
                    text = "Fréquence de l'objectif :",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoutonTypeObjectif(
                        libelle = "Mensuel", 
                        estSelectionne = formState.type == TypeObjectif.Mensuel, 
                        onClick = { onValueChange(TypeObjectif.Mensuel, null, null, null) }, 
                        modifier = Modifier.weight(1f)
                    )
                    BoutonTypeObjectif(
                        libelle = "2 semaines", 
                        estSelectionne = formState.type == TypeObjectif.Bihebdomadaire, 
                        onClick = { onValueChange(TypeObjectif.Bihebdomadaire, null, null, null) }, 
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoutonTypeObjectif(
                        libelle = "Échéance", 
                        estSelectionne = formState.type == TypeObjectif.Echeance, 
                        onClick = { onValueChange(TypeObjectif.Echeance, null, null, null) }, 
                        modifier = Modifier.weight(1f)
                    )
                    BoutonTypeObjectif(
                        libelle = "Annuel", 
                        estSelectionne = formState.type == TypeObjectif.Annuel, 
                        onClick = { onValueChange(TypeObjectif.Annuel, null, null, null) }, 
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Configuration spécifique selon le type d'objectif
                when (formState.type) {
                    TypeObjectif.Mensuel -> {
                        SelecteurJourMois(
                            jourSelectionne = formState.jour,
                            onJourSelected = { onValueChange(null, null, null, it) }
                        )
                    }
                    TypeObjectif.Bihebdomadaire -> {
                        SelecteurJourSemaine(
                            jourSelectionne = formState.jour,
                            onJourSelected = { onValueChange(null, null, null, it) }
                        )
                    }
                    TypeObjectif.Echeance -> {
                        // Sélecteur de date (à implémenter si nécessaire)
                        Text(
                            text = "Date d'échéance : ${formState.date?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it) } ?: "Non définie"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { /* TODO: Ouvrir DatePicker */ },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CalendarToday, "Choisir date")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choisir une date")
                        }
                    }
                    TypeObjectif.Annuel -> {
                        Text(
                            text = "L'objectif sera calculé automatiquement sur une base annuelle.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    TypeObjectif.Aucun -> {
                        Text(
                            text = "Veuillez sélectionner un type d'objectif.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    null -> {
                        Text(
                            text = "Veuillez sélectionner un type d'objectif.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = formState.type != null && formState.montant.toDoubleOrNull() != null && formState.montant.toDoubleOrNull()!! > 0
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

/**
 * Composant pour les boutons de sélection du type d'objectif.
 */
@Composable
private fun BoutonTypeObjectif(
    libelle: String,
    estSelectionne: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val couleurs = if (estSelectionne) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    } else {
        ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    }
    
    if (estSelectionne) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = couleurs
        ) {
            Text(libelle)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            colors = couleurs
        ) {
            Text(libelle)
        }
    }
}