/*
 * ============================================================
 *  [Chemin simulé] ui/statistiques/StatistiquesScreen.kt
 * ------------------------------------------------------------
 *  Améliorations UI:
 *  - Animation des cartes avec staggered effect
 *  - Gradients et glassmorphism
 *  - Micro-interactions et feedback visuel
 *  - Amélioration de la hiérarchie visuelle
 *  - Transitions fluides
 *  - Design plus moderne et premium
 * ============================================================
 */

package com.xburnsx.toutiebudget.ui.statistiques

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatistiquesScreen(
    viewModel: StatistiquesViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    // Fournir le mapping tiers pour toute la page (incluant la modal)
    CompositionLocalProvider(LocalTiersMap provides uiState.tiersToNom) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when {
            uiState.isLoading -> {
                LoadingState()
            }
            uiState.erreur != null -> {
                ErrorState(uiState.erreur)
            }
            else -> {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(animationSpec = tween(600)) +
                            slideInVertically(animationSpec = tween(600)) { it / 4 }
                ) {
                    StatistiquesContent(uiState, viewModel)
                }
            }
        }
    }

    // Modal Transactions avec animation améliorée
    if (uiState.modalOuvert) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.fermerModalTransactions() },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            TransactionModal(uiState.modalTitre, uiState.modalTransactions)
        }
    }
    }
}

@Composable
private fun netGradient(totalNet: Double): Brush {
    return if (totalNet >= 0) {
        Brush.linearGradient(
            colors = listOf(Color(0x334CAF50), Color(0x664CAF50))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
        )
    }
}
// CompositionLocal pour exposer tiers->nom à la modal
val LocalTiersMap = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Chargement des statistiques…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(error: String?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingDown,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "Oups, une erreur est survenue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    error ?: "Erreur inconnue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun StatistiquesContent(
    uiState: StatistiquesUiState,
    viewModel: StatistiquesViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header avec période (inclut sélecteur dans la topbar)
        HeaderSection(uiState, viewModel)

        // KPIs améliorés
                    AnimatedKpiCards(uiState, viewModel)

        // Top 5 cards avec animation staggered
        AnimatedTopCards(uiState, viewModel)

        // Sélecteur retiré (désormais dans la topbar)

        // Graphique UNIQUE avec 2 barres par mois
        ChartsSection(uiState)
    }
}

@Composable
private fun HeaderSection(uiState: StatistiquesUiState, viewModel: StatistiquesViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "Statistiques",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                uiState.periode?.label ?: "Période",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            var moisSelectionne by remember(uiState.periode) { mutableStateOf(uiState.periode?.debut ?: java.util.Date()) }
            com.xburnsx.toutiebudget.ui.budget.composants.SelecteurMoisAnnee(
                moisSelectionne = moisSelectionne,
                onMoisChange = { date ->
                    moisSelectionne = date
                    val debut = java.util.Calendar.getInstance().apply {
                        time = date
                        set(java.util.Calendar.DAY_OF_MONTH, 1)
                        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                    }.time
                    val fin = java.util.Calendar.getInstance().apply {
                        time = debut
                        add(java.util.Calendar.MONTH, 1)
                        add(java.util.Calendar.MILLISECOND, -1)
                    }.time
                    val label = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.FRENCH).format(debut)
                        .replaceFirstChar { it.uppercase() }
                    viewModel.chargerPeriode(debut, fin, label)
                }
            )

            Icon(
                imageVector = Icons.Default.TrendingUp,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AnimatedKpiCards(uiState: StatistiquesUiState, viewModel: StatistiquesViewModel) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(800)) +
                slideInVertically(animationSpec = tween(800)) { it / 2 }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassmorphicKpiCard(
                modifier = Modifier.weight(1f),
                title = "Dépenses",
                value = MoneyFormatter.formatAmount(uiState.totalDepenses),
                icon = Icons.Default.TrendingDown,
                gradient = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    )
                ),
                delay = 0,
                onClick = { viewModel.ouvrirModalDepenses() }
            )

            GlassmorphicKpiCard(
                modifier = Modifier.weight(1f),
                title = "Revenus",
                value = MoneyFormatter.formatAmount(uiState.totalRevenus),
                icon = Icons.Default.TrendingUp,
                gradient = Brush.linearGradient(
                    colors = listOf(
                        Color(0x334CAF50), // vert translucide
                        Color(0x664CAF50)
                    )
                ),
                delay = 100,
                onClick = { viewModel.ouvrirModalRevenus() }
            )

            GlassmorphicKpiCard(
                modifier = Modifier.weight(1f),
                title = "Net",
                value = MoneyFormatter.formatAmount(uiState.totalNet),
                icon = Icons.Default.AccountBalance,
                gradient = netGradient(uiState.totalNet),
                delay = 200
            )
        }
    }
}

@Composable
private fun GlassmorphicKpiCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    gradient: Brush,
    delay: Long,
    onClick: (() -> Unit)? = null
) {
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_scale"
    )

    LaunchedEffect(Unit) {
        delay(delay)
        isVisible = true
    }

    Card(
        modifier = modifier
            .scale(scale)
            .height(120.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(bounded = true)
                    ) { onClick() }
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AnimatedTopCards(
    uiState: StatistiquesUiState,
    viewModel: StatistiquesViewModel
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(400)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(1000)) +
                slideInVertically(animationSpec = tween(1000)) { it / 3 }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Top Performances",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EnhancedListCard(
                    modifier = Modifier.weight(1f),
                    title = "Top 5 Enveloppes",
                    subtitle = "Dépenses par catégorie",
                    items = uiState.top5Enveloppes,
                    leadingTint = MaterialTheme.colorScheme.primary,
                    leadingIcon = Icons.Filled.Category,
                    total = uiState.totalDepenses,
                    onItemClick = { item ->
                        // item.id est maintenant un enveloppeId
                        viewModel.ouvrirModalTransactionsPourEnveloppe(item.id)
                    }
                )

                EnhancedListCard(
                    modifier = Modifier.weight(1f),
                    title = "Top 5 Tiers",
                    subtitle = "Dépenses par tiers",
                    items = uiState.top5Tiers,
                    leadingTint = MaterialTheme.colorScheme.secondary,
                    leadingIcon = Icons.Filled.Person,
                    total = uiState.totalDepenses,
                    onItemClick = { item ->
                        val tx = uiState.transactionsPeriode.filter {
                            it.type == com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Depense &&
                                    (it.tiersUtiliser == item.label)
                        }
                        viewModel.ouvrirModalTransactions("Transactions - ${item.label}", tx)
                    }
                )
            }

            // Courbe cumulée du mois (cashflow net) + MM7
            CashflowSection(uiState)
        }
    }
}
@Composable
private fun CashflowSection(uiState: StatistiquesUiState) {
    if (uiState.cashflowCumulMensuel.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Cashflow du mois (cumul) • MM7",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            val labelsAll = uiState.cashflowCumulMensuel.map { it.first }
            val cumulAll = uiState.cashflowCumulMensuel.map { it.second }
            val mm7All = uiState.moyenneMobile7Jours.map { it.second }
            val n = minOf(labelsAll.size, cumulAll.size, mm7All.size)
            val labels = labelsAll
            val cumul = cumulAll
            val mm7 = mm7All
            val allValues = cumul + mm7
            val minVal = (allValues.minOrNull() ?: 0.0)
            val maxVal = (allValues.maxOrNull() ?: 0.0)
            
            // CORRECTION: Forcer l'échelle à inclure 0.0 pour que le début du mois soit visible
            val paddingRatio = 0.15
            val range = (maxVal - minVal).coerceAtLeast(0.01)
            
            // Forcer l'échelle à inclure 0.0 pour que le début du mois soit visible
            val minY = if (maxVal <= 0) minVal * 1.2 else 0.0
            val maxY = if (minVal >= 0) maxVal * 1.2 else 0.0 + range * paddingRatio

            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
                // Légende + valeurs instantanées
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.width(18.dp).height(3.dp).background(MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp)))
                            Text("Cumul", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.width(18.dp).height(3.dp).background(Color(0xFFFFA000), RoundedCornerShape(2.dp)))
                            Text("MM7 (net)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val lastCumul = cumul.lastOrNull() ?: 0.0
                        val lastMm = mm7.lastOrNull() ?: 0.0
                        Text("Cumul: ${MoneyFormatter.formatAmount(lastCumul)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text("MM7: ${MoneyFormatter.formatAmount(lastMm)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFA000))
                    }
                }

                Spacer(Modifier.height(6.dp))

                // DEBUG: Afficher les vraies valeurs pour diagnostiquer
                Text(
                    "Debug: minY=${"%.2f".format(minY)}, maxY=${"%.2f".format(maxY)}, Cumul=${cumul.firstOrNull()?.let { "%.2f".format(it) } ?: "N/A"}, MM7=${mm7.firstOrNull()?.let { "%.2f".format(it) } ?: "N/A"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                
                // DEBUG: Afficher les positions calculées
                Text(
                    "Positions: Cumul=${cumul.firstOrNull()?.let { "%.2f".format(it) } ?: "N/A"} -> ${cumul.firstOrNull()?.let { ((it - minY) / (maxY - minY) * 100).toInt() } ?: 0}%, MM7=${mm7.firstOrNull()?.let { "%.2f".format(it) } ?: "N/A"} -> ${mm7.firstOrNull()?.let { ((it - minY) / (maxY - minY) * 100).toInt() } ?: 0}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )

                // Pré-calcul des couleurs
                val outlineCol = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                val cumulCol = MaterialTheme.colorScheme.error
                val mmCol = Color(0xFFFFA000)

                // Zone de dessin avec axe Y à gauche
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    // Axe Y (montants)
                    Column(
                        modifier = Modifier.width(56.dp).fillMaxHeight().padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(MoneyFormatter.formatAmount(maxY), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        if (minY < 0 && maxY > 0) Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(MoneyFormatter.formatAmount(minY), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                    val w = size.width
                    val h = size.height
                    val n = cumul.size.coerceAtLeast(1)
                    val stepX = if (n > 1) w / (n - 1) else 0f

                    // DEBUG: Vérifier que le Canvas a une taille
                    println("DEBUG Canvas: w=$w, h=$h, n=$n")

                    fun mapY(v: Double, isMM7: Boolean = false): Float {
                        // Éviter la division par zéro
                        if (maxY == minY) return h / 2f
                        
                        // Calculer le ratio normalisé sans contrainte
                        val ratio = (v - minY) / (maxY - minY)
                        
                        // Inverser l'axe Y (0 = haut, 1 = bas) et gérer les valeurs hors limites
                        val normalizedRatio = when {
                            ratio < 0.0 -> 0.0
                            ratio > 1.0 -> 1.0
                            else -> ratio
                        }
                        
                        // DEBUG: Afficher les calculs pour diagnostiquer
                        if (v == 0.0 || v == -68.0) {
                            println("DEBUG mapY: v=$v, minY=$minY, maxY=$maxY, ratio=$ratio, normalizedRatio=$normalizedRatio, h=$h")
                        }
                        
                        // Positionnement SIMPLE : négatifs en bas, positifs en haut
                        return h * (1f - normalizedRatio.toFloat())
                    }

                    // Axe zéro
                    if (minY < 0 && maxY > 0) {
                        val y0 = mapY(0.0, false)
                        drawLine(
                            color = outlineCol,
                            start = Offset(0f, y0),
                            end = Offset(w, y0),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Tracer cumul (rouge)
                    if (n >= 1) {
                        val path = Path()
                        path.moveTo(0f, mapY(cumul.first(), false))
                        for (i in 1 until n) {
                            path.lineTo(i * stepX, mapY(cumul[i], false))
                        }
                        drawPath(
                            path = path,
                            color = cumulCol,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Tracer MM7 (orange)
                    if (n >= 1) {
                        val path = Path()
                        path.moveTo(0f, mapY(mm7.first(), true))
                        for (i in 1 until n) {
                            path.lineTo(i * stepX, mapY(mm7[i], true))
                        }
                        drawPath(
                            path = path,
                            color = mmCol,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    }
                    // Fin Canvas
                }

                Spacer(Modifier.height(6.dp))

                // Labels jours réduits (max 10)
                val maxLabels = 10
                val nLab = labels.size
                val step = kotlin.math.max(1, nLab / maxLabels)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    labels.forEachIndexed { i, l ->
                        val show = i % step == 0 || i == nLab - 1
                        Text(
                            if (show) l else "",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhancedListCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    items: List<TopItem>,
    leadingTint: Color,
    leadingIcon: ImageVector,
    total: Double,
    onItemClick: (TopItem) -> Unit
) {
    Card(
        modifier = modifier, // Hauteur libre pour afficher 5 éléments complets
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header compact
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = leadingTint
                )
            }

            // Items compacts
            val displayed = items.take(5)
            displayed.forEachIndexed { index, item ->
                CompactListItem(
                    item = item,
                    rank = index + 1,
                    color = leadingTint,
                    animationDelay = index * 50L,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
private fun CompactListItem(
    item: TopItem,
    rank: Int,
    color: Color,
    animationDelay: Long,
    onClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(animationDelay)
        isVisible = true
    }

    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true, color = color),
                onClick = onClick
            )
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Rang mini
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$rank",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }

        // Nom (petit) et montant (gros à droite)
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                MoneyFormatter.formatAmount(item.montant),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PeriodSelector(viewModel: StatistiquesViewModel) {
    var moisSelectionne by remember { mutableStateOf(Date()) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Période d'analyse",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Sélecteur de mois/année (même composant que sur l'écran Budget)
        com.xburnsx.toutiebudget.ui.budget.composants.SelecteurMoisAnnee(
            moisSelectionne = moisSelectionne,
            onMoisChange = { date ->
                moisSelectionne = date
                val debut = Calendar.getInstance().apply {
                    time = date
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.time
                val fin = Calendar.getInstance().apply {
                    time = debut
                    add(Calendar.MONTH, 1)
                    add(Calendar.MILLISECOND, -1)
                }.time
                val label = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.FRENCH).format(debut)
                    .replaceFirstChar { it.uppercase() }
                viewModel.chargerPeriode(debut, fin, label)
            }
        )
    }
}

@Composable
private fun ChartsSection(uiState: StatistiquesUiState) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(600)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(1000)) +
                slideInVertically(animationSpec = tween(1000)) { it / 4 }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                "Évolution sur 6 mois",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // UN SEUL graphique avec 2 barres par mois
            DoubleBarChart(
                depenses = uiState.depenses6DerniersMois,
                revenus = uiState.revenus6DerniersMois
            )

            // Répartition
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Répartition des dépenses",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                EnhancedPieChart(items = uiState.top5Enveloppes)
            }
        }
    }
}

@Composable
private fun DoubleBarChart(
    depenses: List<Pair<String, Double>>,
    revenus: List<Pair<String, Double>>
) {
    if (depenses.isEmpty()) return

    // Trouver le max entre dépenses et revenus pour l'échelle
    val maxDepenses = depenses.maxOfOrNull { it.second } ?: 0.0
    val maxRevenus = revenus.maxOfOrNull { it.second } ?: 0.0
    val max = maxOf(maxDepenses, maxRevenus).coerceAtLeast(0.01)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Légende
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Légende Revenus (gauche) en vert
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                Color(0xFF4CAF50),
                                RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Revenus",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.width(16.dp))

                // Légende Dépenses (droite) en rouge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                MaterialTheme.colorScheme.error,
                                RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Dépenses",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Zone des barres
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                depenses.forEachIndexed { index, (label, depenseValue) ->
                    val revenuValue = revenus.getOrNull(index)?.second ?: 0.0

                    val depenseRatio = (depenseValue / max).toFloat().coerceIn(0f, 1f)
                    val revenuRatio = (revenuValue / max).toFloat().coerceIn(0f, 1f)

                    val animatedDepenseRatio by animateFloatAsState(
                        targetValue = depenseRatio,
                        animationSpec = tween(1000, delayMillis = index * 100),
                        label = "depense-height"
                    )

                    val animatedRevenuRatio by animateFloatAsState(
                        targetValue = revenuRatio,
                        animationSpec = tween(1000, delayMillis = index * 100 + 50),
                        label = "revenu-height"
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Deux barres côte à côte pour ce mois
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Barre Revenus (à gauche) en vert
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height((100 * animatedRevenuRatio).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(Color(0xFF4CAF50))
                            )

                            // Barre Dépenses (à droite) en rouge
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height((100 * animatedDepenseRatio).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(MaterialTheme.colorScheme.error)
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun EnhancedPieChart(items: List<TopItem>) {
    if (items.isEmpty()) return

    val total = items.sumOf { it.montant }.coerceAtLeast(0.01)
    // Palette forcée en rouge pour toutes les catégories
    val rouge = MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items.forEachIndexed { i, item ->
                val pct = (item.montant / total).toFloat().coerceIn(0f, 1f)
                val pct100 = (pct * 100).roundToInt()
                val color = rouge

                var isVisible by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    delay(i * 150L)
                    isVisible = true
                }

                val animatedProgress by animateFloatAsState(
                    targetValue = if (isVisible) pct else 0f,
                    animationSpec = tween(800, delayMillis = i * 100),
                    label = "pie_progress"
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Indicateur couleur amélioré
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            color,
                                            color.copy(alpha = 0.7f)
                                        )
                                    )
                                )
                                .border(
                                    2.dp,
                                    MaterialTheme.colorScheme.surface,
                                    CircleShape
                                )
                        )

                        Text(
                            item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                MoneyFormatter.formatAmount(item.montant),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "$pct100%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Barre de progression avec gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            color,
                                            color.copy(alpha = 0.8f)
                                        )
                                    )
                                )
                        )
                    }
                }
            }

            // Total avec style amélioré
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Total des dépenses",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        MoneyFormatter.formatAmount(total),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionModal(titre: String, transactions: List<Transaction>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header de la modal
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                titre,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Text(
                "${transactions.size} transaction${if (transactions.size > 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        // Liste des transactions avec LazyColumn pour permettre le défilement
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp), // Limiter la hauteur maximale pour éviter que la modale soit trop grande
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(transactions.size) { index ->
                val tx = transactions[index]
                var isVisible by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    delay(index * 50L)
                    isVisible = true
                }

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(animationSpec = tween(300)) +
                            slideInVertically(animationSpec = tween(300)) { it / 4 }
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                // Utiliser la même logique que HistoriqueCompteViewModel
                                val nomTiersBrut = tx.tiersUtiliser ?: "Tiers inconnu"
                                
                                                             // Générer le libellé descriptif comme dans HistoriqueCompteViewModel
                                 val libelleDescriptif = when (tx.type) {
                                     com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Pret -> "Prêt accordé à : $nomTiersBrut"
                                     com.xburnsx.toutiebudget.data.modeles.TypeTransaction.RemboursementRecu -> "Remboursement reçu de : $nomTiersBrut"
                                     com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Emprunt -> "Dette contractée de : $nomTiersBrut"
                                     com.xburnsx.toutiebudget.data.modeles.TypeTransaction.RemboursementDonne -> "Remboursement donné à : $nomTiersBrut"
                                     com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Paiement -> "Paiement : $nomTiersBrut"
                                     com.xburnsx.toutiebudget.data.modeles.TypeTransaction.PaiementEffectue -> "Paiement effectue : $nomTiersBrut"
                                     com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Depense -> nomTiersBrut
                                     com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Revenu -> nomTiersBrut
                                     else -> nomTiersBrut
                                 }
                                
                                Text(
                                    libelleDescriptif,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                                             // Afficher le type de transaction
                                 val typeTransaction = when (tx.type) {
                                     com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Depense -> "Dépense"
                                     com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Revenu -> "Revenu"
                                     com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Pret -> "Prêt accordé"
                                     com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Emprunt -> "Dette contractée"
                                     com.xburnsx.toutiebudget.data.modeles.TypeTransaction.RemboursementRecu -> "Remboursement reçu"
                                     com.xburnsx.toutiebudget.data.modeles.TypeTransaction.RemboursementDonne -> "Remboursement donné"
                                     com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Paiement -> "Paiement reçu"
                                     com.xburnsx.toutiebudget.data.modeles.TypeTransaction.PaiementEffectue -> "Paiement effectué"
                                     else -> "Transaction"
                                 }
                                
                                Text(
                                    typeTransaction,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                                                     // Couleur du montant selon le type (comme dans HistoriqueItem)
                             val couleurMontant = when (tx.type) {
                                 com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Depense -> Color.Red
                                 com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Pret -> Color.Red        // PRET = ROUGE (argent qui sort)
                                 com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Revenu -> Color.Green
                                 com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Emprunt -> Color.Green   // EMPRUNT = VERT (argent qui entre)
                                 com.xburnsx.toutiebudget.data.modeles.TypeTransaction.RemboursementRecu -> Color.Green  // REMBOURSEMENT REÇU = VERT (argent qui rentre)
                                 com.xburnsx.toutiebudget.data.modeles.TypeTransaction.RemboursementDonne -> Color.Red   // REMBOURSEMENT DONNÉ = ROUGE (argent qui sort)
                                 com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Paiement -> Color.Red    // PAIEMENT = ROUGE (argent qui sort)
                                 com.xburnsx.toutiebudget.data.modeles.TypeTransaction.PaiementEffectue -> Color.Red    // PAIEMENT EFFECTUE = ROUGE (argent qui sort)
                                 else -> Color.Yellow
                             }

                            Text(
                                MoneyFormatter.formatAmount(tx.montant),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = couleurMontant
                            )
                        }
                    }
                }
            }
        }

        // Espacement pour le gesture handle
        Spacer(Modifier.height(16.dp))
    }
}

// Utilitaires période (ORIGINAUX)
private fun computePeriodeFromPreset(preset: String): Triple<Date, Date, String> {
    val now = Date()
    return when (preset) {
        "30 jours" -> {
            val fin = now
            val debut = Calendar.getInstance().apply {
                time = fin
                add(Calendar.DAY_OF_YEAR, -29)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.time
            Triple(debut, fin, "30 derniers jours")
        }
        "90 jours" -> {
            val fin = now
            val debut = Calendar.getInstance().apply {
                time = fin
                add(Calendar.DAY_OF_YEAR, -89)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.time
            Triple(debut, fin, "90 derniers jours")
        }
        else -> { // "Mois en cours"
            val cal = Calendar.getInstance().apply {
                time = now
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val debut = cal.time
            val fin = Calendar.getInstance().apply { time = debut; add(Calendar.MONTH, 1); add(Calendar.MILLISECOND, -1) }.time
            Triple(debut, fin, "Mois en cours")
        }
    }
}
