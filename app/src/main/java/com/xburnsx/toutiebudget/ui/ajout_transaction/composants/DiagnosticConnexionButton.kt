package com.xburnsx.toutiebudget.ui.ajout_transaction.composants

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.ui.ajout_transaction.AjoutTransactionViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Bouton de diagnostic de connexion pour déboguer les problèmes réseau
 */
@Composable
fun DiagnosticConnexionButton(
    viewModel: AjoutTransactionViewModel,
    modifier: Modifier = Modifier
) {
    var isDiagnosticRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                isDiagnosticRunning = true
                viewModel.diagnostiquerConnexion()
                // Reset après 3 secondes
                scope.launch {
                    delay(3000)
                    isDiagnosticRunning = false
                }
            },
            enabled = !isDiagnosticRunning,
            modifier = Modifier.padding(8.dp)
        ) {
            if (isDiagnosticRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = if (isDiagnosticRunning) "Diagnostic en cours..." else "🔍 Diagnostic Connexion"
            )
        }

        if (isDiagnosticRunning) {
            Text(
                text = "Vérification de la connectivité réseau...",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
} 