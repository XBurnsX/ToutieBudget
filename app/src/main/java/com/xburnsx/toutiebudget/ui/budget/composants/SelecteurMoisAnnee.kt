package com.xburnsx.toutiebudget.ui.budget.composants

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun SelecteurMoisAnnee(moisSelectionne: Date, onMoisChange: (Date) -> Unit) {
    val format = SimpleDateFormat("MMMM yyyy", java.util.Locale.FRENCH)
    val moisLabels = listOf(
        "Janvier", "Février", "Mars", "Avril",
        "Mai", "Juin", "Juillet", "Août",
        "Septembre", "Octobre", "Novembre", "Décembre"
    )
    var expanded by remember { mutableStateOf(false) }
    val calendar = Calendar.getInstance().apply { time = moisSelectionne }
    var anneeCourante by remember { mutableStateOf(calendar.get(Calendar.YEAR)) }
    val moisCourant = calendar.get(Calendar.MONTH)

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(format.format(moisSelectionne).replaceFirstChar { it.uppercase() }, color = Color.White, fontWeight = FontWeight.Bold)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { anneeCourante-- }) {
                    Text("<", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text(anneeCourante.toString(), color = Color.White, fontWeight = FontWeight.Bold)
                IconButton(onClick = { anneeCourante++ }) {
                    Text(">", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                for (row in 0 until 3) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (col in 0 until 4) {
                            val monthIndex = row * 4 + col
                            val isSelected = (anneeCourante == calendar.get(Calendar.YEAR) && monthIndex == moisCourant)
                            TextButton(
                                onClick = {
                                    val cal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, anneeCourante)
                                        set(Calendar.MONTH, monthIndex)
                                        set(Calendar.DAY_OF_MONTH, 1)
                                    }
                                    onMoisChange(cal.time)
                                    expanded = false
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (isSelected) Color(0xFF1976D2) else Color.Transparent,
                                    contentColor = if (isSelected) Color.White else Color.LightGray
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(moisLabels[monthIndex], fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
    }
} 