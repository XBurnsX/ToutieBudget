package com.xburnsx.toutiebudget.ui.cartes_credit.composants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.core.graphics.toColorInt

@Composable
fun CarteCreditDetailCard(
    carte: CompteCredit,
    statistiques: StatistiquesCarteCredit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C2C2E)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // En-tête avec nom et couleur
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = carte.nom,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = try {
                        Color(carte.couleur.toColorInt())
                    } catch (_: Exception) {
                        Color.White
                    }
                )
                
                Icon(
                    imageVector = Icons.Default.CreditCard,
                    contentDescription = "Carte de crédit",
                    tint = try {
                        Color(carte.couleur.toColorInt())
                    } catch (_: Exception) {
                        Color(0xFF2196F3)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Informations principales
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Solde utilisé
                Column {
                    Text(
                        text = "Solde utilisé",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = MoneyFormatter.formatAmount(abs(carte.solde)),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Limite de crédit
                Column {
                    Text(
                        text = "Limite",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = MoneyFormatter.formatAmount(carte.limiteCredit),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Crédit disponible
                Column {
                    Text(
                        text = "Disponible",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = MoneyFormatter.formatAmount(statistiques.creditDisponible),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Green,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Barre de progression pour l'utilisation
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Utilisation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = "${(statistiques.tauxUtilisation * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            statistiques.tauxUtilisation > 0.8 -> Color.Red
                            statistiques.tauxUtilisation > 0.6 -> Color.Yellow
                            else -> Color.Green
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LinearProgressIndicator(
                    progress = { statistiques.tauxUtilisation.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = when {
                        statistiques.tauxUtilisation > 0.8 -> Color.Red
                        statistiques.tauxUtilisation > 0.6 -> Color.Yellow
                        else -> Color.Green
                    },
                    trackColor = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Informations supplémentaires
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Taux d'intérêt
                Column {
                    Text(
                        text = "Taux d'intérêt",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        text = carte.tauxInteret?.let { "${it}%" } ?: "Non défini",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }

                // Paiement minimum
                Column {
                    Text(
                        text = "Paiement min.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        text = MoneyFormatter.formatAmount(statistiques.paiementMinimum),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }

                // Intérêts mensuels
                Column {
                    Text(
                        text = "Intérêts/mois",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        text = MoneyFormatter.formatAmount(statistiques.interetsMensuels),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Red
                    )
                }
            }


        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun CarteCreditDetailCardPreview() {
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
    
    CarteCreditDetailCard(
        carte = carteCredit,
        statistiques = statistiques
    )
}
