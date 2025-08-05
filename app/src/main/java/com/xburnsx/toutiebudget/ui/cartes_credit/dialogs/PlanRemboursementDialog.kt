package com.xburnsx.toutiebudget.ui.cartes_credit.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.repositories.PaiementPlanifie
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable
fun PlanRemboursementDialog(
    carte: CompteCredit,
    planRemboursement: List<PaiementPlanifie>,
    paiementMensuel: Double,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // En-tête
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Plan de remboursement",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = carte.nom,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Résumé du plan
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Dette initiale :", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                MoneyFormatter.formatAmount(abs(carte.solde)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Paiement mensuel :", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                MoneyFormatter.formatAmount(paiementMensuel),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Durée totale :", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${planRemboursement.size} mois",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        val totalInterets = planRemboursement.sumOf { it.montantInterets }
                        val totalPaiements = planRemboursement.sumOf { it.paiementTotal }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total intérêts :", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                MoneyFormatter.formatAmount(totalInterets),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Coût total :", style = MaterialTheme.typography.titleMedium)
                            Text(
                                MoneyFormatter.formatAmount(totalPaiements),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // En-tête du tableau
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Mois",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "Paiement",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.2f)
                        )
                        Text(
                            "Intérêts",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.2f)
                        )
                        Text(
                            "Solde",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Liste des paiements
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(planRemboursement) { index, paiement ->
                        PaiementItem(
                            paiement = paiement,
                            dateFormat = dateFormat,
                            estDernier = index == planRemboursement.size - 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bouton fermer
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Fermer")
                }
            }
        }
    }
}

@Composable
private fun PaiementItem(
    paiement: PaiementPlanifie,
    dateFormat: SimpleDateFormat,
    estDernier: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (estDernier)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mois
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "#${paiement.mois}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = dateFormat.format(paiement.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Paiement total
            Text(
                text = MoneyFormatter.formatAmount(paiement.paiementTotal),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1.2f)
            )

            // Intérêts
            Text(
                text = MoneyFormatter.formatAmount(paiement.montantInterets),
                style = MaterialTheme.typography.bodyMedium,
                color = if (paiement.montantInterets > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1.2f)
            )

            // Solde restant
            Text(
                text = if (paiement.soldeRestant <= 0.01) "Payé ✓" else MoneyFormatter.formatAmount(paiement.soldeRestant),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (paiement.soldeRestant <= 0.01) FontWeight.Bold else FontWeight.Normal,
                color = if (paiement.soldeRestant <= 0.01) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1.2f)
            )
        }
    }
}
