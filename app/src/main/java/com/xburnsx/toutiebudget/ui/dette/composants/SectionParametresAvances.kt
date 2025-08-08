package com.xburnsx.toutiebudget.ui.dette.composants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.modeles.CompteDette
import com.xburnsx.toutiebudget.ui.ajout_transaction.composants.SelecteurDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun SectionParametresAvances(
    dette: CompteDette,
    onDetteChange: (CompteDette) -> Unit
) {
    // Etats locaux pour les dates (initialisation basée sur la durée existante)
    var dateDebut by remember { mutableStateOf(LocalDate.now()) }
    var dateFin by remember { mutableStateOf(dateDebut.plusMonths((dette.dureeMoisPret ?: 12).toLong())) }

    // Quand la durée change ailleurs (ex: via champ durée), on ajuste la date de fin
    LaunchedEffect(dette.dureeMoisPret, dateDebut) {
        val duree = (dette.dureeMoisPret ?: 12).coerceAtLeast(0)
        dateFin = dateDebut.plusMonths(duree.toLong())
    }

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Date de début :",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                SelecteurDate(
                    dateSelectionnee = dateDebut,
                    onDateChange = { nouvelleDate ->
                        dateDebut = nouvelleDate
                        val mois = monthsBetween(nouvelleDate, dateFin).coerceAtLeast(0)
                        onDetteChange(dette.copy(dureeMoisPret = mois))
                    }
                )
            }

            // Date de fin
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Date de fin :",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                SelecteurDate(
                    dateSelectionnee = dateFin,
                    onDateChange = { nouvelleDate ->
                        dateFin = nouvelleDate
                        val mois = monthsBetween(dateDebut, nouvelleDate).coerceAtLeast(0)
                        onDetteChange(dette.copy(dureeMoisPret = mois))
                    }
                )
            }

            // Paiements effectués (lecture seule)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Payment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Paiements effectués (automatique)",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = dette.paiementEffectue.toString(),
                    fontSize = 14.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun monthsBetween(start: LocalDate, end: LocalDate): Int {
    val months = ChronoUnit.MONTHS.between(start, end)
    return months.toInt()
} 