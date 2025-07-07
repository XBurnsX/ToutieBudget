package com.xburnsx.toutiebudget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
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
    // Création du contrôleur de navigation
    val navController = rememberNavController()
    
    // Application du thème personnalisé
    ToutieBudgetTheme {
        // Scaffold est la structure de base de l'application
        // Elle contient la barre de navigation en bas
        Scaffold(
            bottomBar = {
                // Barre de navigation en bas
                NavigationBar {
                    // État actuel de la navigation
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    // Pour chaque élément de navigation
                    Screen.items.forEach { screen ->
                        // Élément de la barre de navigation
                        NavigationBarItem(
                            // Icône de l'élément
                            icon = { 
                                Icon(
                                    imageVector = screen.icon, 
                                    contentDescription = screen.title,
                                    modifier = Modifier.size(24.dp)
                                ) 
                            },
                            // Texte sous l'icône
                            label = { Text(screen.title) },
                            // Si cet élément est sélectionné
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            // Action lors du clic
                            onClick = {
                                // Navigation vers l'écran sélectionné
                                navController.navigate(screen.route) {
                                    // Configuration de la navigation
                                    popUpTo(Screen.Budget.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            // Personnalisation des couleurs
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            // Configuration de la navigation
            NavHost(
                navController = navController,
                startDestination = Screen.Budget.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                // Définition des écrans de l'application
                composable(Screen.Budget.route) { BudgetScreen() }
                composable(Screen.Comptes.route) { ComptesScreen() }
                composable(Screen.NouvelleTransaction.route) { NouvelleTransactionScreen() }
                composable(Screen.Statistiques.route) { StatistiquesScreen() }
            }
        }
    }
}

// Définition des écrans de l'application
sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Budget : Screen("budget", "Budget", Icons.Default.Home)
    object Comptes : Screen("comptes", "Comptes", Icons.Default.Wallet)
    object NouvelleTransaction : Screen("nouvelle_transaction", "Ajouter", Icons.Default.Add)
    object Statistiques : Screen("statistiques", "Statistiques", Icons.Default.BarChart)
    
    companion object {
        val items = listOf(Budget, Comptes, NouvelleTransaction, Statistiques)
    }
}

// Écrans de l'application
@Composable
fun BudgetScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Écran Budget")
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
        Text("Écran Comptes")
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
        Text("Nouvelle Transaction")
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
        Text("Statistiques")
    }
}