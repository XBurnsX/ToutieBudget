// chemin/simule: /ui/comptes/composants/CompteItem.kt
package com.xburnsx.toutiebudget.ui.comptes.composants

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.data.modeles.CompteCredit
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.data.modeles.CompteInvestissement
import com.xburnsx.toutiebudget.ui.budget.composants.toColor
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompteItem(
    compte: Compte,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val couleurCompte = compte.couleur.toColor()
    
    // Forcer la couleur rouge pour les comptes dette
    val couleurFinale = if (compte is CompteDette) {
        Color.Red
    } else {
        couleurCompte
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(60.dp)
                    .background(couleurFinale),
                contentAlignment = Alignment.Center
            ) {
                IconePourCompte(compte = compte, tint = Color.White)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = compte.nom,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = MoneyFormatter.formatAmount(compte.solde),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = if (compte.solde >= 0) Color.White else MaterialTheme.colorScheme.error
                        )
                        if (compte is CompteCheque && compte.pretAPlacer != 0.0) {
                            Text(
                                text = "Prêt à placer: ${MoneyFormatter.formatAmount(compte.pretAPlacer)}",
                                fontSize = 13.sp,
                                color = if (compte.pretAPlacer >= 0) Color(0xFF66BB6A) else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.offset(y = 12.dp) // Déplace le texte vers le bas
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (compte !is CompteCheque) {
                    InfoSecondaireCompte(compte = compte)
                }
            }
        }
    }
}

@Composable
private fun IconePourCompte(compte: Compte, tint: Color) {
    val icone = when (compte) {
        is CompteCheque -> Icons.Default.AccountBalanceWallet
        is CompteCredit -> Icons.Default.CreditCard
        is CompteDette -> Icons.Default.RequestQuote
        is CompteInvestissement -> Icons.AutoMirrored.Filled.ShowChart
    }
    Icon(
        imageVector = icone,
        contentDescription = "Type de compte",
        tint = tint,
        modifier = Modifier.size(28.dp)
    )
}

@Composable
private fun InfoSecondaireCompte(compte: Compte) {
    when (compte) {
        is CompteCredit -> {
            val progression = (abs(compte.solde) / compte.limiteCredit).toFloat().coerceIn(0f, 1f)
            val couleurProgression = when {
                progression > 0.9f -> MaterialTheme.colorScheme.error
                progression > 0.7f -> Color(0xFFFFA000) // Ambre
                else -> Color(0xFF388E3C) // Vert foncé
            }
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { progression },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                    color = couleurProgression,
                    trackColor = Color.Gray.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        text = "Utilisé : ${MoneyFormatter.formatAmount(abs(compte.solde))}",
                        fontSize = 12.sp, color = Color.LightGray
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Limite : ${MoneyFormatter.formatAmount(compte.limiteCredit)}",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
            }
        }
        is CompteDette -> {
            val base = (compte.prixTotal ?: compte.montantInitial).coerceAtLeast(0.0)
            val remaining = if (compte.soldeDette < 0) abs(compte.soldeDette) else compte.soldeDette
            val progression = if (base > 0) {
                ((base - remaining) / base).toFloat().coerceIn(0f, 1f)
            } else 0f
            Column {
                LinearProgressIndicator(
                    progress = { progression },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                    color = Color(0xFF4CAF50),
                    trackColor = Color.Gray.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Remboursé à ${(progression * 100).toInt()}% sur ${MoneyFormatter.formatAmount(base)}",
                    fontSize = 12.sp, color = Color.Gray
                )
            }
        }
        is CompteInvestissement -> Text("Compte d'investissement", fontSize = 13.sp, color = Color.Gray)
        else -> { /* Ne rien faire pour les autres types de comptes */ }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun CompteItemPreview() {
    val compteCheque = CompteCheque(
        id = "1",
        utilisateurId = "user",
        nom = "WealthSimple",
        solde = 2543.75,
        couleur = "#4CAF50",
        estArchive = false,
        ordre = 1
    )
    
    val compteCreditNormal = CompteCredit(
        id = "2",
        utilisateurId = "user",
        nom = "Mastercard",
        soldeUtilise = -1250.47,
        couleur = "#FFC107",
        estArchive = false,
        ordre = 2,
        limiteCredit = 5000.0,
        tauxInteret = 19.99
    )
    
    val compteCreditLimite = CompteCredit(
        id = "3",
        utilisateurId = "user",
        nom = "Marge de Crédit",
        soldeUtilise = -9500.0,
        couleur = "#F44336",
        estArchive = false,
        ordre = 3,
        limiteCredit = 10000.0,
        tauxInteret = 7.5
    )
    
    val compteDette = CompteDette(
        id = "4",
        utilisateurId = "user",
        nom = "Prêt Auto",
        soldeDette = 15200.0,
        estArchive = false,
        ordre = 4,
        montantInitial = 25000.0,
        tauxInteret = 5.5,
        paiementMinimum = 500.0,
        dureeMoisPret = 60
    )
    
    val compteInvestissement = CompteInvestissement(
        id = "5",
        utilisateurId = "user",
        nom = "CELI",
        solde = 12750.33,
        couleur = "#9C27B0",
        estArchive = false,
        ordre = 5
    )

    MaterialTheme {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CompteItem(compte = compteCheque, onClick = {}, onLongClick = {})
            CompteItem(compte = compteCreditNormal, onClick = {}, onLongClick = {})
            CompteItem(compte = compteCreditLimite, onClick = {}, onLongClick = {})
            CompteItem(compte = compteDette, onClick = {}, onLongClick = {})
            CompteItem(compte = compteInvestissement, onClick = {}, onLongClick = {})
        }
    }
}