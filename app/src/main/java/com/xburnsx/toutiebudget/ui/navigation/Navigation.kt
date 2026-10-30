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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
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
                // LD20 : le même ViewModel survit à la rotation et au retour (pas de double vérification)
                val startupViewModel = composeViewModel { AppModule.provideStartupViewModel() }
                StartupScreen(
                    viewModel = startupViewModel,
                    onNavigateToLogin = {
                        navController.navigate("login_flow") {
                            popUpTo("startup_check") { inclusive = true }
                        }
                    },
                    onNavigateToBudget = {
                        // Session mémorisée : si l'import complet n'a jamais réussi, le reprendre (LD9, LD10)
                        val destination = if (AppModule.provideInitialImportService().importEstComplet()) {
                            "main_flow"
                        } else {
                            "post_login_startup"
                        }
                        navController.navigate(destination) {
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
                // LD20 : la connexion en cours survit à la rotation et au retour de l'activité Google
                val loginViewModel = composeViewModel { AppModule.provideLoginViewModel() }
                val scope = rememberCoroutineScope()
                LoginScreen(
                    viewModel = loginViewModel,
                    onLoginSuccess = {
                        scope.launch {
                            try {
                                // PR8 : les préférences changées ici et pas encore envoyées ne sont pas écrasées
                                val prefs = AppModule.provideRealtimeSyncService().recupererPreferencesUtilisateur()
                                com.xburnsx.toutiebudget.utils.PreferencesSync.appliquerServeur(context, prefs)
                                    ?.let { couleurTheme = it }
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

                // La destination Navigation possède son vrai ViewModelStore.
                // Le ViewModel survit à Modifier, puis est libéré au popBackStack.
                val viewModel = composeViewModel<com.xburnsx.toutiebudget.ui.historique.HistoriqueCompteViewModel>(
                    viewModelStoreOwner = backStackEntry,
                    factory = remember(backStackEntry.id) {
                        historiqueFactory {
                            AppModule.provideHistoriqueCompteViewModel(
                                androidx.lifecycle.SavedStateHandle().apply {
                                    set("compteId", compteId)
                                    set("collectionCompte", collectionCompte)
                                    set("nomCompte", nomCompte)
                                }
                            )
                        }
                    }
                )

                // HC13/NV7 : une édition réussie actualise exactement l'historique
                // d'où elle est partie, sans recommencer un chargement sur chaque recomposition.
                val retourEdition by backStackEntry.savedStateHandle
                    .getStateFlow("historique_retour_edition", 0L).collectAsState()
                LaunchedEffect(retourEdition) {
                    if (retourEdition > 0L) {
                        // HC13 : consommer le signal, sinon une recréation de
                        // l'écran relancerait le même rechargement inutilement.
                        backStackEntry.savedStateHandle["historique_retour_edition"] = 0L
                        viewModel.rechargerTransactions()
                    }
                }

                RessourceVerifiee(compteId, { VerificationsRoutes.compte(compteId, collectionCompte, actif = false) }, { navController.retourSecurise() }) {
                    HistoriqueCompteScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.retourSecurise() },
                        onModifierTransaction = { transactionId ->
                            // ✅ Naviguer vers l'écran de modification avec les paramètres de retour
                            navController.naviguerUneFois(Routes.modifierTransaction(transactionId))
                        },
                        onReprendreTransaction = { transactionId ->
                            // Le chargement doit se faire dans le ViewModel de la
                            // destination, pas sur un ViewModel aussitôt abandonné.
                            navController.naviguerUneFois(Routes.reprendreTransaction(transactionId))
                        }
                    )
                }
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

                val viewModel = composeViewModel<com.xburnsx.toutiebudget.ui.historique.HistoriqueEnveloppeViewModel>(
                    viewModelStoreOwner = backStackEntry,
                    factory = remember(backStackEntry.id) {
                        historiqueFactory {
                            AppModule.provideHistoriqueEnveloppeViewModel(
                                androidx.lifecycle.SavedStateHandle().apply {
                                    set("enveloppeId", enveloppeId)
                                    set("nomEnveloppe", nomEnveloppe)
                                }
                            )
                        }
                    }
                )

                val retourEdition by backStackEntry.savedStateHandle
                    .getStateFlow("historique_retour_edition", 0L).collectAsState()
                LaunchedEffect(retourEdition) {
                    if (retourEdition > 0L) {
                        // HC13 : consommer le signal, sinon une recréation de
                        // l'écran relancerait le même rechargement inutilement.
                        backStackEntry.savedStateHandle["historique_retour_edition"] = 0L
                        viewModel.rechargerTransactions()
                    }
                }

                RessourceVerifiee(enveloppeId, { VerificationsRoutes.enveloppe(enveloppeId) }, { navController.retourSecurise() }) {
                    HistoriqueEnveloppeScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.retourSecurise() },
                        onModifierTransaction = { transactionId ->
                            // Naviguer vers l'écran de modification
                            navController.naviguerUneFois(Routes.modifierTransaction(transactionId))
                        },
                        onReprendreTransaction = { transactionId ->
                            // Le chargement doit se faire dans le ViewModel de la
                            // destination, pas sur un ViewModel aussitôt abandonné.
                            navController.naviguerUneFois(Routes.reprendreTransaction(transactionId))
                        }
                    )
                }
            }
            
            // Route pour l'écran de modification de transaction
            composable(
                route = "modifier_transaction/{transactionId}",
                arguments = listOf(
                    navArgument("transactionId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val transactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
                // HC13 : ne jamais recréer le formulaire à chaque recomposition.
                // Le ViewModel de modification appartient à sa destination.
                val viewModel = composeViewModel<com.xburnsx.toutiebudget.ui.ajout_transaction.ModifierTransactionViewModel>(
                    viewModelStoreOwner = backStackEntry,
                    factory = remember(backStackEntry.id) {
                        historiqueFactory { AppModule.provideModifierTransactionViewModel() }
                    }
                )
                RessourceVerifiee(transactionId, { VerificationsRoutes.transaction(transactionId) }, { navController.retourSecurise() }) {
                    ModifierTransactionScreen(
                        transactionId = transactionId,
                        viewModel = viewModel,
                        onTransactionModified = {
                            // Signal à la destination d'origine (historique compte OU enveloppe).
                            // Une date/nom passé en argument ne suffit pas à rafraîchir Room.
                            navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                                val generation = handle.get<Long>("historique_retour_edition") ?: 0L
                                handle["historique_retour_edition"] = generation + 1L
                            }
                            navController.retourSecurise()
                        },
                        onNavigateBack = {
                            navController.retourSecurise()
                        }
                    )
                }
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
                RessourceVerifiee(carteCreditId, { VerificationsRoutes.compte(carteCreditId, "comptes_credits", actif = true) }, { navController.retourSecurise() }) {
                    GestionCarteCreditScreen(
                        carteCreditId = carteCreditId,
                        viewModel = viewModel,
                        onRetour = { navController.retourSecurise() },
                        onNaviguerVersPaiement = { carteCredit ->
                            // Navigation vers l'écran d'ajout de transaction en mode paiement
                            // avec la carte de crédit présélectionnée
                            navController.naviguerUneFois(Routes.paiementCarte(carteCredit.id))
                        }
                    )
                }
            }
            
            // Route « Gérer le placement » (section 12)
            composable(
                route = "gestion_investissement/{compteId}",
                arguments = listOf(navArgument("compteId") { type = NavType.StringType })
            ) { backStackEntry ->
                RessourceVerifiee(backStackEntry.arguments?.getString("compteId"), { VerificationsRoutes.compte(backStackEntry.arguments?.getString("compteId").orEmpty(), "comptes_investissement", actif = true) }, { navController.retourSecurise() }) {
                    com.xburnsx.toutiebudget.ui.investissement.InvestissementScreen(
                        compteId = backStackEntry.arguments?.getString("compteId") ?: "",
                        viewModel = AppModule.provideInvestissementViewModel(),
                        onRetour = { navController.retourSecurise() }
                    )
                }
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
                RessourceVerifiee(detteId, { VerificationsRoutes.compte(detteId, "comptes_dettes", actif = true) }, { navController.retourSecurise() }) {
                    DetteScreen(
                        detteId = detteId,
                        viewModel = viewModel,
                        onRetour = { navController.retourSecurise() },
                        onSauvegarder = { dette ->
                            // Retour à l'écran précédent après sauvegarde
                            navController.retourSecurise()
                        }
                    )
                }
            }
            
            // AA4 / HC13 : une seule instance conserve le préremplissage;
            // l'ID vient de la route, et aucune écriture n'est automatique.
            composable(
                route = "reprendre_transaction/{transactionId}",
                arguments = listOf(navArgument("transactionId") { type = NavType.StringType })
            ) { backStackEntry ->
                val transactionId = backStackEntry.arguments?.getString("transactionId").orEmpty()
                val ajoutViewModel = remember(backStackEntry.id) {
                    AppModule.provideAjoutTransactionViewModel()
                }
                LaunchedEffect(backStackEntry.id, transactionId) {
                    if (transactionId.isNotBlank()) ajoutViewModel.reprendreTransaction(transactionId)
                }
                val budgetViewModel = remember(backStackEntry.id) {
                    AppModule.provideBudgetViewModel()
                }
                RessourceVerifiee(transactionId, { VerificationsRoutes.transaction(transactionId) }, { navController.retourSecurise() }) {
                    AjoutTransactionScreen(
                        viewModel = ajoutViewModel,
                        onTransactionSuccess = { budgetViewModel.rafraichirDonnees() }
                    )
                }
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
                
                // NV3 : charger la carte PAR ID, pas depuis un cache potentiellement vide.
                val cartesCreditViewModel = remember { AppModule.provideCartesCreditViewModel() }
                val etatCartes by cartesCreditViewModel.uiState.collectAsState()
                LaunchedEffect(carteCreditId) {
                    cartesCreditViewModel.chargerCarteSpecifique(carteCreditId)
                }
                val carteCredit = etatCartes.cartesCredit.find { it.id == carteCreditId }
                when {
                    carteCredit != null -> AjoutTransactionScreen(
                        viewModel = viewModel,
                        onTransactionSuccess = {
                            // NV11 : retrouver la carte d'origine avec son solde à jour
                            budgetViewModel.rafraichirDonnees()
                            cartesCreditViewModel.chargerCarteSpecifique(carteCreditId)
                            navController.retourSecurise()
                        },
                        modePreselectionne = "Paiement",
                        carteCreditPreselectionnee = carteCredit
                    )
                    etatCartes.messageErreur != null ->
                        Text("Paiement indisponible : ${etatCartes.messageErreur}")
                    else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
            }
        }

        // Vérifie les mises à jour à l'ouverture (fonctionne même sans être connecté)
        com.xburnsx.toutiebudget.ui.miseajour.MiseAJourDialog()
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
            // PR8 : une préférence modifiée ici et pas encore envoyée garde sa valeur locale
            val prefs = com.xburnsx.toutiebudget.di.AppModule.provideRealtimeSyncService().recupererPreferencesUtilisateur()
            com.xburnsx.toutiebudget.utils.PreferencesSync.appliquerServeur(ctx, prefs)
                ?.let { onCouleurThemeChange(it) }
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
                        bottomBarNavController.naviguerUneFois(Screen.Categories.route)
                    },
                    onVirementClick = {
                        bottomBarNavController.naviguerUneFois(Screen.VirerArgent.route)
                    },
                    onSettingsClick = {
                        bottomBarNavController.naviguerUneFois(Screen.Settings.route)
                    },
                    onPretPersonnelClick = {
                        bottomBarNavController.naviguerUneFois("pret_personnel")
                    },
                    onGererSoldeNegatifClick = { mois ->
                        bottomBarNavController.naviguerUneFois(Routes.gererSoldeNegatif(mois.time))
                    },
                    onVoirHistoriqueEnveloppe = { enveloppeId, nomEnveloppe ->
                        mainNavController.naviguerUneFois(
                            Routes.historiqueEnveloppe(enveloppeId, nomEnveloppe)
                        )
                    }
                )
            }

            // Route pour la gestion des soldes négatifs
            composable(
                "gerer_solde_negatif/{moisMillis}",
                arguments = listOf(navArgument("moisMillis") { type = NavType.LongType })
            ) { backStackEntry ->
                val viewModel = AppModule.provideBudgetViewModel()
                GererSoldeNegatifScreen(
                    viewModel = viewModel,
                    moisSelectionne = Date(backStackEntry.arguments?.getLong("moisMillis") ?: System.currentTimeMillis()),
                    onRetour = {
                        bottomBarNavController.retourSecurise()
                    },
                    onVirementClick = { enveloppeId, montantNegatif, mois, total ->
                        // Naviguer vers le virement avec paramètres pré-remplis
                        val moisInt = mois.month + 1 // +1 car month commence à 0
                        val anneeInt = mois.year + 1900 // +1900 car year commence à 1900
                        bottomBarNavController.naviguerUneFois(
                            Routes.virementPrerempli(enveloppeId, montantNegatif, moisInt, anneeInt, total)
                        )
                    },
                    onVoirHistoriqueEnveloppe = { enveloppeId, nomEnveloppe ->
                        mainNavController.naviguerUneFois(
                            Routes.historiqueEnveloppe(enveloppeId, nomEnveloppe)
                        )
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    couleurTheme = couleurTheme,
                    onCouleurThemeChange = onCouleurThemeChange,
                    onLogout = {
                        // Redirige immédiatement vers l'écran de login
                        mainNavController.naviguerUneFois("login_flow") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    // PR1 : nouveau budget vide ou restauration -> redémarrage complet, toujours connecté
                    onNouveauBudget = {
                        mainNavController.naviguerUneFois("post_login_startup") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onBack = { bottomBarNavController.retourSecurise() },
                    onNavigateToArchives = { bottomBarNavController.naviguerUneFois("archives") },
                    onNavigateToSyncJobs = { bottomBarNavController.naviguerUneFois("sync_jobs") },
                    onNavigateToDatabaseManager = { bottomBarNavController.naviguerUneFois("database_manager") },
                    onNavigateToAliasBancaires = { bottomBarNavController.naviguerUneFois("alias_bancaires") },
                    onNavigateToOperationsBancaires = { bottomBarNavController.naviguerUneFois("operations_bancaires") },
                    onNavigateToHistory = { bottomBarNavController.naviguerUneFois(Screen.HistoriqueGeneral.route) }
                )
            }

            // Route pour l'historique général
            composable(Screen.HistoriqueGeneral.route) {
                HistoryScreen(
                    onBack = { bottomBarNavController.retourSecurise() }
                )
            }

            // BANQ-03 : import bancaire dans une boîte de validation dédiée.
            composable("operations_bancaires") {
                com.xburnsx.toutiebudget.ui.settings.OperationsBancairesScreen(
                    onBack = { bottomBarNavController.retourSecurise() }
                )
            }

            // BANQ-02 : accès aux alias, uniquement depuis Paramètres.
            composable("alias_bancaires") {
                com.xburnsx.toutiebudget.ui.settings.AliasBancairesScreen(
                    onBack = { bottomBarNavController.retourSecurise() }
                )
            }

            // Route pour la gestion de la base de données
            composable("database_manager") {
                com.xburnsx.toutiebudget.ui.settings.DatabaseManagerScreen(
                    onBack = { bottomBarNavController.retourSecurise() },
                    couleurTheme = couleurTheme
                )
            }
            
            // Route pour la page des SyncJob
            composable("sync_jobs") {
                SyncJobScreen(
                    onBack = { bottomBarNavController.retourSecurise() }
                )
            }
            // Route archives
            composable("archives") {
                val comptesVm = AppModule.provideComptesViewModel()
                val categoriesVm = AppModule.provideCategoriesEnveloppesViewModel()
                ArchivesScreen(
                    comptesViewModel = comptesVm,
                    categoriesViewModel = categoriesVm,
                    onBack = { bottomBarNavController.retourSecurise() },
                    onVoirHistoriqueCompte = { compteId, collectionCompte, nomCompte ->
                        mainNavController.naviguerUneFois(
                            Routes.historiqueCompte(compteId, collectionCompte, nomCompte)
                        )
                    },
                    onVoirHistoriqueEnveloppe = { enveloppeId, nomEnveloppe ->
                        mainNavController.naviguerUneFois(
                            Routes.historiqueEnveloppe(enveloppeId, nomEnveloppe)
                        )
                    }
                )
            }
            
            composable(Screen.Comptes.route) {
                val viewModel = AppModule.provideComptesViewModel()
                ComptesScreen(
                    viewModel = viewModel,
                    onCompteClick = { compteId, collectionCompte, nomCompte ->
                        mainNavController.naviguerUneFois(
                            Routes.historiqueCompte(compteId, collectionCompte, nomCompte)
                        )
                    },
                    onCarteCreditLongClick = { carteCreditId ->
                        mainNavController.naviguerUneFois(Routes.gestionCarteCredit(carteCreditId))
                    },
                    onDetteLongClick = { detteId ->
                        mainNavController.naviguerUneFois(Routes.gestionDette(detteId))
                    },
                    onInvestissementLongClick = { compteId ->
                        mainNavController.naviguerUneFois(Routes.gestionInvestissement(compteId))
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
                    onBack = { bottomBarNavController.retourSecurise() }
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
                        // AT17 : un seul retour, et seulement si on est encore sur Virer argent
                        // (deux retours retiraient aussi la page Budget et laissaient un écran vide)
                        bottomBarNavController.retourDepuis(Screen.VirerArgent.route)
                    }
                )
            }

            // Route pour virement avec paramètres pré-remplis
            composable(
                "virer_argent_pre_rempli/{enveloppeId}/{montantNegatif}/{moisInt}/{anneeInt}?total={total}",
                arguments = listOf(navArgument("total") { type = NavType.BoolType; defaultValue = true })
            ) { backStackEntry ->
                val enveloppeId = backStackEntry.arguments?.getString("enveloppeId") ?: ""
                val montantNegatif = backStackEntry.arguments?.getString("montantNegatif") ?: "0"
                val moisInt = backStackEntry.arguments?.getString("moisInt") ?: "1"
                val anneeInt = backStackEntry.arguments?.getString("anneeInt") ?: "2024"
                
                // NV10 : arguments invalides = écran explicatif, jamais crash
                // ni montant inventé à 0 $ lors d'un virement.
                val moisNumero = moisInt.toIntOrNull()
                val anneeNumero = anneeInt.toIntOrNull()
                val montant = montantNegatif.toDoubleOrNull()
                if (enveloppeId.isBlank() || moisNumero == null || moisNumero !in 1..12 ||
                    anneeNumero == null || anneeNumero !in 1900..2100 ||
                    montant == null || !montant.isFinite() || montant <= 0.0
                ) {
                    Text("Virement prérempli impossible : paramètres manquants ou invalides.")
                    return@composable
                }
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, anneeNumero)
                    set(Calendar.MONTH, moisNumero - 1)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val dateMois = calendar.time
                
                val viewModel = AppModule.provideVirerArgentViewModel()
                VirerArgentScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        bottomBarNavController.retourDepuis("virer_argent_pre_rempli/{enveloppeId}/{montantNegatif}/{moisInt}/{anneeInt}?total={total}")
                    },
                    enveloppePreselectionnee = enveloppeId,
                    montantPreselectionne = montant,
                    moisPreselectionne = dateMois,
                    couvertureTotale = backStackEntry.arguments?.getBoolean("total") ?: true
                )
            }
            

            // Écran Prêt personnel
            composable("pret_personnel") {
                val viewModel = AppModule.providePretPersonnelViewModel()
                com.xburnsx.toutiebudget.ui.pret_personnel.PretPersonnelScreen(
                    viewModel = viewModel,
                    onBack = { bottomBarNavController.retourSecurise() }
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

/** Revient en arrière seulement si l'écran affiché est bien [route] : un deuxième appel ne fait rien. */
private fun androidx.navigation.NavController.retourDepuis(route: String) {
    if (currentBackStackEntry?.destination?.route == route) popBackStack()
}

/**
 * Usine typée pour les historiques dans le ViewModelStore de l'entrée Navigation.
 * Contrairement à remember { ViewModel() }, on obtient onCleared() au bon moment.
 */
private fun <T : ViewModel> historiqueFactory(construire: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
            val instance = construire()
            return modelClass.cast(instance)
                ?: throw IllegalArgumentException("Type de ViewModel historique inattendu.")
        }
    }

/** NV10/NVA3 : vérifications des destinations — null si tout va bien, sinon un message utile. */
private object VerificationsRoutes {
    suspend fun compte(id: String, collection: String, actif: Boolean): String? {
        if (!Routes.idValide(id)) return "Lien invalide : aucun compte indiqué."
        val c = com.xburnsx.toutiebudget.di.AppModule.provideCompteRepository().getCompteById(id, collection)
            ?: return "Ce compte n'existe plus sur cet appareil (supprimé, ou pas encore synchronisé)."
        if (actif && c.estArchive) return "« ${c.nom} » est archivé : réactive-le dans Paramètres > Archives pour le gérer."
        return null
    }

    suspend fun enveloppe(id: String): String? {
        if (!Routes.idValide(id)) return "Lien invalide : aucune enveloppe indiquée."
        val existe = com.xburnsx.toutiebudget.di.AppModule.provideEnveloppeRepository()
            .recupererToutesLesEnveloppes().getOrThrow().any { it.id == id }
        return if (existe) null else "Cette enveloppe n'existe plus sur cet appareil."
    }

    suspend fun transaction(id: String): String? {
        if (!Routes.idValide(id)) return "Lien invalide : aucune transaction indiquée."
        return com.xburnsx.toutiebudget.di.AppModule.provideTransactionRepository()
            .recupererTransactionDeLaSession(id)
            .fold({ null }, { "Cette transaction n'existe plus (supprimée ici ou sur un autre appareil)." })
    }
}
