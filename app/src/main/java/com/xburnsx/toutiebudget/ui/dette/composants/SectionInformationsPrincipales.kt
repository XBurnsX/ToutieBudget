package com.xburnsx.toutiebudget.ui.dette.composants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel
import kotlin.math.roundToLong

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
            modifier = Modifier
                .padding(16.dp)
                .windowInsetsPadding(WindowInsets.ime)
                .windowInsetsPadding(WindowInsets.navigationBars),
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
                valeur = (((dette.tauxInteret ?: 0.0) * 100.0).roundToLong()), // 22.5 -> 2250 centièmes de %
                onValeurChange = { valeur ->
                    onDetteChange(dette.copy(tauxInteret = valeur.toDouble() / 100))
                },
                libelle = "Taux d'intérêt annuel",
                isMoney = false, // ce n'est pas un montant en $
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
            ChampEntierAvecSuffixe(
                label = "Durée du prêt",
                value = (dette.dureeMoisPret ?: 12).toString(),
                suffix = "mois",
                onValueChange = { value ->
                    val mois = value.filter { it.isDigit() }.toIntOrNull() ?: 0
                    onDetteChange(dette.copy(dureeMoisPret = mois))
                }
            )
        }
    }
}

@Composable
private fun ChampEntierAvecSuffixe(
    label: String,
    value: String,
    suffix: String,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { nouveau -> onValueChange(nouveau.filter { it.isDigit() }) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = suffix,
                fontSize = 16.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
} 