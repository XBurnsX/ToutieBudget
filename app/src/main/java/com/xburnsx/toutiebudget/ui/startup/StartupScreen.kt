package com.xburnsx.toutiebudget.ui.startup

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Écran de démarrage qui vérifie l'état du serveur et l'authentification
 * avant de rediriger vers la bonne destination
 */
@Composable
fun StartupScreen(
    viewModel: StartupViewModel,
    onNavigateToLogin: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onShowServerError: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.state.collectAsState()

    // Lancer la vérification au premier affichage
    LaunchedEffect(Unit) {
        viewModel.checkStartupConditions(context)
    }

    // Gérer la navigation selon l'état
    LaunchedEffect(state) {
        when (state) {
            is StartupState.UserAuthenticated -> {
                onNavigateToBudget()
            }
            is StartupState.UserNotAuthenticated -> {
                onNavigateToLogin()
            }
            is StartupState.ServerError -> {
                onShowServerError()
            }
            is StartupState.Loading -> {
                // Rester sur l'écran de chargement
            }
        }
    }

    // Interface utilisateur
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (state) {
                is StartupState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Initialisation de ToutieBudget...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Vérification du serveur et de l'authentification",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }

                is StartupState.ServerError -> {
                    Text(
                        text = "⚠️",
                        fontSize = 48.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Erreur de connexion",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Impossible de se connecter au serveur PocketBase",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.retry(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Réessayer")
                    }
                }

                // Les autres états déclenchent une navigation automatique
                else -> {
                    // Interface de transition minimale
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
