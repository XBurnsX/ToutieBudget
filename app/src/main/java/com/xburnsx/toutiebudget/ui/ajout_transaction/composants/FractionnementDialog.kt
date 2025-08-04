// chemin/simule: /ui/ajout_transaction/composants/FractionnementDialog.kt
// Dépendances: Jetpack Compose, Material3, EnveloppeUi.kt, MoneyFormatter.kt

package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.budget.composants.toColor
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.util.UUID
import kotlin.math.abs

/**
 * Dialog moderne et élégant pour fractionner une transaction entre plusieurs enveloppes.
 * Design professionnel avec animations fluides et interface intuitive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FractionnementDialog(
    montantTotal: Double,
    enveloppesDisponibles: List<EnveloppeUi>,
    onFractionnementConfirme: (List<FractionTransaction>) -> Unit,
    onDismiss: () -> Unit
) {
    var fractions by remember { mutableStateOf(listOf<FractionTransaction>()) }
    var montantRestant by remember { mutableStateOf(montantTotal) }
    var erreur by remember { mutableStateOf<String?>(null) }

    // Animation pour l'apparition du dialog
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        if (fractions.isEmpty()) {
            fractions = listOf(
                FractionTransaction(
                    id = UUID.randomUUID().toString(),
                    montant = 0.0,
                    enveloppeId = ""
                )
            )
        }
    }

    // Calcul des montants
    val montantAlloue = fractions.sumOf { it.montant }
    montantRestant = montantTotal - montantAlloue

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300, easing = EaseOutQuart)
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(200)
            ) + fadeOut(animationSpec = tween(200))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E1E)
                ),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // === EN-TÊTE ÉLÉGANT ===
                    EnteteDialog(onDismiss = onDismiss)

                    // === BARRE DE PROGRESSION MODERNE ===
                    BarreProgressionModerne(
                        montantTotal = montantTotal,
                        montantAlloue = montantAlloue,
                        montantRestant = montantRestant
                    )

                    // === LISTE DES FRACTIONS ===
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = fractions,
                            key = { _, fraction -> fraction.id }
                        ) { index, fraction ->
                            AnimatedVisibility(
                                visible = true,
                                enter = slideInVertically(
                                    initialOffsetY = { -it },
                                    animationSpec = tween(300)
                                ) + fadeIn(animationSpec = tween(300))
                            ) {
                                CardeFraction(
                                    fraction = fraction,
                                    numeroFraction = index + 1,
                                    enveloppesDisponibles = enveloppesDisponibles,
                                    peutSupprimer = fractions.size > 1,
                                    onFractionChanged = { nouvelleFraction ->
                                        fractions = fractions.map {
                                            if (it.id == fraction.id) nouvelleFraction else it
                                        }
                                        erreur = null
                                    },
                                    onSupprimer = {
                                        if (fractions.size > 1) {
                                            fractions = fractions.filter { it.id != fraction.id }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // === BOUTON AJOUTER FRACTION ===
                    BoutonAjouterFraction {
                        val nouvelleFraction = FractionTransaction(
                            id = UUID.randomUUID().toString(),
                            montant = 0.0,
                            enveloppeId = ""
                        )
                        fractions = fractions + nouvelleFraction
                    }

                    // === MESSAGE D'ERREUR ===
                    AnimatedVisibility(
                        visible = erreur != null,
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        erreur?.let { messageErreur ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF661B1B)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB3B3),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = messageErreur,
                                        color = Color(0xFFFFB3B3),
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // === BOUTONS D'ACTION ===
                    BoutonsAction(
                        montantRestant = montantRestant,
                        fractions = fractions,
                        onAnnuler = onDismiss,
                        onConfirmer = {
                            if (montantRestant.isNearlyZero() && fractions.all { it.montant > 0 && it.enveloppeId.isNotBlank() }) {
                                onFractionnementConfirme(fractions)
                            } else {
                                erreur = "Vous devez répartir tout le montant et sélectionner une enveloppe pour chaque fraction."
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * En-tête élégant du dialog avec titre et bouton de fermeture.
 */
@Composable
private fun EnteteDialog(
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Fractionner",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Répartissez le montant entre vos enveloppes",
                fontSize = 14.sp,
                color = Color(0xFF9CA3AF)
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .background(
                    color = Color(0xFF374151),
                    shape = CircleShape
                )
                .size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Fermer",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Barre de progression moderne avec dégradé et animations.
 */
@Composable
private fun BarreProgressionModerne(
    montantTotal: Double,
    montantAlloue: Double,
    montantRestant: Double
) {
    val progression by animateFloatAsState(
        targetValue = if (montantTotal > 0) (montantAlloue / montantTotal).toFloat() else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "progression"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Barre de progression avec dégradé
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(
                        color = Color(0xFF374151),
                        shape = RoundedCornerShape(6.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progression)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            ),
                            shape = RoundedCornerShape(6.dp)
                        )
                )
            }

            // Indicateurs de montants
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IndicateurMontant(
                    label = "Total",
                    montant = montantTotal,
                    couleur = Color.White,
                    alignement = Alignment.Start
                )

                IndicateurMontant(
                    label = "Alloué",
                    montant = montantAlloue,
                    couleur = MaterialTheme.colorScheme.primary,
                    alignement = Alignment.CenterHorizontally
                )

                IndicateurMontant(
                    label = "Restant",
                    montant = montantRestant,
                    couleur = if (montantRestant >= 0) Color(0xFF10B981) else Color(0xFFEF4444),
                    alignement = Alignment.End
                )
            }
        }
    }
}

/**
 * Indicateur de montant avec animation.
 */
@Composable
private fun IndicateurMontant(
    label: String,
    montant: Double,
    couleur: Color,
    alignement: Alignment.Horizontal
) {
    Column(
        horizontalAlignment = alignement
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF9CA3AF),
            fontWeight = FontWeight.Medium
        )

        val montantAffiche by animateFloatAsState(
            targetValue = montant.toFloat(),
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "montant_$label"
        )

        Text(
            text = MoneyFormatter.formatAmountFromCents(montantAffiche.toLong()),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = couleur
        )
    }
}

/**
 * Carte compacte et élégante pour chaque fraction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardeFraction(
    fraction: FractionTransaction,
    numeroFraction: Int,
    enveloppesDisponibles: List<EnveloppeUi>,
    peutSupprimer: Boolean,
    onFractionChanged: (FractionTransaction) -> Unit,
    onSupprimer: () -> Unit
) {
    var expandedEnveloppe by remember { mutableStateOf(false) }
    val enveloppeSelectionnee = enveloppesDisponibles.find { it.id == fraction.enveloppeId }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // En-tête de la fraction
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = numeroFraction.toString(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Fraction $numeroFraction",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (peutSupprimer) {
                    IconButton(
                        onClick = onSupprimer,
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                color = Color(0xFF374151),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Supprimer",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Sélection de l'enveloppe
            ExposedDropdownMenuBox(
                expanded = expandedEnveloppe,
                onExpandedChange = { expandedEnveloppe = !expandedEnveloppe }
            ) {
                OutlinedTextField(
                    value = enveloppeSelectionnee?.nom ?: "Sélectionner une enveloppe",
                    onValueChange = { },
                    readOnly = true,
                    label = {
                        Text(
                            "Enveloppe",
                            color = Color(0xFF9CA3AF)
                        )
                    },
                    leadingIcon = {
                        enveloppeSelectionnee?.let { enveloppe ->
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        color = enveloppe.couleurProvenance?.toColor() ?: Color.Gray,
                                        shape = CircleShape
                                    )
                            )
                        }
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedEnveloppe)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color(0xFF4B5563),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = expandedEnveloppe,
                    onDismissRequest = { expandedEnveloppe = false },
                    modifier = Modifier.background(Color(0xFF1E1E1E))
                ) {
                    enveloppesDisponibles.forEach { enveloppe ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(
                                                color = enveloppe.couleurProvenance?.toColor() ?: Color.Gray,
                                                shape = CircleShape
                                            )
                                    )
                                    Text(
                                        text = enveloppe.nom,
                                        color = Color.White
                                    )
                                }
                            },
                            onClick = {
                                onFractionChanged(fraction.copy(enveloppeId = enveloppe.id))
                                expandedEnveloppe = false
                            }
                        )
                    }
                }
            }

            // Champ de montant
            OutlinedTextField(
                value = if (fraction.montant > 0) String.format("%.2f", fraction.montant / 100.0) else "",
                onValueChange = { value ->
                    val montantEnDollars = value.toDoubleOrNull() ?: 0.0
                    val montantEnCentimes = montantEnDollars * 100
                    onFractionChanged(fraction.copy(montant = montantEnCentimes))
                },
                label = {
                    Text(
                        "Montant ($)",
                        color = Color(0xFF9CA3AF)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AttachMoney,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color(0xFF4B5563),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }
    }
}

/**
 * Bouton moderne pour ajouter une nouvelle fraction.
 */
@Composable
private fun BoutonAjouterFraction(
    onAjouter: () -> Unit
) {
    OutlinedButton(
        onClick = onAjouter,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            )
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Ajouter une fraction",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Boutons d'action du dialog (Annuler/Confirmer).
 */
@Composable
private fun BoutonsAction(
    montantRestant: Double,
    fractions: List<FractionTransaction>,
    onAnnuler: () -> Unit,
    onConfirmer: () -> Unit
) {
    val peutConfirmer = montantRestant.isNearlyZero() &&
            fractions.all { it.montant > 0 && it.enveloppeId.isNotBlank() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onAnnuler,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF9CA3AF)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Annuler",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Button(
            onClick = onConfirmer,
            enabled = peutConfirmer,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = Color(0xFF374151)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Confirmer",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Extension pour vérifier si un montant est proche de zéro.
 */
fun Double.isNearlyZero(epsilon: Double = 0.01): Boolean {
    return abs(this) < epsilon
}

/**
 * Modèle de données pour une fraction de transaction.
 */
data class FractionTransaction(
    val id: String,
    val montant: Double,
    val enveloppeId: String,
    val note: String = ""
)