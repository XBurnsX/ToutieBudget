package com.xburnsx.toutiebudget.ui.budget.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SelecteurMoisAnnee(
    moisSelectionne: Date,
    onMoisChange: (Date) -> Unit,
    modifier: Modifier = Modifier
) {
    // Utilise rememberUpdatedState pour garantir que la lambda la plus récente est toujours utilisée.
    val currentOnMoisChange by rememberUpdatedState(onMoisChange)

    // État pour contrôler la visibilité du menu déroulant
    var expanded by remember { mutableStateOf(false) }

    // Calendrier pour la logique de date
    val calendar = remember { Calendar.getInstance() }.apply { time = moisSelectionne }

    // Année affichée dans le picker, distincte de l'année sélectionnée
    var anneeAffichee by remember(moisSelectionne) {
        mutableStateOf(calendar.get(Calendar.YEAR))
    }

    // Génère dynamiquement les noms des mois pour la locale française
    val moisLabels = remember {
        java.text.DateFormatSymbols(Locale.FRENCH).months.take(12).map {
            it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.FRENCH) else char.toString() }
        }
    }

    // Formatteur pour afficher le mois et l'année
    val formatAfficheur = remember { SimpleDateFormat("MMMM yyyy", Locale.FRENCH) }

    // Box agit comme une ancre pour le DropdownMenu
    Box(modifier = modifier) {
        // Bouton principal pour afficher la date et ouvrir le menu
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { expanded = true } // Ouvre le menu au clic
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatAfficheur.format(moisSelectionne).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                contentDescription = if (expanded) "Fermer le sélecteur" else "Ouvrir le sélecteur",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // Le DropdownMenu qui contient notre sélecteur personnalisé
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }, // Se ferme si on clique à l'extérieur
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .fillMaxWidth(0.95f), // Prend 95% de la largeur de l'écran
            properties = PopupProperties(focusable = true) // Permet d'interagir avec les boutons à l'intérieur
        ) {
            // Le contenu de notre sélecteur
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Sélecteur d'année
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { anneeAffichee-- }) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, "Année précédente", tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        text = anneeAffichee.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { anneeAffichee++ }) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, "Année suivante", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Grille des mois
                for (row in 0 until 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0 until 4) {
                            val monthIndex = row * 4 + col
                            val calendarSelected = Calendar.getInstance().apply { time = moisSelectionne }
                            val isSelected = (anneeAffichee == calendarSelected.get(Calendar.YEAR) && monthIndex == calendarSelected.get(Calendar.MONTH))

                            val buttonColors = if (isSelected) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = {
                                    val cal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, anneeAffichee)
                                        set(Calendar.MONTH, monthIndex)
                                        set(Calendar.DAY_OF_MONTH, 1)
                                    }
                                    currentOnMoisChange(cal.time)
                                    expanded = false // Ferme le menu après la sélection
                                },
                                colors = buttonColors,
                                shape = CircleShape,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text(
                                    text = moisLabels[monthIndex].substring(0, 3),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    if (row < 2) Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SelecteurMoisAnneePopupPreview() {
    var selectedDate by remember { mutableStateOf(Date()) }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SelecteurMoisAnnee(
                    moisSelectionne = selectedDate,
                    onMoisChange = { newDate ->
                        selectedDate = newDate
                    }
                )

                Spacer(modifier = Modifier.height(50.dp))

                Text("Autre contenu de la page pour montrer que le sélecteur se superpose bien.")
            }
        }
    }
}