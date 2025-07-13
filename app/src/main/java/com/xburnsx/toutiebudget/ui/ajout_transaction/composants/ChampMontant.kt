package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.utils.formatToCurrency

@Composable
fun ChampMontant(
    valeur: String,
    typeTransaction: String,
    onValeurChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isClavierVisible by remember { mutableStateOf(false) }
    var montantTemporaire by remember { mutableStateOf(valeur) }

    val onKeyPress = { key: String ->
        montantTemporaire = when (key) {
            "del" -> {
                if (montantTemporaire.isNotEmpty()) {
                    montantTemporaire.dropLast(1)
                } else {
                    ""
                }
            }
            "." -> {
                if (!montantTemporaire.contains('.') && montantTemporaire.isNotEmpty()) {
                    "$montantTemporaire$key"
                } else {
                    montantTemporaire
                }
            }
            else -> {
                if (montantTemporaire.length < 8) {
                    "$montantTemporaire$key"
                } else {
                    montantTemporaire
                }
            }
        }
    }

    Column(modifier = modifier) {
        // Champ de montant cliquable
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isClavierVisible = !isClavierVisible },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Affichage du montant
                val couleurMontant = if (typeTransaction == "Dépense") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                
                Text(
                    text = formatToCurrency(montantTemporaire),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = couleurMontant,
                    modifier = Modifier.weight(1f)
                )
                
                // Icône pour indiquer que c'est cliquable
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Ouvrir le clavier",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Clavier numérique (visible seulement si activé)
        AnimatedVisibility(
            visible = isClavierVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Afficheur du montant en cours de saisie
                    Text(
                        text = formatToCurrency(montantTemporaire),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // SUPPRIMÉ : ClavierNumeriqueCompose et bouton valider
                }
            }
        }
    }
} 