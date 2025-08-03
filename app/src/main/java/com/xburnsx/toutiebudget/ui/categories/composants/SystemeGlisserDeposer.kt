package com.xburnsx.toutiebudget.ui.categories.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import kotlin.math.abs

/**
 * Système de glisser-déposer avec indicateurs visuels
 */
@Composable
fun SystemeGlisserDeposerCategories(
    categories: List<String>,
    enveloppesParCategorie: Map<String, List<Enveloppe>>,
    onReordonner: (List<String>) -> Unit,
    contenuCategorie: @Composable (nomCategorie: String, enveloppes: List<Enveloppe>, isDragging: Boolean) -> Unit
) {
    var elementGlisse by remember { mutableStateOf<String?>(null) }
    var offsetY by remember { mutableStateOf(0f) }
    var hauteurItem by remember { mutableStateOf(120f) }
    var positionCible by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Couleurs du thème Material
    val couleurPrimaire = MaterialTheme.colorScheme.primary
    val couleurSurface = MaterialTheme.colorScheme.surfaceVariant
    val couleurContour = MaterialTheme.colorScheme.outline

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(categories.size + 1) { index ->
            when {
                // Slot d'insertion au début
                index == 0 -> {
                    val montrerIndicateur = positionCible == 0 && elementGlisse != null
                    if (montrerIndicateur) {
                        IndicateurZoneDepot(couleurPrimaire)
                    }
                }

                // Éléments normaux + slots d'insertion après chaque élément
                index <= categories.size -> {
                    val indexCategorie = index - 1
                    val nomCategorie = categories[indexCategorie]
                    val estGlisse = elementGlisse == nomCategorie
                    val enveloppes = enveloppesParCategorie[nomCategorie] ?: emptyList()

                    Column {
                        // L'élément de catégorie
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coordinates ->
                                    if (hauteurItem == 120f) {
                                        hauteurItem = coordinates.size.height.toFloat() +
                                                with(density) { 4.dp.toPx() }
                                    }
                                }
                                .graphicsLayer(
                                    translationY = if (estGlisse) offsetY else 0f,
                                    alpha = if (estGlisse) 0.85f else 1f,
                                    scaleX = if (estGlisse) 1.03f else 1f,
                                    scaleY = if (estGlisse) 1.03f else 1f,
                                    rotationZ = if (estGlisse) (offsetY * 0.01f).coerceIn(-2f, 2f) else 0f
                                )
                                .shadow(
                                    elevation = if (estGlisse) 12.dp else 2.dp,
                                    spotColor = if (estGlisse) couleurPrimaire.copy(alpha = 0.4f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .then(if (estGlisse) Modifier.zIndex(1f) else Modifier)
                                .background(
                                    color = when {
                                        estGlisse -> couleurSurface.copy(alpha = 0.1f)
                                        elementGlisse != null -> couleurContour.copy(alpha = 0.05f)
                                        else -> Color.Transparent
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .pointerInput(nomCategorie) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            elementGlisse = nomCategorie
                                            offsetY = 0f
                                            positionCible = null
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { _, dragAmount ->
                                            offsetY += dragAmount.y

                                            // Calculer dans quel "slot" on se trouve
                                            val indexActuel = categories.indexOf(nomCategorie)
                                            val positionEnPixels = indexActuel * hauteurItem + offsetY
                                            val nouveauSlot = kotlin.math.round(positionEnPixels / hauteurItem).toInt()
                                                .coerceIn(0, categories.size)

                                            positionCible = nouveauSlot

                                            // Limiter le mouvement
                                            val maxDeplacementVersLeBas = (categories.size - indexActuel) * hauteurItem
                                            val maxDeplacementVersLeHaut = -(indexActuel + 1) * hauteurItem

                                            offsetY = offsetY.coerceIn(
                                                maxDeplacementVersLeHaut,
                                                maxDeplacementVersLeBas
                                            )

                                            if (abs(offsetY) % hauteurItem < 20f) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        },
                                        onDragEnd = {
                                            if (elementGlisse != null && hauteurItem > 0 && positionCible != null) {
                                                val indexOriginal = categories.indexOf(elementGlisse!!)
                                                val nouvelIndex = if (positionCible!! > indexOriginal) {
                                                    positionCible!! - 1
                                                } else {
                                                    positionCible!!
                                                }.coerceIn(0, categories.size - 1)

                                                if (nouvelIndex != indexOriginal) {
                                                    val nouvelleListe = categories.toMutableList()
                                                    val item = nouvelleListe.removeAt(indexOriginal)
                                                    nouvelleListe.add(nouvelIndex, item)
                                                    onReordonner(nouvelleListe)

                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            }

                                            elementGlisse = null
                                            offsetY = 0f
                                            positionCible = null
                                        },
                                        onDragCancel = {
                                            elementGlisse = null
                                            offsetY = 0f
                                            positionCible = null
                                        }
                                    )
                                }
                        ) {
                            contenuCategorie(nomCategorie, enveloppes, estGlisse)
                        }

                        // Slot d'insertion après cet élément
                        val montrerIndicateurApres = positionCible == indexCategorie + 1 &&
                                elementGlisse != null &&
                                !estGlisse
                        if (montrerIndicateurApres) {
                            IndicateurZoneDepot(couleurPrimaire)
                        }
                    }
                }
            }
        }
    }
}


/**
 * Composant d'indicateur visuel pour montrer où l'élément va être déposé
 */
@Composable
private fun IndicateurZoneDepot(couleurPrimaire: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ligne à gauche
        Box(
            modifier = Modifier
                .weight(1f)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(couleurPrimaire.copy(alpha = 0.7f))
        )

        // Indicateur central
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(couleurPrimaire)
                .padding(horizontal = 8.dp)
        )

        // Ligne à droite
        Box(
            modifier = Modifier
                .weight(1f)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(couleurPrimaire.copy(alpha = 0.7f))
        )
    }
}