// chemin/simule: /ui/budget/composants/PrevisionsDialog.kt
// A7 : à la demande, combien il reste à prévoir pour les objectifs avant une date (paie) ou la fin du mois.

package com.xburnsx.toutiebudget.ui.budget.composants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
import com.xburnsx.toutiebudget.ui.budget.CategorieEnveloppesUi
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private data class LignePrevision(val enveloppe: EnveloppeUi, val echeance: Date)

private fun finDuMois(mois: Date): Date = Calendar.getInstance().apply {
    time = mois
    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
}.time

/**
 * Date à laquelle le versement recommandé du mois doit être prêt :
 * jour d'objectif mensuel, date d'échéance si elle tombe dans le mois, sinon la fin du mois.
 */
private fun echeanceDansLeMois(enveloppe: EnveloppeUi, mois: Date): Date {
    val fin = finDuMois(mois)
    val texte = enveloppe.dateObjectif ?: return fin
    val cal = Calendar.getInstance().apply { time = mois }

    if (enveloppe.typeObjectif == TypeObjectif.Mensuel) {
        val jour = texte.toIntOrNull()?.takeIf { it in 1..31 } ?: return fin
        cal.set(Calendar.DAY_OF_MONTH, jour.coerceAtMost(cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
        return cal.time
    }

    val date = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }.parse(texte)
    } catch (_: Exception) {
        null
    } ?: return fin
    val c = Calendar.getInstance().apply { time = date }
    return if (c.get(Calendar.YEAR) == cal.get(Calendar.YEAR) && c.get(Calendar.MONTH) == cal.get(Calendar.MONTH)) date else fin
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrevisionsDialog(
    categories: List<CategorieEnveloppesUi>,
    moisSelectionne: Date,
    onFermer: () -> Unit
) {
    val fin = finDuMois(moisSelectionne)
    var limite by remember { mutableStateOf<Date?>(null) } // null = fin du mois
    var choisirDate by remember { mutableStateOf(false) }
    var erreurDate by remember { mutableStateOf<String?>(null) }
    val formatJour = remember { SimpleDateFormat("d MMM", Locale.CANADA_FRENCH) }

    val horizon = limite ?: fin
    val lignes = categories.flatMap { it.enveloppes }
        .filter { !it.estArchive && kotlin.math.round(it.versementRecommande * 100).toLong() > 0L }
        .map { LignePrevision(it, echeanceDansLeMois(it, moisSelectionne)) }
        .filter { !it.echeance.after(horizon) }
        .sortedBy { it.echeance }
    val total = lignes.sumOf { it.enveloppe.versementRecommande }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("À prévoir pour les objectifs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = limite == null,
                        onClick = { limite = null; erreurDate = null },
                        label = { Text("Fin du mois") }
                    )
                    FilterChip(
                        selected = limite != null,
                        onClick = { choisirDate = true },
                        label = { Text(limite?.let { "Avant le ${formatJour.format(it)}" } ?: "Avant une date") }
                    )
                }

                erreurDate?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                if (lignes.isEmpty()) {
                    Text("Rien à prévoir d'ici là.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(lignes, key = { it.enveloppe.id }) { ligne ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ligne.enveloppe.nom)
                                    Text(
                                        formatJour.format(ligne.echeance),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Text(MoneyFormatter.formatAmount(ligne.enveloppe.versementRecommande))
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Total", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(MoneyFormatter.formatAmount(total), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onFermer) { Text("Fermer") } }
    )

    if (choisirDate) {
        val etat = rememberDatePickerState(
            initialSelectedDateMillis = (limite ?: moisSelectionne).time
        )
        DatePickerDialog(
            onDismissRequest = { choisirDate = false },
            confirmButton = {
                TextButton(onClick = {
                    etat.selectedDateMillis?.let { millis ->
                        // Le sélecteur donne minuit UTC : garder le jour choisi, en fin de journée locale
                        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
                        val dateChoisie = Calendar.getInstance().apply {
                            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
                            set(Calendar.MILLISECOND, 999)
                        }
                        val moisAffiche = Calendar.getInstance().apply { time = moisSelectionne }
                        if (dateChoisie.get(Calendar.YEAR) != moisAffiche.get(Calendar.YEAR) ||
                            dateChoisie.get(Calendar.MONTH) != moisAffiche.get(Calendar.MONTH)
                        ) {
                            erreurDate = "Choisis une date dans le mois affiché sur ton budget."
                        } else {
                            erreurDate = null
                            limite = dateChoisie.time
                        }
                    }
                    choisirDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { choisirDate = false }) { Text("Annuler") } }
        ) {
            DatePicker(state = etat)
        }
    }
}
