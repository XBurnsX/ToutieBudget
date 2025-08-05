package com.xburnsx.toutiebudget.ui.cartes_credit.composants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.ui.cartes_credit.StatistiquesCarteCredit
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import kotlin.math.*

@Composable
fun SimulateurRemboursement(
    carte: CompteCredit,
    statistiques: StatistiquesCarteCredit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C2C2E)
        )
    ) {
        // Génération des scénarios
        val scenarios = genererScenarios(carte, statistiques)

        if (scenarios.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Simulation de remboursement",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Comparez différentes stratégies de paiement :",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(scenarios) { scenario ->
                        ScenarioCard(scenario = scenario)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Conseils basés sur les scénarios
                ConseilsRemboursement(scenarios = scenarios)
            }
        } else {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Simulation de remboursement",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A1A)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Aucune dette à rembourser",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenarioCard(scenario: ScenarioRemboursement) {
    Card(
        modifier = Modifier.width(200.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (scenario.type) {
                TypeScenario.MINIMUM -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                TypeScenario.RECOMMANDE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                TypeScenario.AGRESSIF -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (scenario.type) {
                        TypeScenario.MINIMUM -> Icons.Default.Speed
                        TypeScenario.RECOMMANDE -> Icons.Default.Star
                        TypeScenario.AGRESSIF -> Icons.Default.Bolt
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = when (scenario.type) {
                        TypeScenario.MINIMUM -> MaterialTheme.colorScheme.error
                        TypeScenario.RECOMMANDE -> MaterialTheme.colorScheme.primary
                        TypeScenario.AGRESSIF -> MaterialTheme.colorScheme.tertiary
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = scenario.nom,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            InfoScenario(
                label = "Paiement/mois",
                valeur = MoneyFormatter.formatAmount(scenario.paiementMensuel)
            )

            InfoScenario(
                label = "Durée",
                valeur = "${scenario.dureeMois} mois"
            )

            InfoScenario(
                label = "Intérêts totaux",
                valeur = MoneyFormatter.formatAmount(scenario.interetsTotal),
                couleur = MaterialTheme.colorScheme.error
            )

            InfoScenario(
                label = "Coût total",
                valeur = MoneyFormatter.formatAmount(scenario.coutTotal)
            )
        }
    }
}

@Composable
private fun InfoScenario(
    label: String,
    valeur: String,
    couleur: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = valeur,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = couleur
        )
    }
}

@Composable
private fun ConseilsRemboursement(scenarios: List<ScenarioRemboursement>) {
    val scenarioMin = scenarios.find { it.type == TypeScenario.MINIMUM }
    val scenarioRec = scenarios.find { it.type == TypeScenario.RECOMMANDE }

    if (scenarioMin != null && scenarioRec != null) {
        val economie = scenarioMin.interetsTotal - scenarioRec.interetsTotal

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "💡 Conseil",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "En payant ${MoneyFormatter.formatAmount(scenarioRec.paiementMensuel)} au lieu du minimum, vous économiserez ${MoneyFormatter.formatAmount(economie)} en intérêts !",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private data class ScenarioRemboursement(
    val nom: String,
    val type: TypeScenario,
    val paiementMensuel: Double,
    val dureeMois: Int,
    val interetsTotal: Double,
    val coutTotal: Double
)

private enum class TypeScenario {
    MINIMUM, RECOMMANDE, AGRESSIF
}

private fun genererScenarios(carte: CompteCredit, statistiques: StatistiquesCarteCredit): List<ScenarioRemboursement> {
    val dette = abs(carte.solde)
    if (dette <= 0) return emptyList()

    val scenarios = mutableListOf<ScenarioRemboursement>()

    // Scénario minimum
    val paiementMin = statistiques.paiementMinimum
    val dureeMin = calculerDureeRemboursement(carte, paiementMin)
    if (dureeMin != null && dureeMin <= 600) { // Limite raisonnable
        val interetsMin = calculerInteretsTotal(carte, paiementMin, dureeMin)
        scenarios.add(
            ScenarioRemboursement(
                nom = "Minimum",
                type = TypeScenario.MINIMUM,
                paiementMensuel = paiementMin,
                dureeMois = dureeMin,
                interetsTotal = interetsMin,
                coutTotal = dette + interetsMin
            )
        )
    }

    // Scénario recommandé (5% de la dette)
    val paiementRec = maxOf(dette * 0.05, paiementMin)
    val dureeRec = calculerDureeRemboursement(carte, paiementRec)
    if (dureeRec != null) {
        val interetsRec = calculerInteretsTotal(carte, paiementRec, dureeRec)
        scenarios.add(
            ScenarioRemboursement(
                nom = "Recommandé",
                type = TypeScenario.RECOMMANDE,
                paiementMensuel = paiementRec,
                dureeMois = dureeRec,
                interetsTotal = interetsRec,
                coutTotal = dette + interetsRec
            )
        )
    }

    // Scénario agressif (10% de la dette)
    val paiementAgg = maxOf(dette * 0.10, paiementMin)
    val dureeAgg = calculerDureeRemboursement(carte, paiementAgg)
    if (dureeAgg != null) {
        val interetsAgg = calculerInteretsTotal(carte, paiementAgg, dureeAgg)
        scenarios.add(
            ScenarioRemboursement(
                nom = "Agressif",
                type = TypeScenario.AGRESSIF,
                paiementMensuel = paiementAgg,
                dureeMois = dureeAgg,
                interetsTotal = interetsAgg,
                coutTotal = dette + interetsAgg
            )
        )
    }

    return scenarios
}

private fun calculerDureeRemboursement(carte: CompteCredit, paiementMensuel: Double): Int? {
    val dette = abs(carte.solde)
    val taux = carte.tauxInteret ?: 0.0
    val tauxMensuel = taux / 100.0 / 12.0

    if (dette <= 0) return 0
    if (paiementMensuel <= dette * tauxMensuel) return null

    if (tauxMensuel == 0.0) {
        return ceil(dette / paiementMensuel).toInt()
    }

    val numerateur = ln(1 + (dette * tauxMensuel) / paiementMensuel)
    val denominateur = ln(1 + tauxMensuel)

    return ceil(numerateur / denominateur).toInt()
}

private fun calculerInteretsTotal(carte: CompteCredit, paiementMensuel: Double, dureeMois: Int): Double {
    val dette = abs(carte.solde)
    val totalPaiements = paiementMensuel * dureeMois
    return maxOf(0.0, totalPaiements - dette)
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun SimulateurRemboursementPreview() {
    val carteCredit = CompteCredit(
        id = "1",
        utilisateurId = "user1",
        nom = "Carte Visa",
        soldeUtilise = -2500.0,
        couleur = "#2196F3",
        estArchive = false,
        ordre = 1,
        limiteCredit = 10000.0,
        tauxInteret = 19.99
    )
    
    val statistiques = StatistiquesCarteCredit(
        creditDisponible = 7500.0,
        tauxUtilisation = 0.25,
        interetsMensuels = 41.65,
        paiementMinimum = 541.65,
        tempsRemboursementMinimum = 5,
        totalInteretsAnnuels = 499.8
    )
    
    SimulateurRemboursement(
        carte = carteCredit,
        statistiques = statistiques
    )
}
