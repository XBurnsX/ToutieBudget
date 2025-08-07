package com.xburnsx.toutiebudget.ui.dette.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Schedule
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

@Composable
fun SectionInformationsPrincipales(
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
                text = "Informations principales",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            // Taux d'intérêt annuel
            ChampUniversel(
                valeur = (dette.tauxInteret ?: 25.0).toLong() * 100, // Conversion en centimes pour ChampUniversel
                onValeurChange = { valeur ->
                    onDetteChange(dette.copy(tauxInteret = valeur.toDouble() / 100))
                },
                libelle = "Taux d'intérêt annuel",
                isMoney = false, // Pas d'argent, c'est un pourcentage
                suffix = "%",
                icone = Icons.Default.Percent
            )
            
            // Prix d'achat
            ChampUniversel(
                valeur = (dette.montantInitial * 100).toLong(), // Conversion en centimes
                onValeurChange = { valeur ->
                    onDetteChange(dette.copy(montantInitial = valeur.toDouble() / 100))
                },
                libelle = "Prix d'achat",
                isMoney = true, // C'est de l'argent
                suffix = "$",
                icone = Icons.Default.AttachMoney
            )
            
            // Durée du prêt
            ChampUniversel(
                valeur = (dette.dureeMoisPret ?: 12).toLong() * 100, // Conversion en centimes pour ChampUniversel
                onValeurChange = { valeur ->
                    onDetteChange(dette.copy(dureeMoisPret = (valeur / 100).toInt()))
                },
                libelle = "Durée du prêt",
                isMoney = false, // Pas d'argent, c'est une durée
                suffix = "mois",
                icone = Icons.Default.Schedule
            )
        }
    }
} 