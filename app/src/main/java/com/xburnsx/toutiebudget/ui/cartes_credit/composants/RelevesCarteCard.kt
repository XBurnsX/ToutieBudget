package com.xburnsx.toutiebudget.ui.cartes_credit.composants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.ReleveCarte
import com.xburnsx.toutiebudget.domain.services.MoteurCarteCredit
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.util.concurrent.TimeUnit

/**
 * Relevés officiels (CC6) et historique par relevé (CC11) : intérêts FACTURÉS (réels)
 * à côté des intérêts ESTIMÉS pour la même période, et cumul réel par année.
 */
@Composable
fun RelevesCarteCard(
    carte: CompteCredit,
    onAjouter: () -> Unit,
    onModifier: (ReleveCarte) -> Unit,
    onComptabiliserInterets: (String) -> Unit
) {
    val releves = carte.details.relevesTries
    var confirmerInterets by remember { mutableStateOf<ReleveCarte?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Relevés officiels",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onAjouter) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter un relevé", tint = Color.White)
                }
            }

            if (releves.isEmpty()) {
                Text(
                    "Ajoutez votre dernier relevé (solde, minimum, échéance, intérêts facturés) : " +
                        "ces montants officiels passent avant les estimations.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            releves.forEachIndexed { index, releve ->
                val precedent = releves.getOrNull(index + 1)
                val estime = precedent?.let { interetsEstimesPeriode(carte, it, releve) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onModifier(releve) }
                        .padding(vertical = 4.dp)
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Relevé du ${releve.dateEmission}", color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(MoneyFormatter.formatAmount(releve.soldeOfficiel), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        buildString {
                            releve.minimumOfficiel?.let { append("Minimum ${MoneyFormatter.formatAmount(it)}") }
                            releve.dateEcheance?.let { append(if (isNotEmpty()) " · " else ""); append("échéance $it") }
                        },
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Intérêts facturés ${MoneyFormatter.formatAmount(releve.interetsFactures)}" +
                            (estime?.let { " · estimés ${MoneyFormatter.formatAmount(it)}" } ?: "") +
                            (if (releve.fraisFactures > 0.005) " · frais ${MoneyFormatter.formatAmount(releve.fraisFactures)}" else "") +
                            (if (releve.paiementsRecus > 0.005) " · paiements ${MoneyFormatter.formatAmount(releve.paiementsRecus)}" else ""),
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (releve.interetsFactures > 0.005 && !releve.interetsComptabilises) {
                        TextButton(onClick = { confirmerInterets = releve }) {
                            Text("Ajouter ces intérêts au solde de la carte")
                        }
                    } else if (releve.interetsComptabilises) {
                        Text("Intérêts déjà ajoutés au solde.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (index < releves.lastIndex) HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
            }

            // CC11 : cumul des intérêts RÉELLEMENT facturés par année
            val parAnnee = releves.groupBy { it.dateEmission.take(4) }.mapValues { (_, r) -> r.sumOf { it.interetsFactures } }
                .filterValues { it > 0.005 }
            if (parAnnee.isNotEmpty()) {
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                Text("Intérêts facturés par année", color = Color.White, fontWeight = FontWeight.Medium)
                parAnnee.toSortedMap(compareByDescending { it }).forEach { (annee, total) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(annee, color = Color.LightGray, modifier = Modifier.weight(1f))
                        Text(MoneyFormatter.formatAmount(total), color = Color.White)
                    }
                }
            }
        }
    }

    confirmerInterets?.let { releve ->
        AlertDialog(
            onDismissRequest = { confirmerInterets = null },
            title = { Text("Ajouter les intérêts facturés ?") },
            text = {
                Text(
                    "La dette de la carte augmentera de ${MoneyFormatter.formatAmount(releve.interetsFactures)}, une seule fois.\n\n" +
                        "Ne le faites pas si le solde saisi dans l'app inclut déjà ces intérêts " +
                        "(par exemple s'il a été copié du solde en temps réel de la banque)."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onComptabiliserInterets(releve.id)
                    confirmerInterets = null
                }) { Text("Ajouter") }
            },
            dismissButton = { TextButton(onClick = { confirmerInterets = null }) { Text("Annuler") } }
        )
    }
}

/** Estimation pour la période entre deux relevés à partir du solde officiel précédent (comparaison seulement). */
private fun interetsEstimesPeriode(carte: CompteCredit, precedent: ReleveCarte, releve: ReleveCarte): Double? {
    val debut = MoteurCarteCredit.lireDate(precedent.dateEmission) ?: return null
    val fin = MoteurCarteCredit.lireDate(releve.dateEmission) ?: return null
    val jours = TimeUnit.MILLISECONDS.toDays(fin.time - debut.time).toInt()
    if (jours <= 0) return null
    return MoteurCarteCredit.arrondi(
        MoteurCarteCredit.interetsEstimes(precedent.soldeOfficiel, MoteurCarteCredit.tauxEffectif(carte, debut), jours)
    )
}
