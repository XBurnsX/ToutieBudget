package com.xburnsx.toutiebudget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color // Import pour utiliser Color.Black, Color.Red
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xburnsx.toutiebudget.ui.theme.ToutieBudgetTheme // Assurez-vous que ce thème est bien défini

// Annotation pour indiquer que nous utilisons des API expérimentales
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ToutieBudgetApp()
        }
    }
}

@Composable
fun ToutieBudgetApp() {
    val navController = rememberNavController()
    ToutieBudgetTheme { // Votre thème gère les couleurs de base. Nous allons surcharger pour la bottom bar.
        Scaffold(
            bottomBar = {
                UniqueMagnificentBottomBar(navController = navController)
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Budget.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Budget.route) { BudgetScreen() }
                composable(Screen.Comptes.route) { ComptesScreen() }
                composable(Screen.NouvelleTransaction.route) { NouvelleTransactionScreen() }
                composable(Screen.Statistiques.route) { StatistiquesScreen() }
            }
        }
    }
}

@Composable
fun UniqueMagnificentBottomBar(navController: NavHostController) {
    Column { // Un Column pour empiler la ligne rouge et la NavigationBar
        // La barre rouge de séparation en haut
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp) // Fine ligne de séparation
                .background(Color.Red) // Rouge vif
        )
        NavigationBar(
            tonalElevation = 12.dp, // Plus d'élévation pour un effet "flottant"
            containerColor = Color(0xFF1A1A1A) // Fond très sombre, presque noir
        ) {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            Screen.items.forEach { screen ->
                val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                UniqueMagnificentNavigationBarItem(
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
fun RowScope.UniqueMagnificentNavigationBarItem(
    screen: Screen,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f, // Contraction plus prononcée
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "Press Scale"
    )

    NavigationBarItem(
        icon = {
            // Animation de l'échelle de l'icône
            val iconScale by animateFloatAsState(
                targetValue = if (isSelected) 1.2f else 1f, // Zoom plus important
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "Icon Scale"
            )

            // Animation de la taille de l'indicateur de fond (le cercle)
            val indicatorSize by animateDpAsState(
                targetValue = if (isSelected) 56.dp else 0.dp, // Cercle plus grand quand sélectionné
                animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
                label = "Indicator Size"
            )

            Box(
                modifier = Modifier
                    .size(56.dp) // Taille fixe pour l'espace de l'icône, peu importe si sélectionné ou non
                    .background(
                        color = Color.Transparent, // Le fond de l'item reste transparent
                        shape = CircleShape
                    )
                    .clip(CircleShape), // Empêche les débordements de l'indicateur si l'icône est trop grande
                contentAlignment = Alignment.Center
            ) {
                // L'indicateur de sélection: un cercle qui apparaît et disparaît
                Box(
                    modifier = Modifier
                        .size(indicatorSize)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), // Un rouge plus subtil, semi-transparent
                            shape = CircleShape
                        )
                )

                // L'icône elle-même
                Icon(
                    imageVector = screen.icon,
                    contentDescription = screen.title,
                    modifier = Modifier
                        .size(28.dp) // Icône légèrement plus grande
                        .scale(iconScale), // Appliquer l'échelle animée à l'icône
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray // Icône rouge si sélectionnée, sinon gris clair
                )
            }
        },
        label = {
            val textColor = animateColorAsState(
                targetValue = if (isSelected) Color.White else Color.Gray, // Texte blanc si sélectionné, sinon gris foncé
                animationSpec = tween(durationMillis = 200),
                label = "Text Color"
            )
            val textAlpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.6f, // Texte plus transparent si non sélectionné
                animationSpec = tween(durationMillis = 200),
                label = "Text Alpha"
            )
            Text(
                text = screen.title,
                color = textColor.value.copy(alpha = textAlpha),
                style = MaterialTheme.typography.labelMedium // Style de texte plus petit
            )
        },
        selected = isSelected,
        onClick = onClick,
        interactionSource = interactionSource,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color.Transparent, // Géré par le tint de l'icône
            selectedTextColor = Color.White,       // Géré par la couleur du texte
            indicatorColor = Color.Transparent,    // Géré par notre Box personnalisée
            unselectedIconColor = Color.Transparent, // Géré par le tint de l'icône
            unselectedTextColor = Color.Gray       // Géré par la couleur du texte
        ),
        modifier = Modifier.scale(pressScale) // Appliquer la mise à l'échelle de pression à l'ensemble de l'élément
    )
}

// Définition des écrans de l'application (inchangée)
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

// Écrans de l'application (inchangés)
@Composable
fun BudgetScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Écran Budget", color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun ComptesScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Écran Comptes", color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun NouvelleTransactionScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Nouvelle Transaction", color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun StatistiquesScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Statistiques", color = MaterialTheme.colorScheme.onBackground)
    }
}