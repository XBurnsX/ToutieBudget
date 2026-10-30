package com.xburnsx.toutiebudget.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xburnsx.toutiebudget.data.services.SyncJobAutoTriggerService
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.utils.DiagnosticDonnees
import com.xburnsx.toutiebudget.utils.PreferencesSync
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * PA1/PA2/PR18 : mode local ou en ligne, actions en attente, état des préférences, dernier échange
 * réussi, et synchronisation manuelle qui réutilise la file SyncJobs existante (aucune écriture modifiée).
 */
@Composable
fun EtatDonneesCarte() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val statutPrefs by PreferencesSync.statut.collectAsState()
    var enAttente by remember { mutableIntStateOf(-1) }
    var enLigne by remember { mutableStateOf(DiagnosticDonnees.estEnLigne(context)) }
    var dernierEchange by remember { mutableStateOf(DiagnosticDonnees.dernierEchange(context)) }
    var synchroEnCours by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var actualisation by remember { mutableIntStateOf(0) }

    LaunchedEffect(actualisation) {
        PreferencesSync.rafraichirStatut(context)
        enLigne = DiagnosticDonnees.estEnLigne(context)
        enAttente = runCatching { AppModule.provideSyncJobDao(context).getPendingSyncJobsCount() }.getOrDefault(-1)
        dernierEchange = DiagnosticDonnees.dernierEchange(context)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("État des données", color = Color.White, fontWeight = FontWeight.Bold)
        // LDA1 : quel compte ToutieBudget est ouvert (appareil partagé)
        com.xburnsx.toutiebudget.di.PocketBaseClient.obtenirUtilisateurConnecte()?.email?.let {
            Text("Compte ouvert : $it", color = Color.LightGray, fontSize = 13.sp)
        }
        Text(
            if (enLigne) "Mode : en ligne" else "Mode : hors ligne (tout est gardé sur l'appareil)",
            color = if (enLigne) Color(0xFF81C784) else Color(0xFFFFB74D), fontSize = 13.sp
        )
        Text(
            when {
                enAttente < 0 -> "Actions en attente : inconnu"
                enAttente == 0 -> "Actions en attente de synchronisation : aucune"
                else -> "Actions en attente de synchronisation : $enAttente"
            },
            color = Color.LightGray, fontSize = 13.sp
        )
        Text("Préférences : ${statutPrefs.etat.libelle}" + (statutPrefs.detail?.let { " ($it)" } ?: ""),
            color = Color.LightGray, fontSize = 13.sp)
        Text("Dernier échange réussi avec le serveur : ${DiagnosticDonnees.formater(dernierEchange)}",
            color = Color.LightGray, fontSize = 13.sp)
        message?.let { Text(it, color = Color.White, fontSize = 13.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = !synchroEnCours,
                onClick = {
                    synchroEnCours = true
                    message = null
                    scope.launch {
                        // PA2 : même mécanique que la synchro automatique ; rien n'est réécrit
                        SyncJobAutoTriggerService.declencherSynchronisationImmediate(context)
                        val prefsOk = PreferencesSync.envoyer(context)
                        delay(1500)
                        actualisation++
                        synchroEnCours = false
                        message = if (!DiagnosticDonnees.estEnLigne(context)) "Hors ligne : la synchro partira au retour d'Internet."
                            else if (prefsOk) "Synchronisation demandée." else "Préférences pas encore envoyées (voir l'état ci-dessus)."
                    }
                }
            ) {
                if (synchroEnCours) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Synchroniser maintenant")
            }
            OutlinedButton(onClick = { actualisation++ }) { Text("Actualiser") }
        }
    }
}
