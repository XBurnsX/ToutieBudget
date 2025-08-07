// chemin/simule: /ui/navigation/Navigation.kt
package com.xburnsx.toutiebudget.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import com.xburnsx.toutiebudget.R
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.ui.ajout_transaction.AjoutTransactionScreen
import com.xburnsx.toutiebudget.ui.ajout_transaction.ModifierTransactionScreen
import com.xburnsx.toutiebudget.ui.budget.BudgetScreen
import com.xburnsx.toutiebudget.ui.categories.CategoriesEnveloppesScreen
import com.xburnsx.toutiebudget.ui.comptes.ComptesScreen
import com.xburnsx.toutiebudget.ui.historique.HistoriqueCompteScreen
import com.xburnsx.toutiebudget.ui.login.LoginScreen
import com.xburnsx.toutiebudget.ui.server.ServerStatusDialog
import com.xburnsx.toutiebudget.ui.startup.StartupScreen
import com.xburnsx.toutiebudget.ui.settings.SettingsScreen
import com.xburnsx.toutiebudget.ui.virement.VirerArgentScreen
import com.xburnsx.toutiebudget.ui.theme.CouleurTheme
import com.xburnsx.toutiebudget.ui.theme.ToutieBudgetTheme
import com.xburnsx.toutiebudget.utils.ThemePreferences

// --- Définition des écrans ---
sealed class Screen(
    val route: String,
    val title: String,
    val iconProvider: @Composable () -> ImageVector
) {
    object Budget : Screen("budget", "Budget", { ImageVector.vectorResource(R.drawable.budget) })
    object Comptes : Screen("comptes", "Comptes", { Icons.Default.AccountBalance })
    object NouvelleTransaction : Screen("nouvelle_transaction", "Ajouter", { ImageVector.vectorResource(R.drawable.ajout_transaction) })
    object Categories : Screen("categories", "Catégories", { Icons.Default.Category })
    object Statistiques : Screen("statistiques", "Stats", { Icons.Default.BarChart })
    object HistoriqueCompte : Screen("historique_compte/{compteId}/{collectionCompte}/{nomCompte}", "Historique", { Icons.Default.History })
    object VirerArgent : Screen("virer_argent", "Virement", { ImageVector.vectorResource(R.drawable.transfert_argent) })
    object Settings : Screen("settings", "Paramètres", { Icons.Default.Settings })


    companion object {
        val items = listOf(Budget, Comptes, NouvelleTransaction, Statistiques)
    }
}

// --- Graphe de Navigation Principal ---
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Charger la couleur du thème sauvegardée au démarrage
    var couleurTheme by remember {
        mutableStateOf(ThemePreferences.chargerCouleurTheme(context))
    }

    // Appliquer le thème dynamique à toute l'application
    ToutieBudgetTheme(couleurTheme = couleurTheme) {
        NavHost(
            navController = navController,
            startDestination = "startup_check"
        ) {
            // Écran de vérification du serveur au démarrage
            composable("startup_check") {
                val startupViewModel = AppModule.provideStartupViewModel()
                StartupScreen(
                    viewModel = startupViewModel,
                    onNavigateToLogin = {
                        navController.navigate("login_flow") {
                            popUpTo("startup_check") { inclusive = true }
                        }
                    },
                    onNavigateToBudget = {
                        navController.navigate("main_flow") {
                            popUpTo("startup_check") { inclusive = true }
                        }
                    },
                    onShowServerError = {
                        // Reste sur l'écran de démarrage qui affiche l'erreur avec bouton "Réessayer"
                    }
                )
            }

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
                MainAppScaffold(navController, couleurTheme) { nouvelleCouleur ->
                    couleurTheme = nouvelleCouleur
                    // Sauvegarder la nouvelle couleur
                    ThemePreferences.sauvegarderCouleurTheme(context, nouvelleCouleur)
                }
            }
            // Route pour l'historique qui est en dehors du Scaffold principal
            composable(
                route = Screen.HistoriqueCompte.route,
                arguments = listOf(
                    navArgument("compteId") { type = NavType.StringType },
                    navArgument("collectionCompte") { type = NavType.StringType },
                    navArgument("nomCompte") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val compteId = backStackEntry.arguments?.getString("compteId") ?: ""
                val collectionCompte = backStackEntry.arguments?.getString("collectionCompte") ?: ""
                val nomCompte = backStackEntry.arguments?.getString("nomCompte") ?: ""

                // Créer le SavedStateHandle avec les arguments
                val savedStateHandle = androidx.lifecycle.SavedStateHandle().apply {
                    set("compteId", compteId)
                    set("collectionCompte", collectionCompte)
                    set("nomCompte", nomCompte)
                }

                val viewModel = AppModule.provideHistoriqueCompteViewModel(savedStateHandle)

                HistoriqueCompteScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onModifierTransaction = { transactionId ->
                        // ✅ Naviguer vers l'écran de modification avec les paramètres de retour
                        navController.navigate("modifier_transaction/$transactionId/$compteId/$collectionCompte/$nomCompte")
                    }
                )
            }
            
            // Route pour l'écran de modification de transaction
            composable(
                route = "modifier_transaction/{transactionId}/{compteId}/{collectionCompte}/{nomCompte}",
                arguments = listOf(
                    navArgument("transactionId") { type = NavType.StringType },
                    navArgument("compteId") { type = NavType.StringType },
                    navArgument("collectionCompte") { type = NavType.StringType },
                    navArgument("nomCompte") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val transactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
                val compteId = backStackEntry.arguments?.getString("compteId") ?: ""
                val collectionCompte = backStackEntry.arguments?.getString("collectionCompte") ?: ""
                val nomCompte = backStackEntry.arguments?.getString("nomCompte") ?: ""
                val viewModel = AppModule.provideModifierTransactionViewModel()
                ModifierTransactionScreen(
                    transactionId = transactionId,
                    viewModel = viewModel,
                    onTransactionModified = {
                        // ✅ Retour à l'écran précédent (historique) en conservant la position de scroll
                        navController.popBackStack()
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            // Route pour l'écran de gestion des cartes de crédit
            composable(
                route = "gestion_carte_credit/{carteCreditId}",
                arguments = listOf(
                    navArgument("carteCreditId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val carteCreditId = backStackEntry.arguments?.getString("carteCreditId") ?: ""
                val viewModel = AppModule.provideCartesCreditViewModel()
                com.xburnsx.toutiebudget.ui.cartes_credit.GestionCarteCreditScreen(
                    carteCreditId = carteCreditId,
                    viewModel = viewModel,
                    onRetour = { navController.popBackStack() },
                    onNaviguerVersPaiement = { carteCredit ->
                        // Navigation vers l'écran d'ajout de transaction en mode paiement
                        // avec la carte de crédit présélectionnée
                        navController.navigate("nouvelle_transaction_paiement/${carteCredit.id}")
                    }
                )
            }
            
            // Route pour l'écran de gestion des dettes
            composable(
                route = "gestion_dette/{detteId}",
                arguments = listOf(
                    navArgument("detteId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val detteId = backStackEntry.arguments?.getString("detteId") ?: ""
                val viewModel = AppModule.provideDetteViewModel()
                com.xburnsx.toutiebudget.ui.dette.DetteScreen(
                    detteId = detteId,
                    viewModel = viewModel,
                    onRetour = { navController.popBackStack() },
                    onSauvegarder = { dette ->
                        // Retour à l'écran précédent après sauvegarde
                        navController.popBackStack()
                    }
                )
            }
            
            // Route pour l'ajout de transaction avec présélection (paiement carte de crédit)
            composable(
                route = "nouvelle_transaction_paiement/{carteCreditId}",
                arguments = listOf(
                    navArgument("carteCreditId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val carteCreditId = backStackEntry.arguments?.getString("carteCreditId") ?: ""
                val budgetViewModel = AppModule.provideBudgetViewModel()
                val viewModel = AppModule.provideAjoutTransactionViewModel()
                
                // Récupérer la carte de crédit pour la présélection
                val cartesCreditViewModel = AppModule.provideCartesCreditViewModel()
                val carteCredit = cartesCreditViewModel.uiState.value.cartesCredit.find { it.id == carteCreditId }
                
                AjoutTransactionScreen(
                    viewModel = viewModel,
                    onTransactionSuccess = { budgetViewModel.rafraichirDonnees() },
                    modePreselectionne = "Paiement",
                    carteCreditPreselectionnee = carteCredit
                )
            }
        }
    }
}

@Composable
fun MainAppScaffold(
    mainNavController: NavHostController,
    couleurTheme: CouleurTheme,
    onCouleurThemeChange: (CouleurTheme) -> Unit
) {
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
                BudgetScreen(
                    viewModel = viewModel,
                    onCategoriesClick = {
                        bottomBarNavController.navigate(Screen.Categories.route)
                    },
                    onVirementClick = {
                        bottomBarNavController.navigate(Screen.VirerArgent.route)
                    },
                    onSettingsClick = {
                        bottomBarNavController.navigate(Screen.Settings.route)
                    },
                    onLogout = {
                        // Redirige immédiatement vers l'écran de login
                        mainNavController.navigate("login_flow") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    couleurTheme = couleurTheme,
                    onCouleurThemeChange = onCouleurThemeChange,
                    onLogout = {
                        // Redirige immédiatement vers l'écran de login
                        mainNavController.navigate("login_flow") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onBack = {
                        bottomBarNavController.popBackStack()
                    }
                )
            }
            composable(Screen.Comptes.route) {
                val viewModel = AppModule.provideComptesViewModel()
                ComptesScreen(
                    viewModel = viewModel,
                    onCompteClick = { compteId, collectionCompte, nomCompte ->
                        mainNavController.navigate(
                            "historique_compte/$compteId/$collectionCompte/$nomCompte"
                        )
                    },
                    onCarteCreditLongClick = { carteCreditId ->
                        mainNavController.navigate("gestion_carte_credit/$carteCreditId")
                    },
                    onDetteLongClick = { detteId ->
                        mainNavController.navigate("gestion_dette/$detteId")
                    }
                )
            }
            composable(Screen.NouvelleTransaction.route) {
                val budgetViewModel = AppModule.provideBudgetViewModel()
                val viewModel = AppModule.provideAjoutTransactionViewModel()
                AjoutTransactionScreen(
                    viewModel = viewModel,
                    onTransactionSuccess = { budgetViewModel.rafraichirDonnees() }
                )
            }
            composable(Screen.Categories.route) {
                val viewModel = AppModule.provideCategoriesEnveloppesViewModel()
                CategoriesEnveloppesScreen(
                    viewModel = viewModel,
                    onBack = { bottomBarNavController.popBackStack() }
                )
            }
            composable(Screen.Statistiques.route) {
                PlaceholderScreen("Statistiques")
            }
            composable(Screen.VirerArgent.route) {
                val viewModel = AppModule.provideVirerArgentViewModel()
                val budgetViewModel = AppModule.provideBudgetViewModel()
                VirerArgentScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        // Le temps réel va automatiquement actualiser les données !
                        // Plus besoin d'appeler manuellement rafraichirDonnees()
                        bottomBarNavController.navigate(Screen.Budget.route) {
                            popUpTo(Screen.Budget.route) { inclusive = true }
                        }
                    }
                )
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
