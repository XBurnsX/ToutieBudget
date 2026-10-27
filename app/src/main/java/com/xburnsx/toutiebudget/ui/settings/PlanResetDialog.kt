// A8 / BUD-01 : option des Paramètres pour refaire la répartition d'un mois.

package com.xburnsx.toutiebudget.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.domain.services.PlanResetService
import com.xburnsx.toutiebudget.ui.budget.composants.SelecteurMoisAnnee
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import kotlinx.coroutines.launch
import java.util.Date

@Composable
fun PlanResetDialog(onFermer: () -> Unit) {
    val service = remember { AppModule.providePlanResetService() }
    val scope = rememberCoroutineScope()

    var mois by remember { mutableStateOf(Date()) }
    var apercu by remember { mutableStateOf<PlanResetService.Apercu?>(null) }
    var choisies by remember { mutableStateOf<Set<String>>(emptySet()) }
    var chargement by remember { mutableStateOf(true) }
    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var confirmer by remember { mutableStateOf(false) }
    var termine by remember { mutableStateOf<String?>(null) }
    var tentative by remember { mutableStateOf(0) }

    LaunchedEffect(mois, tentative) {
        chargement = true
        erreur = null
        apercu = null
        choisies = emptySet()
        service.preparerApercu(mois)
            .onSuccess { a ->
                apercu = a
                choisies = a.lignes.map { it.allocationId }.toSet()
            }
            .onFailure { erreur = "Impossible de préparer l'aperçu : ${it.message}" }
        chargement = false
    }

    val lignesChoisies = apercu?.lignes?.filter { it.allocationId in choisies }.orEmpty()
    val parCompte = lignesChoisies.groupBy { it.compteId }.values.map { lignes ->
        lignes.first().nomCompte to lignes.sumOf { it.montant }
    }

    Dialog(
        onDismissRequest = { if (!enCours) onFermer() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Refaire la répartition",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onFermer, enabled = !enCours) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }
                Text(
                    "L'argent assigné dans le mois et pas encore dépensé retourne au « Prêt à placer » de son compte. " +
                        "Les transactions, le report des mois précédents et les autres mois ne changent pas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Box(modifier = Modifier.padding(vertical = 8.dp)) {
                    SelecteurMoisAnnee(moisSelectionne = mois, onMoisChange = { if (!enCours && !confirmer) mois = it })
                }

                when {
                    chargement -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    apercu == null -> Column(Modifier.weight(1f)) {
                        Text(erreur ?: "", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { tentative++ }) { Text("Réessayer") }
                    }
                    apercu!!.lignes.isEmpty() -> Column(Modifier.weight(1f)) {
                        Text("Aucun argent assigné pouvant être remis pour ce mois.")
                        if (apercu!!.ignorees.isNotEmpty()) {
                            Text("Provenance inconnue ou compte archivé : ${apercu!!.ignorees.joinToString()}",
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                    else -> LazyColumn(modifier = Modifier.weight(1f)) {
                        items(apercu!!.lignes, key = { it.allocationId }) { ligne ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !enCours) {
                                        choisies = if (ligne.allocationId in choisies) choisies - ligne.allocationId
                                        else choisies + ligne.allocationId
                                    }
                            ) {
                                Checkbox(
                                    checked = ligne.allocationId in choisies,
                                    onCheckedChange = null,
                                    enabled = !enCours
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ligne.nomEnveloppe)
                                    Text(
                                        "vers ${ligne.nomCompte}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Text(MoneyFormatter.formatAmount(ligne.montant))
                            }
                        }
                        if (apercu!!.ignorees.isNotEmpty()) {
                            item {
                                Text(
                                    "Laissées telles quelles (compte inconnu ou archivé) : ${apercu!!.ignorees.joinToString()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }

                if (parCompte.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    parCompte.forEach { (nomCompte, total) ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("Prêt à placer $nomCompte", modifier = Modifier.weight(1f))
                            Text("+ ${MoneyFormatter.formatAmount(total)}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (apercu != null) {
                    erreur?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                        TextButton(onClick = { tentative++ }, enabled = !enCours) { Text("Actualiser l'aperçu") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { confirmer = true },
                    enabled = !enCours && !chargement && erreur == null && lignesChoisies.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (enCours) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Remettre dans « Prêt à placer »")
                }
            }
        }
    }

    if (confirmer) {
        AlertDialog(
            onDismissRequest = { confirmer = false },
            title = { Text("Confirmer la nouvelle répartition") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${lignesChoisies.size} allocation(s) sélectionnée(s) dans ${lignesChoisies.map { it.enveloppeId }.distinct().size} enveloppe(s).")
                    parCompte.forEach { (nomCompte, total) ->
                        Text("$nomCompte : + ${MoneyFormatter.formatAmount(total)}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmer = false
                    val a = apercu ?: return@TextButton
                    val idsSelectionnes = choisies.toSet()
                    enCours = true
                    erreur = null
                    scope.launch {
                        service.appliquer(a, idsSelectionnes)
                            .onSuccess { faites ->
                                termine = "${faites.size} allocation(s) remises dans « Prêt à placer »."
                                tentative++
                            }
                            .onFailure {
                                erreur = it.message ?: "Le plan a changé : recharge l'aperçu avant de confirmer."
                            }
                        enCours = false
                    }
                }) { Text("Confirmer") }
            },
            dismissButton = { TextButton(onClick = { confirmer = false }) { Text("Annuler") } }
        )
    }

    termine?.let { message ->
        AlertDialog(
            onDismissRequest = { termine = null; onFermer() },
            title = { Text("Répartition remise à zéro") },
            text = { Text("$message Vous pouvez maintenant répartir l'argent à nouveau depuis la page Budget.") },
            confirmButton = { TextButton(onClick = { termine = null; onFermer() }) { Text("OK") } }
        )
    }
}
