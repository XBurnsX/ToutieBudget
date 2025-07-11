// chemin/simule: /ui/navigation/Navigation.kt
package com.xburnsx.toutiebudget.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.ui.ajout_transaction.AjoutTransactionScreen
import com.xburnsx.toutiebudget.ui.budget.BudgetScreen
import com.xburnsx.toutiebudget.ui.categories.CategoriesEnveloppesScreen
import com.xburnsx.toutiebudget.ui.comptes.ComptesScreen
import com.xburnsx.toutiebudget.ui.historique.HistoriqueCompteScreen
import com.xburnsx.toutiebudget.ui.login.LoginScreen
import com.xburnsx.toutiebudget.ui.virement.VirerArgentScreen

// --- Définition des écrans ---
sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Budget : Screen("budget", "Budget", Icons.Default.Home)
    object Comptes : Screen("comptes", "Comptes", Icons.Default.Wallet)
    object NouvelleTransaction : Screen("nouvelle_transaction", "Ajouter", Icons.Default.Add)
    object Categories : Screen("categories", "Catégories", Icons.Default.Category)
    object Statistiques : Screen("statistiques", "Stats", Icons.Default.BarChart)
    object HistoriqueCompte : Screen("historique_compte/{compteId}/{collectionCompte}/{nomCompte}", "Historique", Icons.Default.History)
    object VirerArgent : Screen("virer_argent", "Virement", Icons.Default.SwapHoriz)


    companion object {
        val items = listOf(Budget, Comptes, NouvelleTransaction, Categories, Statistiques)
    }
}

// --- Graphe de Navigation Principal ---
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "login_flow"
    ) {
        composable("login_flow") {
            val loginViewModel = AppModule.provideLoginViewModel()
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    navController.navigate("main_flow") {
                        popUpTo("login_flow") { inclusive = true }
                    }
                }
            )
        }
        composable("main_flow") {
            MainAppScaffold(navController)
        }
        // Route pour l'historique qui est en dehors du Scaffold principal
        composable(
            route = Screen.HistoriqueCompte.route,
            arguments = listOf(
                navArgument("compteId") { type = NavType.StringType },
                navArgument("collectionCompte") { type = NavType.StringType },
                navArgument("nomCompte") { type = NavType.StringType }
            )
        ) {
            // Pour les ViewModels avec arguments, une factory est nécessaire.
            // En attendant, on ne peut pas l'instancier directement ici.
            // HistoriqueCompteScreen(viewModel = ..., onNavigateBack = { navController.popBackStack() })
            PlaceholderScreen("Historique")
        }
    }
}

@Composable
fun MainAppScaffold(mainNavController: NavHostController) {
    val bottomBarNavController = rememberNavController()
    Scaffold(
        bottomBar = {
            FloatingTransformingBottomBar(navController = bottomBarNavController)
        }
    ) { innerPadding ->
        NavHost(
            navController = bottomBarNavController,
            startDestination = Screen.Budget.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Budget.route) {
                val viewModel = AppModule.provideBudgetViewModel()
                BudgetScreen(viewModel = viewModel)
            }
            composable(Screen.Comptes.route) {
                val viewModel = AppModule.provideComptesViewModel()
                ComptesScreen(
                    viewModel = viewModel,
                    onNavigateToHistorique = { compte ->
                        mainNavController.navigate(
                            "historique_compte/${compte.id}/${compte::class.java.simpleName}/${compte.nom}"
                        )
                    }
                )
            }
            composable(Screen.NouvelleTransaction.route) {
                val viewModel = AppModule.provideAjoutTransactionViewModel()
                AjoutTransactionScreen(viewModel = viewModel)
            }
            composable(Screen.Categories.route) {
                val viewModel = AppModule.provideCategoriesEnveloppesViewModel()
                CategoriesEnveloppesScreen(viewModel = viewModel)
            }
            composable(Screen.Statistiques.route) {
                PlaceholderScreen("Statistiques")
            }
            composable(Screen.VirerArgent.route) {
                val viewModel = AppModule.provideVirerArgentViewModel()
                VirerArgentScreen(viewModel = viewModel)
            }
        }
    }
}

// CORRECTION : La fonction PlaceholderScreen est maintenant définie ici.
@Composable
private fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Écran $name")
    }
}
