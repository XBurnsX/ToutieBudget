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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.data.modeles.Transaction
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

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
    CompositionLocalProvider(LocalTiersMap provides uiState.tiersIdToNom) {
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
// CompositionLocal pour exposer tiersId->nom à la modal
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
        // Header avec période
        HeaderSection(uiState.periode?.label ?: "Période")

        // KPIs améliorés
        AnimatedKpiCards(uiState)

        // Top 5 cards avec animation staggered
        AnimatedTopCards(uiState, viewModel)

        // Sélecteurs de période
        PeriodSelector(viewModel)

        // Graphique UNIQUE avec 2 barres par mois
        ChartsSection(uiState)
    }
}

@Composable
private fun HeaderSection(periodLabel: String) {
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
                periodLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Default.TrendingUp,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AnimatedKpiCards(uiState: StatistiquesUiState) {
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
                delay = 0
            )

            GlassmorphicKpiCard(
                modifier = Modifier.weight(1f),
                title = "Revenus",
                value = MoneyFormatter.formatAmount(uiState.totalRevenus),
                icon = Icons.Default.TrendingUp,
                gradient = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                    )
                ),
                delay = 100
            )

            GlassmorphicKpiCard(
                modifier = Modifier.weight(1f),
                title = "Net",
                value = MoneyFormatter.formatAmount(uiState.totalNet),
                icon = Icons.Default.AccountBalance,
                gradient = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    )
                ),
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
    delay: Long
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
            .height(120.dp),
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
                    style = MaterialTheme.typography.titleLarge,
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
                    subtitle = "Dépenses par partenaire",
                    items = uiState.top5Tiers,
                    leadingTint = MaterialTheme.colorScheme.secondary,
                    leadingIcon = Icons.Filled.Person,
                    total = uiState.totalDepenses,
                    onItemClick = { item ->
                        val tx = uiState.transactionsPeriode.filter {
                            it.type == com.xburnsx.toutiebudget.data.modeles.TypeTransaction.Depense &&
                                    (it.tiersId == item.id || it.tiers == item.label)
                        }
                        viewModel.ouvrirModalTransactions("Transactions - ${item.label}", tx)
                    }
                )
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
    val selectedPreset = remember { mutableStateOf("Mois en cours") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Période d'analyse",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Mois en cours", "30 jours", "90 jours").forEach { label ->
                val isSelected = selectedPreset.value == label

                AssistChip(
                    onClick = {
                        selectedPreset.value = label
                        val (debut, fin, labelPeriod) = computePeriodeFromPreset(label)
                        viewModel.chargerPeriode(debut, fin, labelPeriod)
                    },
                    label = {
                        Text(
                            label,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    ),
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = if (isSelected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.outlineVariant,
                        borderWidth = if (isSelected) 2.dp else 1.dp
                    ),
                    elevation = AssistChipDefaults.assistChipElevation(
                        elevation = if (isSelected) 4.dp else 0.dp
                    )
                )
            }
        }
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
                revenus = uiState.depenses6DerniersMois // Remplacez par uiState.revenus6DerniersMois
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
                // Légende Dépenses
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

                Spacer(Modifier.width(16.dp))

                // Légende Revenus
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                MaterialTheme.colorScheme.tertiary,
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
                            // Barre Dépenses
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height((100 * animatedDepenseRatio).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(MaterialTheme.colorScheme.error)
                            )

                            // Barre Revenus
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height((100 * animatedRevenuRatio).dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(MaterialTheme.colorScheme.tertiary)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Zone des labels (toujours visible)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                depenses.forEach { (label, _) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun EnhancedPieChart(items: List<TopItem>) {
    if (items.isEmpty()) return

    val total = items.sumOf { it.montant }.coerceAtLeast(0.01)
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.surfaceTint
    )

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
                val color = palette[i % palette.size]

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
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
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
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        MoneyFormatter.formatAmount(total),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
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
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                "${transactions.size} transaction${if (transactions.size > 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Liste des transactions avec animation
        transactions.forEachIndexed { index, tx ->
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
                            val tiersMap = LocalTiersMap.current
                            val nomTiers = tx.tiers ?: (tx.tiersId?.let { tiersMap[it] }) ?: "Tiers inconnu"
                            Text(
                                nomTiers,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                "Transaction",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Text(
                            MoneyFormatter.formatAmount(tx.montant),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
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