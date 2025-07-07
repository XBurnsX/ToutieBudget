package com.xburnsx.toutiebudget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xburnsx.toutiebudget.ui.theme.ToutieBudgetTheme

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
    ToutieBudgetTheme {
        Scaffold(
            bottomBar = {
                // Appel de la nouvelle barre flottante
                FloatingTransformingBottomBar(navController = navController)
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
fun FloatingTransformingBottomBar(navController: NavHostController) {
    // Utilisation d'un Box pour centrer horizontalement et ajouter le padding de la barre système
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding() // Maintient la barre au-dessus de la barre système
            .padding(horizontal = 16.dp, vertical = 8.dp), // Padding pour "flotter" sur les côtés
        contentAlignment = Alignment.Center // Centrer la barre horizontalement
    ) {
        // La barre de navigation principale (Maintenant une Row flottante)
        Row(
            modifier = Modifier
                .wrapContentWidth() // La largeur s'adapte au contenu, mais max fullWidth - padding
                .height(64.dp) // Hauteur de la barre
                .background(
                    color = Color(0xFF1A1A1A), // Fond très sombre
                    shape = RoundedCornerShape(32.dp) // Coins très arrondis sur tous les côtés pour un effet "pilule"
                )
                .padding(horizontal = 8.dp), // Padding interne
            horizontalArrangement = Arrangement.SpaceAround, // Ou SpaceEvenly
            verticalAlignment = Alignment.CenterVertically
        ) {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            Screen.items.forEach { screen ->
                val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                CustomAnimatedBottomBarItem(
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
fun RowScope.CustomAnimatedBottomBarItem(
    screen: Screen,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f, // Contraction subtile
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "Press Scale"
    )

    // Animation de la largeur de l'élément (expansion/contraction)
    val itemWidth by animateDpAsState(
        targetValue = if (isSelected) 120.dp else 56.dp, // Largeur quand sélectionné vs non sélectionné
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
        label = "Item Width"
    )

    // Animation de la couleur de fond de l'élément
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Color.Red.copy(alpha = 0.8f) else Color.Transparent, // Rouge pour sélectionné, transparent sinon
        animationSpec = tween(durationMillis = 300),
        label = "Background Color"
    )

    Box(
        modifier = Modifier
            .width(itemWidth) // Appliquer la largeur animée
            .height(56.dp) // Hauteur fixe de l'élément
            .scale(pressScale) // Appliquer l'effet de pression
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(percent = 50) // Forme de pilule pour l'élément sélectionné
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Pas de ripple par défaut, on gère nos propres animations
                onClick = onClick
            )
            .padding(horizontal = 4.dp), // Padding interne légèrement réduit pour plus de compacité
        contentAlignment = Alignment.CenterStart // Aligner le contenu au début
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icône
            val iconSize by animateDpAsState(
                targetValue = if (isSelected) 30.dp else 24.dp, // Icône plus grande si sélectionnée
                animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
                label = "Icon Size"
            )
            val iconTint by animateColorAsState(
                targetValue = if (isSelected) Color.White else Color.LightGray, // Blanc éclatant si sélectionné, gris clair sinon
                animationSpec = tween(durationMillis = 300),
                label = "Icon Tint"
            )

            Icon(
                imageVector = screen.icon,
                contentDescription = screen.title,
                modifier = Modifier.size(iconSize),
                tint = iconTint
            )

            // Texte (apparaît/disparaît)
            AnimatedVisibility(
                visible = isSelected, // Visible uniquement si sélectionné
                enter = fadeIn(animationSpec = tween(delayMillis = 150)) + slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth / 2 },
                    animationSpec = tween(delayMillis = 150)
                ),
                exit = fadeOut(animationSpec = tween(durationMillis = 150)) + slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth / 2 },
                    animationSpec = tween(durationMillis = 150)
                )
            ) {
                Spacer(Modifier.width(8.dp)) // Espace entre icône et texte
                Text(
                    text = screen.title,
                    color = Color.White, // Texte toujours blanc si visible
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.animateContentSize() // Animer la taille du texte si besoin
                )
            }
        }
    }
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