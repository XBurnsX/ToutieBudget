// chemin/simule: /ui/comptes/composants/CompteItem.kt
package com.xburnsx.toutiebudget.ui.comptes.composants

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.ui.budget.composants.toColor
import java.text.NumberFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompteItem(
    compte: Compte,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val formatteurMonetaire = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)
    val couleurCompte = compte.couleur.toColor()

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
                    .background(couleurCompte),
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
                            text = formatteurMonetaire.format(compte.solde),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = if (compte.solde >= 0) Color.White else MaterialTheme.colorScheme.error
                        )
                        if (compte is CompteCheque) {
                            Text(
                                text = "Prêt à placer: ${formatteurMonetaire.format(compte.solde)}",
                                fontSize = 13.sp,
                                color = Color(0xFF66BB6A),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.offset(y = 12.dp) // Déplace le texte vers le bas
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (compte !is CompteCheque) {
                    InfoSecondaireCompte(compte = compte, formatteur = formatteurMonetaire)
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
        is CompteInvestissement -> Icons.Default.ShowChart
    }
    Icon(
        imageVector = icone,
        contentDescription = "Type de compte",
        tint = tint,
        modifier = Modifier.size(28.dp)
    )
}

@Composable
private fun InfoSecondaireCompte(compte: Compte, formatteur: NumberFormat) {
    when (compte) {
        // --- MODIFICATION ICI ---
        is CompteCheque -> Text(
            text = "Prêt à placer: ${formatteur.format(compte.solde)}",
            fontSize = 13.sp,
            color = Color(0xFF66BB6A), // Vert clair pour la lisibilité
            fontWeight = FontWeight.SemiBold
        )

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
                        text = "Utilisé : ${formatteur.format(abs(compte.solde))}",
                        fontSize = 12.sp, color = Color.LightGray
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Limite : ${formatteur.format(compte.limiteCredit)}",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
            }
        }
        is CompteDette -> {
            val progression = ((compte.montantInitial - compte.solde) / compte.montantInitial).toFloat().coerceIn(0f, 1f)
            Column {
                LinearProgressIndicator(
                    progress = { progression },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                    color = Color(0xFF4CAF50),
                    trackColor = Color.Gray.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Remboursé à ${(progression * 100).toInt()}% sur ${formatteur.format(compte.montantInitial)}",
                    fontSize = 12.sp, color = Color.Gray
                )
            }
        }

        is CompteInvestissement -> Text("Compte d'investissement", fontSize = 13.sp, color = Color.Gray)
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
        solde = -1250.47,
        couleur = "#FFC107",
        estArchive = false,
        ordre = 2,
        limiteCredit = 5000.0,
        interet = 19.99
    )
    
    val compteCreditLimite = CompteCredit(
        id = "3",
        utilisateurId = "user",
        nom = "Marge de Crédit",
        solde = -9500.0,
        couleur = "#F44336",
        estArchive = false,
        ordre = 3,
        limiteCredit = 10000.0,
        interet = 7.5
    )
    
    val compteDette = CompteDette(
        id = "4",
        utilisateurId = "user",
        nom = "Prêt Auto",
        solde = 15200.0,
        estArchive = false,
        ordre = 4,
        montantInitial = 25000.0,
        interet = 5.5
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