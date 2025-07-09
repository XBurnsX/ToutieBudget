package com.xburnsx.toutiebudget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xburnsx.toutiebudget.ui.ecrans.budget.EcranBudget
import com.xburnsx.toutiebudget.ui.ecrans.connexion.EcranConnexion
import com.xburnsx.toutiebudget.ui.ecrans.placeholder.ComptesScreen
import com.xburnsx.toutiebudget.ui.ecrans.placeholder.NouvelleTransactionScreen
import com.xburnsx.toutiebudget.ui.ecrans.placeholder.StatistiquesScreen
import com.xburnsx.toutiebudget.ui.navigation.BarreNavigationFlottante
import com.xburnsx.toutiebudget.ui.navigation.GrapheNavigationRacine
import com.xburnsx.toutiebudget.ui.navigation.Screen
import com.xburnsx.toutiebudget.ui.theme.ToutieBudgetTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * C'est le point d'entrée unique de notre application.
 * @AndroidEntryPoint permet à Hilt d'injecter des dépendances dans cette activité,
 * comme notre MainViewModel.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // On demande à Hilt de nous fournir une instance de MainViewModel.
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Affiche un écran de démarrage le temps que l'app se charge.
        installSplashScreen()
        setContent {
            ToutieBudgetTheme {
                // On observe l'état de connexion de l'utilisateur.
                // `collectAsState` transforme le Flow de notre ViewModel en un état que Compose peut lire.
                // Chaque fois que l'état de connexion change, cette partie du code sera redessinée.
                val estConnecte by viewModel.etatAuth.collectAsState(initial = false)

                // On affiche le graphe de navigation principal, qui décidera quelle page montrer.
                GrapheNavigationRacine(estConnecte = estConnecte)
            }
        }
    }
}

/**
 * Représente le coeur de l'application une fois que l'utilisateur est connecté.
 * Contient votre Scaffold et votre NavHost pour les 4 onglets principaux.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationPrincipale() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            // On affiche votre barre de navigation personnalisée.
            BarreNavigationFlottante(navController = navController)
        }
    ) { innerPadding ->
        // Ce NavHost gère la navigation ENTRE les 4 onglets principaux.
        NavHost(
            navController = navController,
            startDestination = Screen.Budget.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Budget.route) { EcranBudget() }
            composable(Screen.Comptes.route) { ComptesScreen() }
            composable(Screen.NouvelleTransaction.route) { NouvelleTransactionScreen() }
            composable(Screen.Statistiques.route) { StatistiquesScreen() }
        }
    }
}