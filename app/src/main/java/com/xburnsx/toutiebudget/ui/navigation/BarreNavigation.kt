package com.xburnsx.toutiebudget.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BarreNavigationFlottante(navController: NavHostController) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .height(64.dp)
                .background(
                    color = Color(0xFF1A1A1A),
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            Screen.items.forEach { screen ->
                val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                ElementBarreNavigationPersonnalise(
                    screen = screen,
                    isSelected = isSelected,
                    onClick = {
                        navController.navigate(screen.route) {
                            popUpTo(Screen.Budget.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun RowScope.ElementBarreNavigationPersonnalise(
    screen: Screen,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "Press Scale"
    )

    val itemWidth by animateDpAsState(
        targetValue = if (isSelected) 140.dp else 56.dp,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
        label = "Item Width"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "Background Color"
    )

    Box(
        modifier = Modifier
            .width(itemWidth)
            .height(56.dp)
            .scale(pressScale)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(percent = 50)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            val iconSize by animateDpAsState(
                targetValue = if (isSelected) 42.dp else 32.dp,
                animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
                label = "Icon Size"
            )
            val iconTint by animateColorAsState(
                targetValue = if (isSelected) Color.White else Color.LightGray,
                animationSpec = tween(durationMillis = 300),
                label = "Icon Tint"
            )

            Icon(
                imageVector = screen.icon,
                contentDescription = screen.title,
                modifier = Modifier.size(iconSize),
                tint = iconTint
            )

            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(animationSpec = tween(delayMillis = 150)) + slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth / 2 },
                    animationSpec = tween(delayMillis = 150)
                ),
                exit = fadeOut(animationSpec = tween(durationMillis = 150)) + slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth / 2 },
                    animationSpec = tween(durationMillis = 150)
                )
            ) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = screen.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.animateContentSize()
                )
            }
        }
    }
}

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Budget : Screen("budget", "Budget", Icons.Default.Home)
    object Comptes : Screen("comptes", "Comptes", Icons.Default.Wallet)
    object NouvelleTransaction : Screen("nouvelle_transaction", "Ajouter", Icons.Default.Add)
    object Statistiques : Screen("statistiques", "Stats", Icons.Default.BarChart)

    companion object {
        val items = listOf(Budget, Comptes, NouvelleTransaction, Statistiques)
    }
}