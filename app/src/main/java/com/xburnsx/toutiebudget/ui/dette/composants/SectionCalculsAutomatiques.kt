package com.xburnsx.toutiebudget.ui.dette.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import kotlin.math.pow

@Composable
fun SectionCalculsAutomatiques(dette: CompteDette) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = "Calculatrice",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "Calculs automatiques",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            // Calculs
            val calculs = calculerDette(dette)
            
            ChampCalcul(
                label = "Paiement mensuel (calculé) :",
                value = "${calculs.paiementMensuel} $"
            )
            ChampCalcul(
                label = "Prix total (calculé) :",
                value = "${calculs.prixTotal} $"
            )
            ChampCalcul(
                label = "Coût total :",
                value = "${calculs.coutTotal} $"
            )
            ChampCalcul(
                label = "Solde restant :",
                value = "${calculs.soldeRestant} $"
            )
            ChampCalcul(
                label = "Intérêts payés :",
                value = "${calculs.interetsPayes} $"
            )
        }
    }
}

@Composable
private fun ChampCalcul(
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = value,
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

data class CalculsDette(
    val paiementMensuel: String,
    val prixTotal: String,
    val coutTotal: String,
    val soldeRestant: String,
    val interetsPayes: String
)

private fun calculerDette(dette: CompteDette): CalculsDette {
    val montantInitial = dette.montantInitial
    val tauxInteretAnnuel = dette.tauxInteret ?: 25.0
    val dureeMois = dette.dureeMoisPret ?: 12
    val paiementsEffectues = dette.paiementEffectue.coerceAtLeast(0)
    
    // Conversion du taux annuel en mensuel
    val tauxInteretMensuel = tauxInteretAnnuel / 100 / 12
    
    // Calcul du paiement mensuel
    val paiementMensuel = if (tauxInteretMensuel > 0) {
        montantInitial * (tauxInteretMensuel * (1 + tauxInteretMensuel).pow(dureeMois)) / 
        ((1 + tauxInteretMensuel).pow(dureeMois) - 1)
    } else {
        if (dureeMois > 0) montantInitial / dureeMois else 0.0
    }
    
    // Prix total
    val prixTotal = paiementMensuel * dureeMois
    
    // Coût total
    val coutTotal = paiementMensuel * dureeMois
    
    // Intérêts payés
    val interetsPayes = (prixTotal - montantInitial) * (paiementsEffectues.toDouble() / dureeMois.coerceAtLeast(1))
    
    // Solde restant (indicatif)
    val soldeRestant = prixTotal - paiementMensuel * paiementsEffectues
    
    return CalculsDette(
        paiementMensuel = String.format("%.2f", paiementMensuel),
        prixTotal = String.format("%.2f", prixTotal),
        coutTotal = String.format("%.2f", coutTotal),
        soldeRestant = String.format("%.2f", soldeRestant.coerceAtLeast(0.0)),
        interetsPayes = String.format("%.2f", interetsPayes.coerceAtLeast(0.0))
    )
} 