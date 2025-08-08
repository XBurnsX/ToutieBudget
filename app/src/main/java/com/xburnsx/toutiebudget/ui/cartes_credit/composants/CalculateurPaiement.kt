package com.xburnsx.toutiebudget.ui.cartes_credit.composants

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import kotlin.math.abs

@Composable
fun CalculateurPaiement(
    carte: CompteCredit,
    onCalculerPlan: (Double) -> Unit,
    onMontantChange: (Double) -> Unit = {},
    tempsRemboursement: Int? = null,
    interetsTotal: Double? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    var paiementMensuelCentimes by remember { mutableStateOf(0L) }
    var erreurPaiement by remember { mutableStateOf<String?>(null) }

    // Effet pour déclencher les calculs quand le montant change
    LaunchedEffect(paiementMensuelCentimes) {
        val montant = paiementMensuelCentimes / 100.0
        if (montant > 0 && erreurPaiement == null) {
            onMontantChange(montant)
        }
    }

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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                    
                    // Afficher les frais mensuels s'ils existent
                    if (carte.totalFraisMensuels > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Frais mensuels :",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = MoneyFormatter.formatAmount(carte.totalFraisMensuels),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
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
                        label = { Text("Minimum", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )

                    SuggestionChip(
                        onClick = {
                            paiementMensuelCentimes = (paiement5pourcent * 100).toLong()
                            erreurPaiement = null
                        },
                        label = { Text("5% dette", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )

                    SuggestionChip(
                        onClick = {
                            paiementMensuelCentimes = (paiement10pourcent * 100).toLong()
                            erreurPaiement = null
                        },
                        label = { Text("10% dette", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bouton pour calculer le plan
            Button(
                onClick = {
                    val montant = paiementMensuelCentimes / 100.0
                    if (erreurPaiement == null) {
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
            if (erreurPaiement == null) {
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A1A)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Temps de remboursement :",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                            Text(
                                text = if (tempsRemboursement != null) "$tempsRemboursement mois" else "Impossible",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (interetsTotal != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Intérêts totaux :",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                                Text(
                                    text = MoneyFormatter.formatAmount(interetsTotal),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF6B6B)
                                )
                            }
                        }
                        
                        // Afficher les frais totaux s'ils existent
                        if (carte.totalFraisMensuels > 0 && tempsRemboursement != null) {
                            val fraisTotaux = carte.calculerFraisTotaux(tempsRemboursement)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Frais totaux :",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                                Text(
                                    text = MoneyFormatter.formatAmount(fraisTotaux),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB74D)
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
    
    // Estimation de durée pour calculer les frais moyens
    val dureeEstimee = if (tauxMensuel > 0) {
        val paiementBaseEstime = maxOf(dette * 0.02, 25.0)
        val paiementNetEstime = paiementBaseEstime + interetsMensuels
        kotlin.math.ceil(dette / paiementNetEstime).toInt()
    } else {
        60
    }
    val fraisMensuelsMoyens = carte.calculerFraisMensuelsMoyens(dureeEstimee)

    // Paiement minimum = 2% du solde ou 25€, le plus élevé des deux, plus les intérêts et frais
    val paiementBase = maxOf(dette * 0.02, 25.0)
    return paiementBase + interetsMensuels + fraisMensuelsMoyens
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun CalculateurPaiementPreview() {
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
    
    CalculateurPaiement(
        carte = carteCredit,
        onCalculerPlan = {}
    )
}
