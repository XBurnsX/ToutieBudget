package com.xburnsx.toutiebudget.ui.server

import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.xburnsx.toutiebudget.di.AppModule

@Composable
fun ServerStatusDialog(
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val viewModel = remember { AppModule.provideServerStatusViewModel(context) }
    val uiState by viewModel.uiState.collectAsState()

    // Navigation automatique quand le serveur est disponible
    LaunchedEffect(uiState.shouldNavigateToLogin) {
        if (uiState.shouldNavigateToLogin) {
            onNavigateToLogin()
        }
    }

    if (uiState.showDialog) {
        AlertDialog(
            onDismissRequest = { /* Ne pas permettre de fermer en cliquant à côté */ },
            title = {
                Text("Serveur indisponible")
            },
            text = {
                Text(
                    text = if (uiState.errorMessage.isNotEmpty()) {
                        "Le serveur PocketBase n'est pas accessible :\n${uiState.errorMessage}\n\nVeuillez vérifier que le serveur est démarré et réessayer."
                    } else {
                        "Le serveur PocketBase n'est pas accessible. Veuillez vérifier que le serveur est démarré et réessayer."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.reessayerConnexion()
                    },
                    enabled = !uiState.isChecking
                ) {
                    if (uiState.isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text("Réessayer")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.fermerDialog()
                        onNavigateToLogin()
                    }
                ) {
                    Text("Continuer sans serveur")
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        )
    }
}
