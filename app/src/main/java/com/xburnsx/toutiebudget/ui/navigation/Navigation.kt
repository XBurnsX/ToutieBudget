// chemin/simule: /ui/navigation/Navigation.kt
package com.xburnsx.toutiebudget.ui.navigation

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.xburnsx.toutiebudget.ui.budget.GererSoldeNegatifScreen
import com.xburnsx.toutiebudget.ui.categories.CategoriesEnveloppesScreen
import com.xburnsx.toutiebudget.ui.comptes.ComptesScreen
import com.xburnsx.toutiebudget.ui.historique.HistoriqueCompteScreen
import com.xburnsx.toutiebudget.ui.historique.HistoriqueEnveloppeScreen
import com.xburnsx.toutiebudget.ui.login.LoginScreen
import com.xburnsx.toutiebudget.ui.startup.StartupScreen
import com.xburnsx.toutiebudget.ui.startup.PostLoginStartupScreen
import com.xburnsx.toutiebudget.ui.settings.SettingsScreen
import com.xburnsx.toutiebudget.ui.settings.HistoryScreen
import com.xburnsx.toutiebudget.ui.sync.SyncJobScreen
import com.xburnsx.toutiebudget.ui.virement.VirerArgentScreen
import com.xburnsx.toutiebudget.ui.theme.CouleurTheme
import com.xburnsx.toutiebudget.ui.theme.ToutieBudgetTheme
import com.xburnsx.toutiebudget.utils.ThemePreferences
import com.xburnsx.toutiebudget.ui.cartes_credit.GestionCarteCreditScreen
import com.xburnsx.toutiebudget.ui.dette.DetteScreen
import com.xburnsx.toutiebudget.ui.archives.ArchivesScreen
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Calendar

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
    object HistoriqueEnveloppe : Screen("historique_enveloppe/{enveloppeId}/{nomEnveloppe}", "Historique Enveloppe", { Icons.Default.History })
    object HistoriqueGeneral : Screen("historique_general", "Historique des Comptes", { Icons.Default.History })
    object VirerArgent : Screen("virer_argent", "Virement", { ImageVector.vectorResource(R.drawable.transfert_argent) })
    object Settings : Screen("settings", "Paramètres", { Icons.Default.Settings })


    companion object {
        val items = listOf(Budget, Comptes, NouvelleTransaction, Statistiques)
    }
}

// --- Graphe de Navigation Principal ---
@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Charger la couleur du thème sauvegardée au démarrage
    var couleurTheme by remember {
        mutableStateOf(ThemePreferences.chargerCouleurTheme(context))
    }

    // (Chargement à chaud des prefs après login déplacé dans MainAppScaffold)

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
                    },
                    onNavigateToOfflineMode = {
                        // 🆕 NOUVEAU : Navigation vers le mode hors ligne
                        navController.navigate("main_flow") {
                            popUpTo("startup_check") { inclusive = true }
                        }
                    }
                )
            }

            composable("login_flow") {
                val loginViewModel = AppModule.provideLoginViewModel()
                val scope = rememberCoroutineScope()
                LoginScreen(
                    viewModel = loginViewModel,
                    onLoginSuccess = {
                        scope.launch {
                            try {
                                val prefs = AppModule.provideRealtimeSyncService().recupererPreferencesUtilisateur()
                                when (prefs["theme"] as? String) {
                                    "RED" -> {
                                        couleurTheme = CouleurTheme.RED
                                        ThemePreferences.sauvegarderCouleurTheme(context, CouleurTheme.RED)
                                    }
                                    "PINK" -> {
                                        couleurTheme = CouleurTheme.PINK
                                        ThemePreferences.sauvegarderCouleurTheme(context, CouleurTheme.PINK)
                                    }
                                }
                                (prefs["figer_pret_a_placer"] as? Boolean)?.let { value ->
                                    com.xburnsx.toutiebudget.utils.PreferencesManager.setFigerPretAPlacer(context, value)
                                    AppModule.provideBudgetViewModel().setFigerPretAPlacer(value)
                                }
                                (prefs["notifications_enabled"] as? Boolean)?.let { value ->
                                    com.xburnsx.toutiebudget.utils.PreferencesManager.setNotificationsEnabled(context, value)
                                }
                                (prefs["notif_obj_jours_avant"] as? Int)?.let { value ->
                                    com.xburnsx.toutiebudget.utils.PreferencesManager.setNotifObjJoursAvant(context, value)
                                }
                                (prefs["notif_enveloppe_negatif"] as? Boolean)?.let { value ->
                                    com.xburnsx.toutiebudget.utils.PreferencesManager.setNotifEnveloppeNegative(context, value)
                                }
                            } catch (_: Exception) {
                                // ignorer en cas d'absence de record
                            }

                            navController.navigate("post_login_startup") {
                                popUpTo("login_flow") { inclusive = true }
                            }
                        }
                    }
                )
            }
            
            // Écran d'initialisation post-login avec animations
            composable("post_login_startup") {
                PostLoginStartupScreen(
                    onInitializationComplete = {
                        navController.navigate("main_flow") {
                            popUpTo("post_login_startup") { inclusive = true }
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
            
            // Route pour l'historique des enveloppes
            composable(
                route = Screen.HistoriqueEnveloppe.route,
                arguments = listOf(
                    navArgument("enveloppeId") { type = NavType.StringType },
                    navArgument("nomEnveloppe") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val enveloppeId = backStackEntry.arguments?.getString("enveloppeId") ?: ""
                val nomEnveloppe = backStackEntry.arguments?.getString("nomEnveloppe") ?: ""

                // Créer le SavedStateHandle avec les arguments
                val savedStateHandle = androidx.lifecycle.SavedStateHandle().apply {
                    set("enveloppeId", enveloppeId)
                    set("nomEnveloppe", nomEnveloppe)
                }

                val viewModel = AppModule.provideHistoriqueEnveloppeViewModel(savedStateHandle)

                HistoriqueEnveloppeScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onModifierTransaction = { transactionId ->
                        // Naviguer vers l'écran de modification
                        navController.navigate("modifier_transaction/$transactionId")
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
                GestionCarteCreditScreen(
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
                DetteScreen(
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
    val ctx = LocalContext.current
    // Charger/Appliquer les préférences (locales + distantes) au démarrage du flux principal
    LaunchedEffect(Unit) {
        try {
            // 1) Appliquer d'abord les locales pour éviter le flash
            val figerLocal = com.xburnsx.toutiebudget.utils.PreferencesManager.getFigerPretAPlacer(ctx)
            com.xburnsx.toutiebudget.di.AppModule.provideBudgetViewModel().setFigerPretAPlacer(figerLocal)
            val showArchived = com.xburnsx.toutiebudget.utils.PreferencesManager.getShowArchived(ctx)
            com.xburnsx.toutiebudget.di.AppModule.provideBudgetViewModel().setShowArchived(showArchived)

            // 2) Charger les distantes si connecté et les persister localement
            val prefs = com.xburnsx.toutiebudget.di.AppModule.provideRealtimeSyncService().recupererPreferencesUtilisateur()
            // Thème
            when (prefs["theme"] as? String) {
                "RED" -> {
                    onCouleurThemeChange(com.xburnsx.toutiebudget.ui.theme.CouleurTheme.RED)
                    ThemePreferences.sauvegarderCouleurTheme(ctx, com.xburnsx.toutiebudget.ui.theme.CouleurTheme.RED)
                }
                "PINK" -> {
                    onCouleurThemeChange(com.xburnsx.toutiebudget.ui.theme.CouleurTheme.PINK)
                    ThemePreferences.sauvegarderCouleurTheme(ctx, com.xburnsx.toutiebudget.ui.theme.CouleurTheme.PINK)
                }
            }
            // Switches
            (prefs["figer_pret_a_placer"] as? Boolean)?.let { value ->
                com.xburnsx.toutiebudget.utils.PreferencesManager.setFigerPretAPlacer(ctx, value)
                com.xburnsx.toutiebudget.di.AppModule.provideBudgetViewModel().setFigerPretAPlacer(value)
            }
            (prefs["notifications_enabled"] as? Boolean)?.let { value ->
                com.xburnsx.toutiebudget.utils.PreferencesManager.setNotificationsEnabled(ctx, value)
            }
            (prefs["notif_obj_jours_avant"] as? Int)?.let { value ->
                com.xburnsx.toutiebudget.utils.PreferencesManager.setNotifObjJoursAvant(ctx, value)
            }
            (prefs["notif_enveloppe_negatif"] as? Boolean)?.let { value ->
                com.xburnsx.toutiebudget.utils.PreferencesManager.setNotifEnveloppeNegative(ctx, value)
            }
        } catch (_: Exception) {
            // Silencieux si non connecté / pas de record
        }
    }

    Scaffold(
        bottomBar = {
            // Réduire la hauteur de la "bande noire" au strict minimum
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                    onPretPersonnelClick = {
                        bottomBarNavController.navigate("pret_personnel")
                    },
                    onGererSoldeNegatifClick = {
                        bottomBarNavController.navigate("gerer_solde_negatif")
                    },
                    onVoirHistoriqueEnveloppe = { enveloppeId, nomEnveloppe ->
                        mainNavController.navigate("historique_enveloppe/$enveloppeId/$nomEnveloppe")
                    }
                )
            }

            // Route pour la gestion des soldes négatifs
            composable("gerer_solde_negatif") {
                val viewModel = AppModule.provideBudgetViewModel()
                GererSoldeNegatifScreen(
                    viewModel = viewModel,
                    onRetour = {
                        bottomBarNavController.popBackStack()
                    },
                    onVirementClick = { enveloppeId, montantNegatif, mois ->
                        // Naviguer vers le virement avec paramètres pré-remplis
                        val moisInt = mois.month + 1 // +1 car month commence à 0
                        val anneeInt = mois.year + 1900 // +1900 car year commence à 1900
                        bottomBarNavController.navigate("virer_argent_pre_rempli/$enveloppeId/$montantNegatif/$moisInt/$anneeInt")
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
                    onBack = { bottomBarNavController.popBackStack() },
                    onNavigateToArchives = { bottomBarNavController.navigate("archives") },
                    onNavigateToSyncJobs = { bottomBarNavController.navigate("sync_jobs") },
                    onNavigateToDatabaseManager = { bottomBarNavController.navigate("database_manager") },
                    onNavigateToHistory = { bottomBarNavController.navigate(Screen.HistoriqueGeneral.route) }
                )
            }

            // Route pour l'historique général
            composable(Screen.HistoriqueGeneral.route) {
                HistoryScreen(
                    onBack = { bottomBarNavController.popBackStack() }
                )
            }

            // Route pour la gestion de la base de données
            composable("database_manager") {
                com.xburnsx.toutiebudget.ui.settings.DatabaseManagerScreen(
                    onBack = { bottomBarNavController.popBackStack() },
                    couleurTheme = couleurTheme
                )
            }
            
            // Route pour la page des SyncJob
            composable("sync_jobs") {
                SyncJobScreen(
                    onBack = { bottomBarNavController.popBackStack() }
                )
            }
            // Route archives
            composable("archives") {
                val comptesVm = AppModule.provideComptesViewModel()
                val budgetVm = AppModule.provideBudgetViewModel()
                ArchivesScreen(
                    comptesViewModel = comptesVm,
                    budgetViewModel = budgetVm,
                    onBack = { bottomBarNavController.popBackStack() }
                )
            }
            
            // Route pour la page Historique générale
            composable(Screen.HistoriqueGeneral.route) {
                com.xburnsx.toutiebudget.ui.settings.HistoryScreen(
                    onBack = { bottomBarNavController.popBackStack() }
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
                val viewModel = AppModule.provideStatistiquesViewModel()
                com.xburnsx.toutiebudget.ui.statistiques.StatistiquesScreen(viewModel = viewModel)
            }
            composable(Screen.VirerArgent.route) {
                val viewModel = AppModule.provideVirerArgentViewModel()
                VirerArgentScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        // Le temps réel va automatiquement actualiser les données !
                        // Plus besoin d'appeler manuellement rafraichirDonnees()
                        bottomBarNavController.popBackStack()
                    }
                )
            }

            // Route pour virement avec paramètres pré-remplis
            composable("virer_argent_pre_rempli/{enveloppeId}/{montantNegatif}/{moisInt}/{anneeInt}") { backStackEntry ->
                val enveloppeId = backStackEntry.arguments?.getString("enveloppeId") ?: ""
                val montantNegatif = backStackEntry.arguments?.getString("montantNegatif") ?: "0"
                val moisInt = backStackEntry.arguments?.getString("moisInt") ?: "1"
                val anneeInt = backStackEntry.arguments?.getString("anneeInt") ?: "2024"
                
                // Créer une date valide
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.YEAR, anneeInt.toInt())
                calendar.set(Calendar.MONTH, moisInt.toInt() - 1) // -1 car Calendar.MONTH commence à 0
                calendar.set(Calendar.DAY_OF_MONTH, 1) // Premier jour du mois
                val dateMois = calendar.time
                
                val viewModel = AppModule.provideVirerArgentViewModel()
                VirerArgentScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        bottomBarNavController.popBackStack()
                    },
                    enveloppePreselectionnee = enveloppeId,
                    montantPreselectionne = montantNegatif.toDoubleOrNull() ?: 0.0,
                    moisPreselectionne = dateMois
                )
            }
            

            // Écran Prêt personnel
            composable("pret_personnel") {
                val viewModel = AppModule.providePretPersonnelViewModel()
                com.xburnsx.toutiebudget.ui.pret_personnel.PretPersonnelScreen(
                    viewModel = viewModel,
                    onBack = { bottomBarNavController.popBackStack() }
                )
            }
            }

            // Barre flottante par-dessus le contenu, sans élargir la "bande"
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                FloatingTransformingBottomBar(navController = bottomBarNavController)
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
