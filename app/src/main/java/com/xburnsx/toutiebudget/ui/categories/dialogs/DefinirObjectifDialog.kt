// chemin/simule: /ui/categories/dialogs/DefinirObjectifDialog.kt
// Dépendances: Jetpack Compose, Material3, ChampArgent, TypeObjectif

package com.xburnsx.toutiebudget.ui.categories.dialogs

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.ui.categories.ObjectifFormState
import com.xburnsx.toutiebudget.ui.categories.composants.SelecteurJourMois
import com.xburnsx.toutiebudget.ui.categories.composants.SelecteurJourSemaine
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


@Composable
fun DefinirObjectifDialog(
    nomEnveloppe: String,
    formState: ObjectifFormState,
    onValueChange: (TypeObjectif?, String?, Date?, Int?, Boolean?, Date?, Date?) -> Unit,
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
                // Utiliser BigDecimal pour éviter les erreurs de précision
                BigDecimal(formState.montant)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .toLong()
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
                            // Utiliser BigDecimal pour éviter les erreurs de précision
                            val nouveauMontantString = String.format("%.2f", 
                                BigDecimal.valueOf(nouveauMontant)
                                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                                    .toDouble()
                            )
                            onValueChange(null, nouveauMontantString, null, null, null, null, null)
                        },
                    libelle = "Montant objectif",
                    utiliserClavier = false, // ✅ DÉSACTIVER le clavier intégré
                    isMoney = true,
                    icone = Icons.Default.Flag,
                    estObligatoire = true,
                                            onClicPersonnalise = {
                            // ✅ UTILISER le callback vers le clavier global
                            onOpenKeyboard(montantEnCentimes) { nouveauMontant ->
                                // Utiliser BigDecimal pour éviter les erreurs de précision
                                val nouveauMontantString = String.format("%.2f", 
                                    BigDecimal.valueOf(nouveauMontant)
                                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                                        .toDouble()
                                )
                                onValueChange(null, nouveauMontantString, null, null, null, null, null)
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
                        onClick = { onValueChange(TypeObjectif.Mensuel, null, null, null, null, null, null) }, 
                        modifier = Modifier.weight(1f)
                    )
                    BoutonTypeObjectif(
                        libelle = "2 semaines", 
                        estSelectionne = formState.type == TypeObjectif.Bihebdomadaire, 
                        onClick = { onValueChange(TypeObjectif.Bihebdomadaire, null, null, null, null, null, null) }, 
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoutonTypeObjectif(
                        libelle = "Échéance", 
                        estSelectionne = formState.type == TypeObjectif.Echeance, 
                        onClick = { onValueChange(TypeObjectif.Echeance, null, null, null, null, null, null) }, 
                        modifier = Modifier.weight(1f)
                    )
                    BoutonTypeObjectif(
                        libelle = "Annuel", 
                        estSelectionne = formState.type == TypeObjectif.Annuel, 
                        onClick = { onValueChange(TypeObjectif.Annuel, null, null, null, null, null, null) }, 
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Configuration spécifique selon le type d'objectif
                when (formState.type) {
                    TypeObjectif.Annuel -> {
                        val context = LocalContext.current

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Date de début de l'objectif annuel : ${formState.date?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it) } ?: "Aujourd'hui"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = {
                                    val calendar = Calendar.getInstance()
                                    // Si pas de date définie, utiliser aujourd'hui
                                    if (formState.date != null) {
                                        calendar.time = formState.date
                                    }
                                    // Si pas de date, garder aujourd'hui (pas besoin de +12 mois)

                                    val datePickerDialog = DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            val selectedCalendar = Calendar.getInstance()
                                            selectedCalendar.set(year, month, dayOfMonth)
                                            onValueChange(null, null, selectedCalendar.time, null, null, null, null)
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    )
                                    datePickerDialog.show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CalendarToday, "Choisir date de début")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choisir la date de début (défaut: aujourd'hui)")
                            }
                        }
                    }
                    TypeObjectif.Mensuel -> {
                        SelecteurJourMois(
                            jourSelectionne = formState.jour,
                            onJourSelected = { onValueChange(null, null, null, it, null, null, null) }
                        )
                    }
                    TypeObjectif.Bihebdomadaire -> {
                        val context = LocalContext.current

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SelecteurJourSemaine(
                                jourSelectionne = formState.jour,
                                onJourSelected = { onValueChange(null, null, null, it, null, null, null) }
                            )
                            Text(
                                text = "Date de début : ${formState.date?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it) } ?: "Non définie"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = {
                                    val calendar = Calendar.getInstance()
                                    formState.date?.let { calendar.time = it }

                                    val datePickerDialog = DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            val selectedCalendar = Calendar.getInstance()
                                            selectedCalendar.set(year, month, dayOfMonth, 0, 0, 0)
                                            selectedCalendar.set(Calendar.MILLISECOND, 0)
                                            onValueChange(null, null, selectedCalendar.time, null, null, null, null)
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    )
                                    datePickerDialog.show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CalendarToday, "Choisir date de début")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choisir une date de début")
                            }
                        }
                    }
                    TypeObjectif.Echeance -> {
                        val context = LocalContext.current

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // 🆕 SÉLECTEUR DE DATE DE DÉBUT
                            Text(
                                text = "Date de début : ${formState.dateDebut?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it) } ?: "Non définie"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = {
                                    val calendar = Calendar.getInstance()
                                    formState.dateDebut?.let { calendar.time = it }

                                    val datePickerDialog = DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            val selectedCalendar = Calendar.getInstance()
                                            selectedCalendar.set(year, month, dayOfMonth, 0, 0, 0)
                                            selectedCalendar.set(Calendar.MILLISECOND, 0)
                                            onValueChange(null, null, null, null, null, null, selectedCalendar.time)
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    )
                                    datePickerDialog.show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CalendarToday, "Choisir date de début")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choisir une date de début")
                            }
                            
                            // 🆕 SÉLECTEUR DE DATE DE FIN
                            Text(
                                text = "Date de fin : ${formState.dateFin?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it) } ?: "Non définie"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = {
                                    val calendar = Calendar.getInstance()
                                    formState.dateFin?.let { calendar.time = it }

                                    val datePickerDialog = DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            val selectedCalendar = Calendar.getInstance()
                                            selectedCalendar.set(year, month, dayOfMonth, 0, 0, 0)
                                            selectedCalendar.set(Calendar.MILLISECOND, 0)
                                            onValueChange(null, null, null, null, null, selectedCalendar.time, null)
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    )
                                    // Empêcher la sélection de dates passées pour une échéance
                                    datePickerDialog.datePicker.minDate = System.currentTimeMillis()
                                    datePickerDialog.show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CalendarToday, "Choisir date de fin")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choisir une date de fin")
                            }
                            
                            // 🆕 CASE À COCHER POUR LE RESET APRÈS ÉCHÉANCE
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = formState.resetApresEcheance,
                                    onCheckedChange = { checked ->
                                        onValueChange(null, null, null, null, checked, null, null)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Renouveler automatiquement après échéance",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
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
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = formState.type != null && (formState.montant.toDoubleOrNull()?.let { it > 0 } ?: false)
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