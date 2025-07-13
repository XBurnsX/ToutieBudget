package com.xburnsx.toutiebudget.ui.composants_communs

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.ui.theme.ToutieBudgetTheme
import com.xburnsx.toutiebudget.utils.formatToCurrency

// <<< CONTRÔLE DE LA TAILLE >>>
private val hauteurBoutonClavier: Dp = 42.dp
private val policeBoutonClavier: TextUnit = 24.sp
private val policeAfficheur: TextUnit = 40.sp
private val espacementVertical: Dp = 8.dp
private val espacementHorizontal: Dp = 8.dp
// <<< FIN DU CONTRÔLE >>>

@Composable
fun BlocSaisieMontant(
    modifier: Modifier = Modifier,
    onTermine: (String) -> Unit,
    montantInitial: String = "",
    onFermer: (() -> Unit)? = null
) {
    var montant by remember { mutableStateOf(montantInitial) }

    // Synchroniser le montant local avec le montant initial
    LaunchedEffect(montantInitial) {
        montant = montantInitial
    }

    val onKeyPress = { key: String ->
        val nouveauMontant = when (key) {
            "del" -> montant.dropLast(1)
            // La touche "." est maintenant ignorée
            "." -> montant
            else -> if (montant.length > 8) montant else if (montant == "0") key else "$montant$key"
        }
        montant = nouveauMontant
        // Mettre à jour automatiquement le montant
        onTermine(nouveauMontant)
    }

    // --- DÉFINITION DES STYLES ---
    val cardBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF2C2C2C), // Charcoal foncé
            Color(0xFF1A1A1A)  // Charcoal très foncé
        )
    )
    // --- FIN DES STYLES ---

    Box {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(cardBrush),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                // Rangée du haut : Afficheur + Bouton "Fermer" (optionnel)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Afficheur du montant
                    Text(
                        text = formatToCurrency(montant),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start,
                        fontSize = policeAfficheur,
                        fontWeight = FontWeight.Light,
                        maxLines = 1,
                        color = Color.White
                    )
                    
                    // Bouton Fermer (seulement si onFermer est fourni)
                    if (onFermer != null) {
                        Box(
                            modifier = Modifier
                                .shadow(elevation = 8.dp, shape = CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(onClick = onFermer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Fermer",
                                tint = Color.White,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Le clavier numérique
                ClavierInterne(onKeyPress = onKeyPress)
            }
        }
    }
}

@Composable
private fun ClavierInterne(onKeyPress: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val buttons = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "del")
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(espacementVertical)
    ) {
        buttons.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(espacementHorizontal)
            ) {
                row.forEach { key ->
                    val isFunctionKey = key == "." || key == "del"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(hauteurBoutonClavier)
                            .clip(CircleShape)
                            .background(
                                if (isFunctionKey) Color(0xFF3A3A3A) else Color(0xFF4A4A4A)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = true),
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onKeyPress(key)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "del") {
                            Icon(
                                Icons.Default.Backspace,
                                contentDescription = "Effacer",
                                tint = Color(0xFFCCCCCC)
                            )
                        } else {
                            Text(
                                text = key,
                                fontSize = policeBoutonClavier,
                                fontWeight = if (isFunctionKey) FontWeight.Normal else FontWeight.Medium,
                                color = if (isFunctionKey) Color(0xFFCCCCCC) else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- PREVIEWS ---

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Clair")
@Composable
fun BlocSaisieMontantPreview() {
    ToutieBudgetTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier.padding(16.dp)
            ) {
                BlocSaisieMontant(onTermine = {})
            }
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Sombre")
@Composable
fun BlocSaisieMontantDarkPreview() {
    ToutieBudgetTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier.padding(16.dp)
            ) {
                BlocSaisieMontant(onTermine = {})
            }
        }
    }
}