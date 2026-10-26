package com.xburnsx.toutiebudget.ui.virement.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.xburnsx.toutiebudget.ui.budget.EnveloppeUi
import com.xburnsx.toutiebudget.ui.budget.StatutObjectif
import com.xburnsx.toutiebudget.data.modeles.CompteCheque
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.text.NumberFormat
import java.util.Locale
import androidx.core.graphics.toColorInt

/**
 * Sélecteur d'enveloppe dédié à la page de virement.
 */
@Composable
fun SelecteurEnveloppeVirement(
    enveloppes: Map<String, List<EnveloppeUi>>,
    enveloppeSelectionnee: EnveloppeUi?,
    onEnveloppeChange: (EnveloppeUi) -> Unit,
    modifier: Modifier = Modifier,
    obligatoire: Boolean = true,
    comptesPretAPlacer: List<CompteCheque> = emptyList(),
    // VA5 : destinations impossibles (id → raison), affichées après les autres et non sélectionnables
    incompatibles: Map<String, String> = emptyMap()
) {
    var dialogOuvert by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (obligatoire) "Enveloppe à utiliser" else "Enveloppe (optionnel)",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { dialogOuvert = true },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1F1F1F)
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = if (enveloppeSelectionnee != null) 1.dp else 2.dp,
                color = if (enveloppeSelectionnee != null) {
                    Color(0xFF404040)
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (enveloppeSelectionnee != null) {
                    Column {
                        Text(
                            text = enveloppeSelectionnee.nom,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH)
                                    .format(enveloppeSelectionnee.solde),
                                fontSize = 14.sp,
                                color = when {
                                    enveloppeSelectionnee.solde < 0 -> Color(0xFFEF4444)
                                    MoneyFormatter.isAmountZero(enveloppeSelectionnee.solde) -> Color.Gray
                                    else -> Color(0xFF10B981)
                                },
                                fontWeight = FontWeight.Medium
                            )
                            if (enveloppeSelectionnee.objectif > 0) {
                                Text(
                                    text = "Objectif: " + NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).format(enveloppeSelectionnee.objectif),
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = if (obligatoire) "Sélectionner une enveloppe" else "Aucune enveloppe",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Ouvrir sélecteur",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
    }

    if (dialogOuvert) {
        DialogSelectionEnveloppeVirement(
            enveloppes = enveloppes,
            comptesPretAPlacer = comptesPretAPlacer,
            incompatibles = incompatibles,
            onEnveloppeSelectionnee = {
                onEnveloppeChange(it)
                dialogOuvert = false
            },
            onDismiss = { dialogOuvert = false }
        )
    }
}

@Composable
private fun DialogSelectionEnveloppeVirement(
    enveloppes: Map<String, List<EnveloppeUi>>,
    comptesPretAPlacer: List<CompteCheque>,
    incompatibles: Map<String, String>,
    onEnveloppeSelectionnee: (EnveloppeUi) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 800.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Choisir une enveloppe",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LazyColumn {
                    // Afficher la catégorie "Prêt à placer" en premier pour tous les comptes chèque
                    val comptesChequeAvecPretAPlacer = comptesPretAPlacer

                    if (comptesChequeAvecPretAPlacer.isNotEmpty()) {
                        item {
                            Text(
                                text = "Prêt à placer",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(comptesChequeAvecPretAPlacer.sortedBy { "pret_a_placer_${it.id}" in incompatibles }) { compte ->
                            ItemComptePretAPlacerVirement(
                                compte = compte,
                                raisonIncompatible = incompatibles["pret_a_placer_${compte.id}"],
                                onClick = {
                                    // Créer une EnveloppeUi virtuelle pour représenter ce compte spécifique
                                    val enveloppeCompte = EnveloppeUi(
                                        id = "pret_a_placer_${compte.id}",
                                        nom = "${compte.nom} - Prêt à placer",
                                        solde = compte.pretAPlacer,
                                        depense = 0.0,
                                        alloue = 0.0, // Pas d'allocation pour "prêt à placer"
                                        alloueCumulatif = 0.0, // ← NOUVEAU : Pas d'allocation cumulative non plus
                                        objectif = 0.0,
                                        couleurProvenance = compte.couleur,
                                        statutObjectif = StatutObjectif.VERT
                                    )
                                    onEnveloppeSelectionnee(enveloppeCompte)
                                }
                            )
                        }
                    }

                    // Afficher les autres catégories d'enveloppes
                    enveloppes.forEach { (nomCategorie, enveloppesCategorie) ->
                        item {
                            Text(
                                text = nomCategorie,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        // VA5 : compatibles avec la provenance d'abord
                        items(enveloppesCategorie.sortedBy { it.id in incompatibles }) { enveloppe ->
                            ItemEnveloppeVirement(
                                enveloppe = enveloppe,
                                raisonIncompatible = incompatibles[enveloppe.id],
                                onClick = { onEnveloppeSelectionnee(enveloppe) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemEnveloppeVirement(
    enveloppe: EnveloppeUi,
    raisonIncompatible: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .alpha(if (raisonIncompatible != null) 0.45f else 1f)
            .clickable(enabled = raisonIncompatible == null) { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = enveloppe.nom,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                raisonIncompatible?.let {
                    Text(text = it, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                }
            }
            
            // 🎨 AFFICHAGE DU MONTANT AVEC COULEUR DE PROVENANCE
            if (enveloppe.couleurProvenance != null && enveloppe.solde > 0.001) {
                // Bulle colorée bien ronde avec texte plus petit - SEULEMENT si solde > 0
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(enveloppe.couleurProvenance.toColorInt()),
                            shape = RoundedCornerShape(12.dp)  // ← BULLE BIEN RONDE
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)  // ← Padding normal pour bulle
                ) {
                    Text(
                        text = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).format(enveloppe.solde),
                        fontSize = 12.sp,  // ← Réduit de 14sp à 12sp
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                // Affichage normal sans couleur de provenance
                Text(
                    text = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).format(enveloppe.solde),
                    fontSize = 12.sp,  // ← Même taille que dans la bulle
                    fontWeight = FontWeight.Medium,
                    color = when {
                        enveloppe.solde < 0 -> Color(0xFFEF4444)
                        MoneyFormatter.isAmountZero(enveloppe.solde) -> Color.Gray
                        else -> Color(0xFF10B981)
                    }
                )
            }
        }
    }
}

@Composable
private fun ItemComptePretAPlacerVirement(
    compte: CompteCheque,
    raisonIncompatible: String? = null,
    onClick: () -> Unit
) {
    // Convertir la couleur hexadécimale du compte en Color
    val couleurCompte = try {
        Color(compte.couleur.toColorInt())
    } catch (
        _: Exception) {
        Color(0xFF10B981) // Couleur par défaut si la conversion échoue
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .alpha(if (raisonIncompatible != null) 0.45f else 1f)
            .clickable(enabled = raisonIncompatible == null) { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = compte.nom,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = couleurCompte
                )
                Text(
                    text = raisonIncompatible ?: "Prêt à placer",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            Text(
                text = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).format(compte.pretAPlacer),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = couleurCompte
            )
        }
    }
}