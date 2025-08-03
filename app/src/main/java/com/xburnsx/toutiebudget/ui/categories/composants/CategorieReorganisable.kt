// chemin/simule: /ui/categories/composants/CategorieReorganisable.kt
package com.xburnsx.toutiebudget.ui.categories.composants

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.xburnsx.toutiebudget.data.modeles.Enveloppe

/**
 * Composant pour afficher une catégorie avec capacité de réorganisation par drag & drop.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyItemScope.CategorieReorganisable(
    nomCategorie: String,
    enveloppes: List<Enveloppe>,
    position: Int,
    totalCategories: Int,
    isModeReorganisation: Boolean,
    isEnDeplacement: Boolean = false,
    onAjouterEnveloppeClick: () -> Unit,
    onObjectifClick: (Enveloppe) -> Unit,
    onSupprimerEnveloppe: (Enveloppe) -> Unit = {},
    onSupprimerObjectifEnveloppe: (Enveloppe) -> Unit = {},
    onSupprimerCategorie: (String) -> Unit = {},
    onDeplacerCategorie: (String, Int) -> Unit = { _, _ -> }, // ✅ Correction : fonction avec 2 paramètres
    onDebuterDeplacement: (String) -> Unit = {},
    onTerminerDeplacement: () -> Unit = {},
    // 🆕 PARAMÈTRES POUR LE DÉPLACEMENT D'ENVELOPPES
    onDeplacerEnveloppe: (String, Int) -> Unit = { _, _ -> },
    onDebuterDeplacementEnveloppe: (String) -> Unit = {},
    onTerminerDeplacementEnveloppe: () -> Unit = {},
    enveloppeEnDeplacement: String? = null,
    modifier: Modifier = Modifier
) {

    val hapticFeedback = LocalHapticFeedback.current

    // Animations pour le drag & drop
    val elevation by animateDpAsState(
        targetValue = if (isEnDeplacement) 8.dp else 0.dp,
        label = "elevation"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isEnDeplacement) 0.8f else 1f,
        label = "alpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (isEnDeplacement) 1.02f else 1f,
        label = "scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .shadow(elevation)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .zIndex(if (isEnDeplacement) 1f else 0f)
            .animateItemPlacement(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isModeReorganisation)
                Color(0xFF3A3A3C) else Color(0xFF2C2C2E)
        ),
        border = if (isModeReorganisation)
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column {
            // En-tête de la catégorie avec contrôles de déplacement
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Nom de la catégorie
                Text(
                    text = nomCategorie,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Contrôles de réorganisation
                if (isModeReorganisation) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bouton monter
                        IconButton(
                            onClick = {
                                System.err.println("🚨🚨🚨 [CategorieReorganisable] CLIC BOUTON MONTER DÉTECTÉ ! 🚨🚨🚨")
                                System.err.println("🚨🚨🚨 [CategorieReorganisable] Catégorie: '$nomCategorie', Position: $position 🚨🚨🚨")
                                println("🔄 [CategorieReorganisable] CLIC BOUTON MONTER pour '$nomCategorie'")
                                if (position > 0) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    println("🔄 [CategorieReorganisable] Déplacement '${nomCategorie}' de position $position vers ${position - 1}")
                                    onDeplacerCategorie(nomCategorie, position - 1)
                                } else {
                                    println("❌ [CategorieReorganisable] Impossible de monter - déjà en première position")
                                }
                            },
                            enabled = position > 0
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Monter la catégorie",
                                tint = if (position > 0) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }



                        // Bouton descendre
                        IconButton(
                            onClick = {
                                System.err.println("🚨🚨🚨 [CategorieReorganisable] CLIC BOUTON DESCENDRE DÉTECTÉ ! 🚨🚨🚨")
                                System.err.println("🚨🚨🚨 [CategorieReorganisable] Catégorie: '$nomCategorie', Position: $position 🚨🚨🚨")
                                println("🔄 [CategorieReorganisable] CLIC BOUTON DESCENDRE pour '$nomCategorie'")
                                if (position < totalCategories - 1) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    println("🔄 [CategorieReorganisable] Déplacement '${nomCategorie}' de position $position vers ${position + 1}")
                                    onDeplacerCategorie(nomCategorie, position + 1)
                                } else {
                                    println("❌ [CategorieReorganisable] Impossible de descendre - déjà en dernière position")
                                }
                            },
                            enabled = position < totalCategories - 1
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Descendre la catégorie",
                                tint = if (position < totalCategories - 1) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }

                        // Poignée de drag (pour drag & drop avancé)
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Déplacer par glisser-déposer",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .pointerInput(nomCategorie) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDebuterDeplacement(nomCategorie)
                                        },
                                        onDragEnd = {
                                            onTerminerDeplacement()
                                        }
                                    ) { _, _ ->
                                        // Logique de drag en cours...
                                        // Cette partie peut être étendue pour un drag & drop plus sophistiqué
                                    }
                                }
                        )
                    }
                } else {
                    // Affichage normal (nombre d'enveloppes)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "${enveloppes.size}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Contenu de la catégorie - TOUJOURS VISIBLE avec fonctionnalités adaptées
            if (isModeReorganisation) {
                // En mode réorganisation : affichage simplifié avec déplacement d'enveloppes
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1C1C1E))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "${enveloppes.size} enveloppe${if (enveloppes.size > 1) "s" else ""}",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Liste des enveloppes avec boutons de déplacement
                    enveloppes.forEachIndexed { indexEnv, enveloppe ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = enveloppe.nom,
                                color = Color.White,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Bouton monter enveloppe
                                IconButton(
                                    onClick = {
                                        if (indexEnv > 0) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDeplacerEnveloppe(enveloppe.id, indexEnv - 1)
                                        }
                                    },
                                    enabled = indexEnv > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Monter l'enveloppe",
                                        tint = if (indexEnv > 0) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }



                                // Bouton descendre enveloppe
                                IconButton(
                                    onClick = {
                                        if (indexEnv < enveloppes.size - 1) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDeplacerEnveloppe(enveloppe.id, indexEnv + 1)
                                        }
                                    },
                                    enabled = indexEnv < enveloppes.size - 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Descendre l'enveloppe",
                                        tint = if (indexEnv < enveloppes.size - 1) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Mode normal : affichage complet des enveloppes
                // On utilise CategorieCard ou on recrée l'affichage normal ici
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Mode normal - Utilisez CategorieCard pour l'affichage complet",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
