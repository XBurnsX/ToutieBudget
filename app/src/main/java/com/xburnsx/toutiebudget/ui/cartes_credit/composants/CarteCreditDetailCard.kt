package com.xburnsx.toutiebudget.ui.cartes_credit.composants

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.ui.cartes_credit.StatistiquesCarteCredit
import com.xburnsx.toutiebudget.ui.budget.composants.toColor
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import kotlin.math.abs

@Composable
fun CarteCreditDetailCard(
    carte: CompteCredit,
    statistiques: StatistiquesCarteCredit,
    modifier: Modifier = Modifier
) {
    val couleurCarte = carte.couleur.toColor()
    val dette = abs(carte.solde)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C2C2E)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // En-tête avec nom et icône
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = carte.nom,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = couleurCarte
                    )
                    Text(
                        text = "Carte de crédit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Icon(
                    imageVector = Icons.Default.CreditCard,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = couleurCarte
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Informations principales
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem(
                    titre = "Dette actuelle",
                    valeur = MoneyFormatter.formatAmount(dette),
                    couleur = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )

                InfoItem(
                    titre = "Limite de crédit",
                    valeur = MoneyFormatter.formatAmount(carte.limiteCredit),
                    couleur = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Barre de progression de l'utilisation
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Taux d'utilisation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "${(statistiques.tauxUtilisation * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            statistiques.tauxUtilisation < 0.3 -> MaterialTheme.colorScheme.primary
                            statistiques.tauxUtilisation < 0.7 -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = { statistiques.tauxUtilisation.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = when {
                        statistiques.tauxUtilisation < 0.3 -> MaterialTheme.colorScheme.primary
                        statistiques.tauxUtilisation < 0.7 -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.error
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Statistiques secondaires
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem(
                    titre = "Crédit disponible",
                    valeur = MoneyFormatter.formatAmount(statistiques.creditDisponible),
                    couleur = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )

                InfoItem(
                    titre = "Intérêts/mois",
                    valeur = MoneyFormatter.formatAmount(statistiques.interetsMensuels),
                    couleur = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
            }

            if (carte.tauxInteret != null && carte.tauxInteret > 0) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InfoItem(
                        titre = "Taux d'intérêt",
                        valeur = "${carte.tauxInteret}% / an",
                        couleur = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f)
                    )

                    InfoItem(
                        titre = "Paiement minimum",
                        valeur = MoneyFormatter.formatAmount(statistiques.paiementMinimum),
                        couleur = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoItem(
    titre: String,
    valeur: String,
    couleur: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = valeur,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = couleur
        )
        Text(
            text = titre,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
