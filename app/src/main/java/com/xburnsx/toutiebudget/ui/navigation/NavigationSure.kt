package com.xburnsx.toutiebudget.ui.navigation

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

/** NV8 : dernier appui de navigation, partagé par les deux NavControllers. */
private var dernierAppuiNavigation = 0L
private const val DELAI_DOUBLE_APPUI_MS = 700L

/**
 * NV8 : une seule destination par appui logique. Un deuxième appui rapide, ou la même destination
 * déjà affichée, est ignoré (jamais deux écrans empilés ni deux opérations lancées).
 */
fun NavController.naviguerUneFois(route: String, options: (NavOptionsBuilder.() -> Unit)? = null) {
    val maintenant = SystemClock.elapsedRealtime()
    if (maintenant - dernierAppuiNavigation < DELAI_DOUBLE_APPUI_MS) return
    if (currentBackStackEntry?.destination?.route != null && currentDestination?.route == route) return
    dernierAppuiNavigation = maintenant
    if (options != null) navigate(route, options) else navigate(route)
}

/**
 * NV9 : retour cohérent pour les deux NavControllers. Ne vide jamais une pile de navigation
 * (un écran vide à la place du Budget) : sans écran précédent, rien ne se passe.
 */
fun NavController.retourSecurise(): Boolean =
    if (previousBackStackEntry != null) popBackStack() else false

/**
 * NV10/NVA3 : la destination n'est affichée que si sa ressource existe encore.
 * [verifier] retourne null si tout va bien, sinon un message clair (supprimée, archivée, introuvable…).
 */
@Composable
fun RessourceVerifiee(
    cle: Any?,
    verifier: suspend () -> String?,
    onRetour: () -> Unit,
    contenu: @Composable () -> Unit
) {
    var etat by remember(cle) { mutableStateOf<String?>(null) }
    var verifie by remember(cle) { mutableStateOf(false) }
    LaunchedEffect(cle) {
        etat = runCatching { verifier() }.getOrElse { "Impossible de vérifier cet élément : ${it.message}" }
        verifie = true
    }
    when {
        !verifie -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        etat == null -> contenu()
        else -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(etat ?: "", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Button(onClick = onRetour, modifier = Modifier.padding(top = 16.dp)) { Text("Retour") }
        }
    }
}
