package com.xburnsx.toutiebudget.ui.dette.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SectionParametresAvances(
    dette: CompteDette,
    onDetteChange: (CompteDette) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F1F)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Paramètres avancés",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            // Date de début
            ChampDate(
                label = "Date de début :",
                date = LocalDate.of(2025, 7, 5),
                icon = Icons.Default.CalendarMonth,
                color = Color.Red
            )
            
            // Date de fin
            ChampDate(
                label = "Date de fin :",
                date = LocalDate.of(2026, 6, 5),
                icon = Icons.Default.CheckCircle,
                color = Color.Red
            )
            
            // Paiements effectués
            ChampUniversel(
                valeur = dette.paiementEffectue.toLong() * 100, // Conversion en centimes pour ChampUniversel
                onValeurChange = { valeur ->
                    // TODO: Implémenter la logique de mise à jour
                },
                libelle = "Paiements effectués (automatique)",
                isMoney = false, // Pas d'argent, c'est un nombre de paiements
                suffix = "",
                icone = Icons.Default.Payment
            )
        }
    }
}

@Composable
private fun ChampDate(
    label: String,
    date: LocalDate,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            fontSize = 14.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
} 