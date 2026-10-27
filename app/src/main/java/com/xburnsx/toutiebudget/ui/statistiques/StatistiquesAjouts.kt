package com.xburnsx.toutiebudget.ui.statistiques

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.utils.MoneyFormatter

/** SA1 : tous les comptes, ou une sélection. Les transferts restent identifiés comme tels. */
@Composable
fun FiltreComptes(uiState: StatistiquesUiState, viewModel: StatistiquesViewModel) {
    if (uiState.comptesDisponibles.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = uiState.comptesFiltres.isEmpty(),
            onClick = { viewModel.filtrerComptes(emptySet()) },
            label = { Text("Tous les comptes") }
        )
        uiState.comptesDisponibles.forEach { (id, nom) ->
            val choisi = id in uiState.comptesFiltres
            FilterChip(
                selected = choisi,
                onClick = { viewModel.filtrerComptes(if (choisi) uiState.comptesFiltres - id else uiState.comptesFiltres + id) },
                label = { Text(nom, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

/** SA8 : exporter en CSV la période et les filtres affichés (fichier choisi par l'utilisateur). */
@Composable
fun BoutonExportCsv(uiState: StatistiquesUiState, viewModel: StatistiquesViewModel) {
    val contexte = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    val creer = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val csv = viewModel.exporterCsv()
        message = if (csv == null) {
            "Export impossible : calcul incomplet."
        } else runCatching {
            contexte.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                ?: error("Fichier inaccessible")
            "Rapport exporté."
        }.getOrElse { "Export impossible : ${it.message}" }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            enabled = !uiState.calculIncomplet && uiState.transactionsPeriode.isNotEmpty(),
            onClick = {
                val nom = (uiState.periode?.label ?: "statistiques").replace(' ', '-').lowercase()
                creer.launch("toutiebudget-$nom.csv")
            }
        ) { Text("Exporter CSV") }
        message?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}

private fun formatVariation(c: ReglesStatistiques.Comparaison?): String {
    if (c?.reference == null || c.ecart == null) return "base indisponible"
    val signe = if (c.ecart >= 0) "+" else ""
    val pct = c.pourcentage?.let { " (" + (if (it >= 0) "+" else "") + String.format("%.0f", it) + " %)" } ?: " (base nulle)"
    return "$signe${MoneyFormatter.formatAmount(c.ecart)}$pct vs ${MoneyFormatter.formatAmount(c.reference)}"
}

/** SA3 : comparer au mois précédent et à la même période l'an dernier, à jours égaux. */
@Composable
fun CarteComparaisons(uiState: StatistiquesUiState) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Comparaisons", style = MaterialTheme.typography.titleMedium)
            listOf(
                "Dépenses vs période précédente" to uiState.comparaisonDepensesPrecedente,
                "Revenus vs période précédente" to uiState.comparaisonRevenusPrecedente,
                "Dépenses vs l'an dernier" to uiState.comparaisonDepensesAnnee,
                "Revenus vs l'an dernier" to uiState.comparaisonRevenusAnnee
            ).forEach { (libelle, comparaison) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(libelle, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(formatVariation(comparaison), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
            Text(
                "Un mois en cours est comparé au même nombre de jours.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** SA4 : catégorie -> ses enveloppes -> transactions (fractions comprises). */
@Composable
fun CarteCategories(uiState: StatistiquesUiState, viewModel: StatistiquesViewModel) {
    if (uiState.depensesParCategorie.isEmpty()) return
    var ouverte by remember { mutableStateOf<String?>(null) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Dépenses par catégorie", style = MaterialTheme.typography.titleMedium)
            uiState.depensesParCategorie.forEach { categorie ->
                Row(
                    Modifier.fillMaxWidth().clickable { ouverte = if (ouverte == categorie.id) null else categorie.id }.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text((if (ouverte == categorie.id) "▾ " else "▸ ") + categorie.label, modifier = Modifier.weight(1f))
                    Text(
                        "${MoneyFormatter.formatAmount(categorie.montant)} · ${String.format("%.0f", categorie.pourcentage * 100)} %",
                        fontWeight = FontWeight.Medium
                    )
                }
                if (ouverte == categorie.id) {
                    uiState.enveloppesParCategorie[categorie.id].orEmpty().forEach { enveloppe ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { viewModel.ouvrirModalTransactionsPourEnveloppe(enveloppe.id) }
                                .padding(start = 20.dp, top = 4.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(enveloppe.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(MoneyFormatter.formatAmount(enveloppe.montant), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

/** SA7 : prévu (assigné) vs réel par enveloppe ; un achat par carte n'est pas compté deux fois. */
@Composable
fun CartePrevuReel(uiState: StatistiquesUiState) {
    if (uiState.prevuReel.isEmpty()) return
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Budget prévu vs réel", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth()) {
                Text("Enveloppe", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.4f))
                Text("Prévu", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text("Réel", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text("Écart", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            }
            uiState.prevuReel.take(15).forEach { (nom, ligne) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(nom, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.4f))
                    Text(MoneyFormatter.formatAmount(ligne.prevu), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(MoneyFormatter.formatAmount(ligne.reel), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(
                        MoneyFormatter.formatAmount(ligne.ecart),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ligne.ecart < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** SA6 : courbe des valeurs nettes réellement constatées ; aucune tendance inventée. */
@Composable
fun CartePatrimoine(uiState: StatistiquesUiState) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Évolution du patrimoine", style = MaterialTheme.typography.titleMedium)
            val points = uiState.historiquePatrimoine
            if (points.size < 2) {
                Text(
                    "Historique en construction : la valeur nette est notée chaque jour où les statistiques du mois " +
                        "courant sont calculées. Les mois passés ne sont pas reconstitués.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            val couleur = MaterialTheme.colorScheme.primary
            val valeurs = points.map { it.second }
            val min = valeurs.min()
            val max = valeurs.max()
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                // ST12 : valeurs identiques -> ligne au milieu, jamais de division par zéro
                val etendue = (max - min).takeIf { it > 0.0 } ?: 1.0
                val pas = size.width / (points.size - 1)
                val coordonnees = valeurs.mapIndexed { i, v ->
                    val y = if (max == min) size.height / 2 else size.height - ((v - min) / etendue * size.height).toFloat()
                    Offset(i * pas, y)
                }
                coordonnees.zipWithNext { a, b -> drawLine(couleur, a, b, strokeWidth = 4f) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(points.first().first, style = MaterialTheme.typography.labelSmall)
                Text(MoneyFormatter.formatAmount(valeurs.last()), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                Text(points.last().first, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
