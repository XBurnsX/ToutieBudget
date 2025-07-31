package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*

@Composable
fun SelecteurDate(
    dateSelectionnee: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCalendar by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier
            .clickable { showCalendar = true }
            .size(80.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = "Calendrier",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = dateSelectionnee.dayOfMonth.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
    
    if (showCalendar) {
        Dialog(
            onDismissRequest = { showCalendar = false },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CalendrierDialog(
                    dateSelectionnee = dateSelectionnee,
                    onDateChange = { 
                        onDateChange(it)
                        showCalendar = false
                    },
                    onDismiss = { showCalendar = false }
                )
            }
        }
    }
}

@Composable
private fun CalendrierDialog(
    dateSelectionnee: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var currentMonth by remember { mutableStateOf(YearMonth.from(dateSelectionnee)) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121212) // Même couleur que le fond de l'app
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // En-tête avec mois/année et boutons de navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        currentMonth = currentMonth.minusMonths(1)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "Mois précédent"
                    )
                }
                
                                 Text(
                     text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.FRENCH)} ${currentMonth.year}",
                     fontSize = 18.sp,
                     fontWeight = FontWeight.Bold,
                     color = Color.White,
                     textAlign = TextAlign.Center
                 )
                
                IconButton(
                    onClick = {
                        currentMonth = currentMonth.plusMonths(1)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "Mois suivant"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Jours de la semaine
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val joursSemaine = listOf("L", "M", "M", "J", "V", "S", "D")
                joursSemaine.forEach { jour ->
                                         Text(
                         text = jour,
                         fontSize = 14.sp,
                         fontWeight = FontWeight.Medium,
                         color = Color.White,
                         modifier = Modifier.weight(1f),
                         textAlign = TextAlign.Center
                     )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Grille des jours
            val premierJourMois = currentMonth.atDay(1)
            val dernierJourMois = currentMonth.atEndOfMonth()
            val premierJourSemaine = premierJourMois.dayOfWeek.value
            val nombreJoursMois = dernierJourMois.dayOfMonth
            
            val joursAvant = premierJourSemaine - 1
            val totalCases = joursAvant + nombreJoursMois
            val nombreLignes = (totalCases + 6) / 7
            
            repeat(nombreLignes) { ligne ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    repeat(7) { colonne ->
                        val index = ligne * 7 + colonne
                        val jour = index - joursAvant + 1
                        
                        if (jour in 1..nombreJoursMois) {
                            val date = currentMonth.atDay(jour)
                            val isSelected = date == dateSelectionnee
                            val isToday = date == LocalDate.now()
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isToday -> MaterialTheme.colorScheme.primaryContainer
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable { onDateChange(date) },
                                contentAlignment = Alignment.Center
                            ) {
                                                                 Text(
                                     text = jour.toString(),
                                     fontSize = 14.sp,
                                     fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                     color = when {
                                         isSelected -> Color.White
                                         isToday -> Color.White
                                         else -> Color.White
                                     }
                                 )
                            }
                        } else {
                            // Case vide
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Boutons d'action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                                 TextButton(
                     onClick = onDismiss,
                     colors = ButtonDefaults.textButtonColors(
                         contentColor = Color.White
                     )
                 ) {
                     Text("Annuler")
                 }
                
                                 Button(
                     onClick = {
                         onDateChange(LocalDate.now())
                         onDismiss()
                     },
                     colors = ButtonDefaults.buttonColors(
                         containerColor = MaterialTheme.colorScheme.primary,
                         contentColor = Color.White
                     )
                 ) {
                     Text("Aujourd'hui")
                 }
            }
        }
    }
} 