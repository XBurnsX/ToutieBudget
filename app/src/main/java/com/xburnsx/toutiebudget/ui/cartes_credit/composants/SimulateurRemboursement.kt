package com.xburnsx.toutiebudget.ui.cartes_credit.composants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.ui.cartes_credit.StatistiquesCarteCredit
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import kotlin.math.abs
import kotlin.math.min

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
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
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
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
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
        val economie = scenarioMin.coutTotal - scenarioRec.coutTotal
        
        // Liste de conseils utiles qui apparaissent aléatoirement
        val conseils = listOf(
            ConseilsUtile(
                titre = "💡 Conseil d'urgence",
                message = "Payer plus que le minimum réduit drastiquement les intérêts !",
                condition = { true }
            ),
            ConseilsUtile(
                titre = "💡 Conseil de durée",
                message = "Avec le paiement minimum, vous paierez pendant ${scenarioMin.dureeMois} mois. Augmentez vos paiements pour accélérer !",
                condition = { scenarioMin.dureeMois > 12 }
            ),
            ConseilsUtile(
                titre = "💡 Conseil d'intérêts",
                message = "Vous payez ${MoneyFormatter.formatAmount(scenarioMin.interetsTotal)} en intérêts avec le minimum. Réduisez ce montant !",
                condition = { scenarioMin.interetsTotal > 100 }
            ),
            ConseilsUtile(
                titre = "💡 Conseil d'économie",
                message = "En payant ${MoneyFormatter.formatAmount(scenarioRec.paiementMensuel)} au lieu du minimum, vous économiserez ${MoneyFormatter.formatAmount(economie)} !",
                condition = { economie > 50 }
            ),
            ConseilsUtile(
                titre = "💡 Conseil de frais",
                message = "Vos frais mensuels totalisent ${MoneyFormatter.formatAmount(scenarioMin.fraisTotal / scenarioMin.dureeMois)}/mois. Considérez une carte sans frais !",
                condition = { scenarioMin.fraisTotal > 0 }
            ),
            ConseilsUtile(
                titre = "💡 Conseil de stratégie",
                message = "Priorisez le remboursement de cette carte si son taux d'intérêt est plus élevé que vos autres dettes.",
                condition = { true }
            )
        )
        
        // Sélectionner un conseil aléatoire qui respecte sa condition
        val conseilsApplicables = conseils.filter { it.condition() }
        val conseilAleatoire = conseilsApplicables.randomOrNull() ?: conseils.first()

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
                        text = conseilAleatoire.titre,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = conseilAleatoire.message,
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
    val coutTotal: Double,
    val fraisTotal: Double
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
        val fraisTotalMin = carte.calculerFraisTotaux(dureeMin)
        scenarios.add(
            ScenarioRemboursement(
                nom = "Minimum",
                type = TypeScenario.MINIMUM,
                paiementMensuel = paiementMin,
                dureeMois = dureeMin,
                interetsTotal = interetsMin,
                coutTotal = dette + interetsMin + fraisTotalMin,
                fraisTotal = fraisTotalMin
            )
        )
    }

    // Scénario recommandé (5% de la dette)
    val paiementRec = maxOf(dette * 0.05, paiementMin)
    val dureeRec = calculerDureeRemboursement(carte, paiementRec)
    if (dureeRec != null) {
        val interetsRec = calculerInteretsTotal(carte, paiementRec, dureeRec)
        val fraisTotalRec = carte.calculerFraisTotaux(dureeRec)
        scenarios.add(
            ScenarioRemboursement(
                nom = "Recommandé",
                type = TypeScenario.RECOMMANDE,
                paiementMensuel = paiementRec,
                dureeMois = dureeRec,
                interetsTotal = interetsRec,
                coutTotal = dette + interetsRec + fraisTotalRec,
                fraisTotal = fraisTotalRec
            )
        )
    }

    // Scénario agressif (10% de la dette)
    val paiementAgg = maxOf(dette * 0.10, paiementMin)
    val dureeAgg = calculerDureeRemboursement(carte, paiementAgg)
    if (dureeAgg != null) {
        val interetsAgg = calculerInteretsTotal(carte, paiementAgg, dureeAgg)
        val fraisTotalAgg = carte.calculerFraisTotaux(dureeAgg)
        scenarios.add(
            ScenarioRemboursement(
                nom = "Agressif",
                type = TypeScenario.AGRESSIF,
                paiementMensuel = paiementAgg,
                dureeMois = dureeAgg,
                interetsTotal = interetsAgg,
                coutTotal = dette + interetsAgg + fraisTotalAgg,
                fraisTotal = fraisTotalAgg
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
    
    // Calcul mois par mois pour obtenir la vraie durée
    var soldeRestant = dette
    var mois = 0
    val maxMois = 600 // Limite de 50 ans pour éviter les boucles infinies
    
    while (soldeRestant > 0.01 && mois < maxMois) {
        mois++
        
        // Calculer les intérêts du mois
        val interetsMois = soldeRestant * tauxMensuel
        
        // Calculer les frais du mois (peuvent varier selon la durée)
        val fraisMois = carte.calculerFraisMensuelsMoyens(mois)
        
        // Montant disponible pour le capital
        val paiementDisponible = paiementMensuel - fraisMois
        val capitalMois = min(paiementDisponible - interetsMois, soldeRestant)
        
        // Vérifier si le paiement est suffisant
        if (paiementDisponible <= interetsMois) {
            return null // Le paiement ne couvre même pas les intérêts
        }
        
        // Mettre à jour le solde
        soldeRestant -= capitalMois
    }
    
    return if (mois >= maxMois) null else mois
}

private fun calculerInteretsTotal(carte: CompteCredit, paiementMensuel: Double, dureeMois: Int): Double {
    val dette = abs(carte.solde)
    val taux = carte.tauxInteret ?: 0.0
    val tauxMensuel = taux / 100.0 / 12.0
    val fraisMensuelsMoyens = carte.calculerFraisMensuelsMoyens(dureeMois)
    
    if (tauxMensuel == 0.0) {
        // Sans intérêts, retourner 0
        return 0.0
    }
    
    // Calculer les intérêts réels payés mois par mois
    var soldeRestant = dette
    var totalInterets = 0.0
    
    for (mois in 1..dureeMois) {
        if (soldeRestant <= 0.01) break
        
        val interetsMois = soldeRestant * tauxMensuel
        val paiementDisponible = paiementMensuel - fraisMensuelsMoyens
        val capitalMois = min(paiementDisponible - interetsMois, soldeRestant)
        
        totalInterets += interetsMois
        soldeRestant -= capitalMois
    }
    
    return totalInterets
}

private data class ConseilsUtile(
    val titre: String,
    val message: String,
    val condition: () -> Boolean
)

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
    
    // Test du calcul des intérêts
    val testInterets = calculerInteretsTotal(carteCredit, 500.0, 6)
    println("Test intérêts pour 500€/mois sur 6 mois: $testInterets")
    
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
