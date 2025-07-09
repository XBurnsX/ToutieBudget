package com.xburnsx.toutiebudget.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xburnsx.toutiebudget.ApplicationPrincipale
import com.xburnsx.toutiebudget.ui.ecrans.connexion.EcranConnexion

object Graphe {
    const val RACINE = "graphe_racine"
    const val AUTH = "graphe_auth"
    const val PRINCIPAL = "graphe_principal"
}

@Composable
fun GrapheNavigationRacine(estConnecte: Boolean) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        route = Graphe.RACINE,
        startDestination = if (estConnecte) Graphe.PRINCIPAL else Graphe.AUTH
    ) {
        composable(route = Graphe.AUTH) {
            EcranConnexion(
                surConnexionReussie = {
                    navController.navigate(Graphe.PRINCIPAL) {
                        popUpTo(Graphe.AUTH) { inclusive = true }
                    }
                }
            )
        }
        composable(route = Graphe.PRINCIPAL) {
            ApplicationPrincipale()
        }
    }
}