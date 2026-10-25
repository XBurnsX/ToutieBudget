package com.xburnsx.toutiebudget.ui.miseajour

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.xburnsx.toutiebudget.utils.MiseAJourService
import kotlinx.coroutines.launch

private sealed interface EtatMiseAJour {
    data object Rien : EtatMiseAJour
    data class Disponible(val version: MiseAJourService.VersionDisponible) : EtatMiseAJour
    data class Telechargement(val version: MiseAJourService.VersionDisponible, val progression: Float) : EtatMiseAJour
    data class Erreur(val version: MiseAJourService.VersionDisponible, val message: String) : EtatMiseAJour
}

/**
 * Vérifie à l'ouverture de l'app s'il y a une nouvelle version et propose de l'installer.
 */
@Composable
fun MiseAJourDialog() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var etat by remember { mutableStateOf<EtatMiseAJour>(EtatMiseAJour.Rien) }
    var ignoree by remember { mutableStateOf(false) }
    var verifications by remember { mutableIntStateOf(0) }

    // Vérifier à l'ouverture et à chaque retour au premier plan (ex. après avoir donné l'autorisation)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) verifications++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(verifications) {
        if (ignoree || etat is EtatMiseAJour.Telechargement) return@LaunchedEffect
        MiseAJourService.verifier()?.let { etat = EtatMiseAJour.Disponible(it) }
    }

    fun installer(version: MiseAJourService.VersionDisponible) {
        if (!MiseAJourService.peutInstaller(context)) {
            // Une seule fois : autoriser Toutie Budget à installer des applications
            context.startActivity(MiseAJourService.intentAutorisationInstallation(context))
            return
        }
        scope.launch {
            etat = EtatMiseAJour.Telechargement(version, 0f)
            try {
                val apk = MiseAJourService.telecharger(context, version) { p ->
                    etat = EtatMiseAJour.Telechargement(version, p)
                }
                MiseAJourService.installer(context, apk)
            } catch (e: Exception) {
                etat = EtatMiseAJour.Erreur(version, e.message ?: "Erreur inconnue")
            }
        }
    }

    when (val e = etat) {
        EtatMiseAJour.Rien -> Unit
        is EtatMiseAJour.Disponible -> AlertDialog(
            onDismissRequest = { ignoree = true; etat = EtatMiseAJour.Rien },
            title = { Text("Mise à jour ${e.version.versionName}") },
            text = {
                Column {
                    Text("Une nouvelle version de Toutie Budget est disponible.")
                    e.version.notes?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it)
                    }
                    if (!MiseAJourService.peutInstaller(context)) {
                        Spacer(Modifier.height(8.dp))
                        Text("La première fois, Android va te demander d'autoriser Toutie Budget à installer des applications. Active l'option, puis reviens dans l'app.")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { installer(e.version) }) { Text("Installer") } },
            dismissButton = {
                TextButton(onClick = { ignoree = true; etat = EtatMiseAJour.Rien }) { Text("Plus tard") }
            }
        )
        is EtatMiseAJour.Telechargement -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Mise à jour ${e.version.versionName}") },
            text = {
                Column {
                    Text(if (e.progression < 1f) "Téléchargement…" else "Installation…")
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { e.progression }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {}
        )
        is EtatMiseAJour.Erreur -> AlertDialog(
            onDismissRequest = { ignoree = true; etat = EtatMiseAJour.Rien },
            title = { Text("Mise à jour échouée") },
            text = { Text(e.message) },
            confirmButton = { TextButton(onClick = { installer(e.version) }) { Text("Réessayer") } },
            dismissButton = {
                TextButton(onClick = { ignoree = true; etat = EtatMiseAJour.Rien }) { Text("Fermer") }
            }
        )
    }
}
