// chemin/simule: /ui/budget/composants/ClavierBudgetEnveloppe.kt
// Dépendances: ChampMontantUniversel (existant), SelecteurEnveloppeVirement
// RÉUTILISE votre clavier existant + ajoute sélection compte

package com.xburnsx.toutiebudget.ui.budget.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.ui.composants_communs.ChampUniversel
import com.xburnsx.toutiebudget.data.modeles.Compte
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.virement.VirementErrorMessages
import java.text.NumberFormat
import java.util.Locale

/**
 * 🎯 CLAVIER BUDGET ENVELOPPE - RÉUTILISE LE CLAVIER EXISTANT
 *
 * Reproduit le comportement Flutter Budget :
 * - Clic sur enveloppe → Bottom Sheet
 * - Votre ChampMontantUniversel (avec le clavier parfait)
 * - Sélection compte source + détails enveloppe
 * - Bouton "Assigner à [Nom Enveloppe]"
 */

data class CompteBudget(
    val id: String,
    val nom: String,
    val pretAPlacer: Double,
    val couleur: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClavierBudgetEnveloppe(
    enveloppe: EnveloppeUi,
    comptesDisponibles: List<CompteBudget>,
    comptePreselectionne: CompteBudget? = null,
    onAssigner: (montantCentimes: Long, compteSourceId: String) -> Unit,
    onFermer: () -> Unit
) {
    var montantCentimes by remember { mutableLongStateOf(0L) }
    var compteSelectionne by remember { mutableStateOf(comptePreselectionne) }
    var showSelecteurCompte by remember { mutableStateOf(false) }

    val formateurMonetaire = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)

    // 🎨 DESIGN BOTTOM SHEET COMME FLUTTER
    ModalBottomSheet(
        onDismissRequest = onFermer,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
        windowInsets = WindowInsets(0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // === EN-TÊTE AVEC DÉTAILS ENVELOPPE ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = enveloppe.nom,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Solde actuel avec bulle colorée si provenance
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Solde actuel :",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        // Bulle colorée si argent provenant d'un compte ET solde > 0
                        if (enveloppe.couleurProvenance != null && enveloppe.solde > 0.001) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = Color(android.graphics.Color.parseColor(enveloppe.couleurProvenance)),
                                        shape = CircleShape
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = formateurMonetaire.format(enveloppe.solde),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            Text(
                                text = formateurMonetaire.format(enveloppe.solde),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Objectif si défini
                    if (enveloppe.objectif > 0) {
                        Text(
                            text = "Objectif : ${formateurMonetaire.format(enveloppe.objectif)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Barre de progression vers l'objectif (utilise alloué cumulatif pour cohérence avec ObjectifCalculator)
                        val progression = (enveloppe.alloueCumulatif / enveloppe.objectif).coerceIn(0.0, 1.0) // ← MODIFIÉ : alloueCumulatif
                        LinearProgressIndicator(
                            progress = { progression.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = when {
                                progression >= 1.0 -> Color(0xFF4CAF50) // Vert
                                progression > 0.0 -> Color(0xFFFFC107) // Jaune
                                else -> Color(0xFF9E9E9E) // Gris
                            },
                        )
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // === VOTRE CHAMP MONTANT PARFAIT (RÉUTILISÉ) ===
            ChampUniversel(
                valeur = montantCentimes,
                onValeurChange = { nouveauMontant ->
                    montantCentimes = nouveauMontant
                },
                libelle = "Montant à assigner",
                utiliserClavier = true,
                isMoney = true,
                icone = Icons.Default.TrendingUp,
                estObligatoire = true,
                modifier = Modifier.fillMaxWidth()
            )

            // === SÉLECTEUR DE COMPTE SOURCE ===
            Text(
                text = "Compte source",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSelecteurCompte = true },
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Pastille colorée du compte
                        if (compteSelectionne != null) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        color = Color(android.graphics.Color.parseColor(compteSelectionne!!.couleur)),
                                        shape = CircleShape
                                    )
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }

                        Column {
                            Text(
                                text = compteSelectionne?.nom ?: "Sélectionner un compte",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = if (compteSelectionne != null) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outline
                                }
                            )

                            if (compteSelectionne != null) {
                                Text(
                                    text = "Prêt à placer : ${formateurMonetaire.format(compteSelectionne!!.pretAPlacer)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Sélectionner",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // === BOUTONS ACTIONS RAPIDES ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bouton Assigner Objectif
                OutlinedButton(
                    onClick = {
                        // Remplir automatiquement avec le montant suggéré ou l'objectif complet
                        val montantAFill = if (enveloppe.versementRecommande > 0) {
                            enveloppe.versementRecommande
                        } else if (enveloppe.objectif > 0) {
                            enveloppe.objectif
                        } else {
                            0.0
                        }
                        if (montantAFill > 0) {
                            montantCentimes = (montantAFill * 100).toLong()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = enveloppe.objectif > 0, // Désactivé si pas d'objectif
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        disabledContentColor = MaterialTheme.colorScheme.outline
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (enveloppe.objectif > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Objectif",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        if (enveloppe.objectif > 0) {
                            Text(
                                text = if (enveloppe.versementRecommande > 0) {
                                    formateurMonetaire.format(enveloppe.versementRecommande)
                                } else {
                                    formateurMonetaire.format(enveloppe.objectif)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Bouton Détails
                OutlinedButton(
                    onClick = {
                        // TODO: Ouvrir la page de détails de l'enveloppe
                        // ou afficher un dialog avec les détails complets
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Détails",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Historique",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // === BOUTON ASSIGNER ===
            Button(
                onClick = {
                    // Validation avec messages centralisés
                    when {
                        compteSelectionne == null -> {
                            // Afficher erreur : compte non sélectionné
                            // (Cette erreur sera gérée par le BudgetViewModel)
                        }
                        montantCentimes <= 0 -> {
                            // Afficher erreur : montant invalide
                            // (Cette erreur sera gérée par le BudgetViewModel)
                        }
                        else -> {
                            onAssigner(montantCentimes, compteSelectionne!!.id)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = compteSelectionne != null &&
                        montantCentimes > 0 &&
                        montantCentimes <= (compteSelectionne?.pretAPlacer?.times(100)?.toLong() ?: 0L),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Assigner à ${enveloppe.nom}",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Validation des fonds disponibles - UTILISE LES MESSAGES CENTRALISÉS
            if (compteSelectionne != null && montantCentimes > 0) {
                val montantDouble = montantCentimes / 100.0
                if (montantDouble > compteSelectionne!!.pretAPlacer) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = VirementErrorMessages.ClavierBudget.fondsInsuffisants(compteSelectionne!!.pretAPlacer),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // === DIALOG SÉLECTION DE COMPTE ===
    if (showSelecteurCompte) {
        AlertDialog(
            onDismissRequest = { showSelecteurCompte = false },
            title = { Text("Choisir le compte source") },
            text = {
                Column {
                    comptesDisponibles.forEach { compte ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    compteSelectionne = compte
                                    showSelecteurCompte = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Pastille colorée
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        color = Color(android.graphics.Color.parseColor(compte.couleur)),
                                        shape = CircleShape
                                    )
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = compte.nom,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Prêt à placer : ${formateurMonetaire.format(compte.pretAPlacer)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }

                        if (compte != comptesDisponibles.last()) {
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSelecteurCompte = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}