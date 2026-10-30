package com.xburnsx.toutiebudget.ui.startup

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.R
import com.xburnsx.toutiebudget.di.PocketBaseClient


/**
 * Écran de démarrage qui vérifie l'état du serveur et l'authentification
 * avant de rediriger vers la bonne destination
 * 🆕 SUPPORT HORS LIGNE : Gère la navigation vers le mode hors ligne
 */
@Composable
fun StartupScreen(
    viewModel: StartupViewModel,
    onNavigateToLogin: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onShowServerError: () -> Unit,
    onNavigateToOfflineMode: () -> Unit = {} // 🆕 NOUVEAU : Callback pour le mode hors ligne
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.state.collectAsState()
    val diagnostic by viewModel.diagnostic.collectAsState()

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
            is StartupState.OfflineMode -> {
                // LD12 : l'utilisateur confirme « Continuer avec les données locales » (bouton ci-dessous)
            }
            is StartupState.Loading -> {
                // Rester sur l'écran de chargement
            }
        }
    }

    // Animations pour le splash screen
    val infiniteTransition = rememberInfiniteTransition(label = "splash_animation")
    
    val scaleAnimation by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_animation"
    )
    
    val alphaAnimation by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha_animation"
    )

    // Interface utilisateur avec fond sombre
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF191a1b)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (state) {
                is StartupState.Loading -> {
                    // BRANDING.PNG EN GRAND AVEC ANIMATIONS
                    Image(
                        painter = painterResource(id = R.drawable.branding),
                        contentDescription = "Logo ToutieBudget Branding",
                        modifier = Modifier
                            .size(180.dp)
                            .scale(scaleAnimation)
                            .alpha(alphaAnimation),
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // SPLASH.PNG AUSSI EN GRAND FORMAT
                    Image(
                        painter = painterResource(id = R.drawable.splash),
                        contentDescription = "Logo ToutieBudget Splash",
                        modifier = Modifier.size(160.dp), // GRAND FORMAT AUSSI
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Toutie Budget",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Initialisation en cours...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }

                is StartupState.ServerError -> {
                    // Logo en version atténuée pour l'erreur
                    Image(
                        painter = painterResource(id = R.drawable.splash),
                        contentDescription = "Logo ToutieBudget",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .alpha(0.5f),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "⚠️ Connexion échouée",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6B6B),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Impossible de se connecter au serveur",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Vérifiez votre connexion internet",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    diagnostic?.let { DiagnosticCompact(it) }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { viewModel.retry(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 48.dp)
                    ) {
                        Text(
                            text = "Réessayer",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                    }
                }

                // 🆕 NOUVEAU : Gestion du mode hors ligne
                is StartupState.OfflineMode -> {
                    // Logo en version normale pour le mode hors ligne
                    Image(
                        painter = painterResource(id = R.drawable.splash),
                        contentDescription = "Logo ToutieBudget",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "📱 Mode hors ligne",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, // 🎨 COULEUR DU THÈME au lieu du turquoise fixe
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "L'application s'ouvre en mode hors ligne",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Tu travailles sur les données de cet appareil. Tes changements sont gardés ici et " +
                            "partiront automatiquement au retour d'Internet ; rien n'est encore confirmé par le serveur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    diagnostic?.let { DiagnosticCompact(it) }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Bouton pour continuer en mode hors ligne
                    Button(
                        onClick = { 
                            // 🆕 NOUVEAU : Navigation vers le mode hors ligne
                            onNavigateToOfflineMode()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary // 🎨 COULEUR DU THÈME au lieu du turquoise fixe
                        ),
                        modifier = Modifier.padding(horizontal = 48.dp)
                    ) {
                        Text(
                            text = "Continuer avec les données locales",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bouton pour réessayer la connexion
                    Button(
                        onClick = { viewModel.retry(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        modifier = Modifier.padding(horizontal = 48.dp)
                    ) {
                        Text(
                            text = "Réessayer la connexion",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }

                // Les autres états déclenchent une navigation automatique
                else -> {
                    // Interface de transition minimale
                    Image(
                        painter = painterResource(id = R.drawable.splash),
                        contentDescription = "Logo ToutieBudget",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .alpha(0.8f),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

/** LDA1/LDA2/LDA4/LDA5 : état compact — compte ouvert, serveur, session, synchro et problèmes réels. */
@Composable
private fun DiagnosticCompact(d: DiagnosticDemarrage) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        d.compte?.let {
            Text("Compte ouvert : $it", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        }
        Text("${d.serveur} • ${d.session}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        Text(d.synchro, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        d.problemes.forEach {
            Text("• $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB74D), textAlign = TextAlign.Center)
        }
    }
}
