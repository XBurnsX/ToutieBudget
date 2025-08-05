package com.xburnsx.toutiebudget.ui.cartes_credit.composants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel
import kotlin.math.abs

@Composable
fun CalculateurPaiement(
    carte: CompteCredit,
    onCalculerPlan: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var paiementMensuelCentimes by remember { mutableStateOf(0L) }
    var erreurPaiement by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C2C2E)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Calculateur de remboursement",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Information sur la dette actuelle
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dette à rembourser :",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = MoneyFormatter.formatAmount(abs(carte.solde)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Champ de saisie du paiement mensuel avec ChampUniversel
            ChampUniversel(
                valeur = paiementMensuelCentimes,
                onValeurChange = { nouveauMontant ->
                    paiementMensuelCentimes = nouveauMontant
                    val montantEuros = nouveauMontant / 100.0
                    erreurPaiement = validerPaiementMensuel(montantEuros.toString(), carte)
                },
                libelle = "Paiement mensuel souhaité",
                utiliserClavier = true,
                isMoney = true,
                icone = Icons.Default.AttachMoney,
                estObligatoire = false,
                couleurValeur = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            // Afficher l'erreur si elle existe
            if (erreurPaiement != null) {
                Text(
                    text = erreurPaiement!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Boutons d'actions rapides
            Column {
                Text(
                    text = "Suggestions rapides :",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val paiementMinimum = calculerPaiementMinimum(carte)
                    val paiement5pourcent = abs(carte.solde) * 0.05
                    val paiement10pourcent = abs(carte.solde) * 0.10

                    SuggestionChip(
                        onClick = {
                            paiementMensuelCentimes = (paiementMinimum * 100).toLong()
                            erreurPaiement = null
                        },
                        label = { Text("Minimum", fontSize = 12.sp) }, // Ajouté .sp
                        modifier = Modifier.weight(1f)
                    )

                    SuggestionChip(
                        onClick = {
                            paiementMensuelCentimes = (paiement5pourcent * 100).toLong()
                            erreurPaiement = null
                        },
                        label = { Text("5% dette", fontSize = 12.sp) }, // Ajouté .sp
                        modifier = Modifier.weight(1f)
                    )

                    SuggestionChip(
                        onClick = {
                            paiementMensuelCentimes = (paiement10pourcent * 100).toLong()
                            erreurPaiement = null
                        },
                        label = { Text("10% dette", fontSize = 12.sp) }, // Ajouté .sp
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bouton pour calculer le plan
            Button(
                onClick = {
                    val montant = paiementMensuelCentimes / 100.0
                    if (montant != null && erreurPaiement == null) {
                        onCalculerPlan(montant)
                    }
                },
                enabled = paiementMensuelCentimes > 0 && erreurPaiement == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Timeline, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Calculer le plan de remboursement")
            }

            // Informations supplémentaires si un montant valide est saisi
            val montantSaisi = paiementMensuelCentimes / 100.0
            if (montantSaisi != null && erreurPaiement == null) {
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        val tempsEstime = calculerTempsRemboursement(carte, montantSaisi)
                        val interetsTotal = calculerInteretsTotal(carte, montantSaisi, tempsEstime)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Temps de remboursement :",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = if (tempsEstime != null) "$tempsEstime mois" else "Impossible",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (interetsTotal != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Intérêts totaux :",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = MoneyFormatter.formatAmount(interetsTotal), // Changé CurrencyFormatter vers MoneyFormatter
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun validerPaiementMensuel(valeur: String, carte: CompteCredit): String? {
    if (valeur.isBlank()) return null

    val montant = valeur.toDoubleOrNull()
    return when {
        montant == null -> "Montant invalide"
        montant <= 0 -> "Le montant doit être positif"
        montant < calculerPaiementMinimum(carte) -> "Le montant doit être au moins égal au paiement minimum (${MoneyFormatter.formatAmount(calculerPaiementMinimum(carte))})" // Changé CurrencyFormatter vers MoneyFormatter
        else -> null
    }
}

private fun calculerPaiementMinimum(carte: CompteCredit): Double {
    val dette = abs(carte.solde)
    val taux = carte.tauxInteret ?: 0.0 // Changé interet vers tauxInteret
    val tauxMensuel = taux / 100.0 / 12.0
    val interetsMensuels = dette * tauxMensuel

    // Paiement minimum = 2% du solde ou 25€, le plus élevé des deux, plus les intérêts
    val paiementBase = maxOf(dette * 0.02, 25.0)
    return paiementBase + interetsMensuels
}

private fun calculerTempsRemboursement(carte: CompteCredit, paiementMensuel: Double): Int? {
    val dette = abs(carte.solde)
    val taux = carte.tauxInteret ?: 0.0 // Changé interet vers tauxInteret
    val tauxMensuel = taux / 100.0 / 12.0

    if (dette <= 0) return 0
    if (paiementMensuel <= dette * tauxMensuel) return null // Impossible à rembourser

    if (tauxMensuel == 0.0) {
        return kotlin.math.ceil(dette / paiementMensuel).toInt()
    }

    // Formule mathématique pour calculer le nombre de paiements
    val numerateur = kotlin.math.ln(1 + (dette * tauxMensuel) / paiementMensuel)
    val denominateur = kotlin.math.ln(1 + tauxMensuel)

    return kotlin.math.ceil(numerateur / denominateur).toInt()
}

private fun calculerInteretsTotal(carte: CompteCredit, paiementMensuel: Double, tempsMois: Int?): Double? {
    if (tempsMois == null) return null

    val dette = abs(carte.solde)
    val totalPaiements = paiementMensuel * tempsMois

    return maxOf(0.0, totalPaiements - dette)
}
