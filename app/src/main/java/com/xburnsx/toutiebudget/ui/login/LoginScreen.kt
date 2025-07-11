// chemin/simule: app/src/main/java/com/xburnsx/toutiebudget/ui/login/LoginScreen.kt
// Dépendances: LoginViewModel, GoogleSignInButton, Diagnostic Google Sign-In

package com.xburnsx.toutiebudget.ui.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.xburnsx.toutiebudget.R
import com.xburnsx.toutiebudget.ui.login.composants.GoogleSignInButton
import com.xburnsx.toutiebudget.utils.Sha1Helper
import com.xburnsx.toutiebudget.utils.KeystoreDiagnostic

/**
 * Écran de connexion avec authentification Google OAuth2 et diagnostic complet
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit
) {
    val etatUi by viewModel.etatUi.collectAsState()
    val contexte = LocalContext.current

    // Effet pour naviguer après connexion réussie
    LaunchedEffect(etatUi.connexionReussie) {
        if (etatUi.connexionReussie) {
            onLoginSuccess()
        }
    }

    // Configuration Google Sign-In
    val optionsConnexionGoogle = remember {
        val webClientId = com.xburnsx.toutiebudget.BuildConfig.GOOGLE_WEB_CLIENT_ID
        val config = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(webClientId)
            .requestServerAuthCode(webClientId, /* forceRefresh = */ true)
            .build()
        config
    }

    // Vérification de connexion existante
    LaunchedEffect(Unit) {
        // Vérifier la connexion existante en premier
        viewModel.verifierConnexionExistante(contexte)
    }

    // Configuration du launcher Google Sign-In
    val lanceurConnexionGoogle = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { resultat: ActivityResult ->
        when (resultat.resultCode) {
            Activity.RESULT_OK -> {
                val tache = GoogleSignIn.getSignedInAccountFromIntent(resultat.data)
                try {
                    val compte = tache.getResult(ApiException::class.java)

                    val codeServeur = compte?.serverAuthCode
                    val idToken = compte?.idToken
                    val email = compte?.email ?: "utilisateur@gmail.com"
                    val nom = compte?.displayName
                    
                    if (codeServeur != null && codeServeur.isNotBlank()) {
                        viewModel.gererConnexionGoogleAvecCompte(email, nom, codeServeur, idToken, contexte)
                    } else if (idToken != null && idToken.isNotBlank()) {
                        viewModel.gererConnexionGoogleAvecCompte(email, nom, null, idToken, contexte)
                    } else {
                        viewModel.gererConnexionGoogleAvecCompte(email, nom, null, null, contexte)
                    }
                } catch (e: ApiException) {
                    val messageErreur = when (e.statusCode) {
                        12500 -> "SIGN_IN_REQUIRED"
                        12501 -> "SIGN_IN_CANCELLED"
                        12502 -> "SIGN_IN_CURRENTLY_IN_PROGRESS"
                        10 -> "DEVELOPER_ERROR (Configuration incorrecte)"
                        else -> "Erreur Google Sign-In: ${e.statusCode}"
                    }
                    viewModel.gererConnexionGoogleAvecCompte("utilisateur@gmail.com", "Utilisateur", null, null, contexte)
                }
            }
            Activity.RESULT_CANCELED -> {
                viewModel.gererConnexionGoogleAvecCompte("utilisateur@gmail.com", "Utilisateur", null, null, contexte)
            }
            else -> {
                viewModel.gererConnexionGoogleAvecCompte("utilisateur@gmail.com", "Utilisateur", null, null, contexte)
            }
        }
    }

    // Interface utilisateur
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo et titre
            Image(
                painter = painterResource(id = R.drawable.login),
                contentDescription = "Logo ToutieBudget",
                modifier = Modifier
                    .size(120.dp)
                    .padding(bottom = 32.dp),
                contentScale = ContentScale.Fit
            )

            Text(
                text = "ToutieBudget",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.Default,
                    fontSize = 32.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Gestionnaire de budget intelligent",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // Bouton de connexion Google
            GoogleSignInButton(
                onClick = {
                    lanceurConnexionGoogle.launch(
                        GoogleSignIn.getClient(contexte, optionsConnexionGoogle).signInIntent
                    )
                },
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Indicateur de chargement
            if (etatUi.estEnChargement) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )
                Text(
                    text = etatUi.messageChargement,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }

            // Message d'erreur
            etatUi.erreur?.let { erreur ->
                Text(
                    text = erreur,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            // Mode debug (optionnel)
            if (etatUi.modeDebug) {
                DebugSection(etatUi, viewModel)
            }
        }
    }
}

/**
 * Section de debug pour afficher les logs et informations techniques
 */
@Composable
private fun DebugSection(
    etatUi: EtatLoginUi,
    viewModel: LoginViewModel
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2D2D2D)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔧 Mode Debug",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Text(
                        text = if (isExpanded) "▼" else "▶",
                        color = Color.White
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Boutons d'action debug
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { viewModel.basculerModeDebug() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("Basculer Debug")
                    }
                    
                    Button(
                        onClick = { viewModel.effacerLogsDebug() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800)
                        )
                    ) {
                        Text("Effacer Logs")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Logs de debug
                if (etatUi.logsDebug.isNotEmpty()) {
                    Text(
                        text = "📋 Logs:",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier
                            .height(200.dp)
                            .background(Color(0xFF1A1A1A))
                            .padding(8.dp)
                    ) {
                        items(etatUi.logsDebug) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interface de chargement avec fond transparent
 */
@Composable
private fun InterfaceChargement(messageChargement: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        ),
        modifier = Modifier.padding(32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )

            if (messageChargement.isNotBlank()) {
                Text(
                    text = messageChargement,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Interface de debug avec logs détaillés
 */
@Composable
private fun InterfaceDebug(
    etatUi: EtatLoginUi,
    onConnexionGoogle: () -> Unit,
    onEffacerErreur: () -> Unit,
    onBasculerDebug: () -> Unit,
    onEffacerLogs: () -> Unit,
    onDiagnostic: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // En-tête debug
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.8f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔧 MODE DEBUG",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onEffacerLogs,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red.copy(alpha = 0.7f)
                        )
                    ) {
                        Text("🧹", fontSize = 12.sp)
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = onDiagnostic,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Green.copy(alpha = 0.7f)
                        )
                    ) {
                        Text("🔍", fontSize = 12.sp)
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = onBasculerDebug,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Blue.copy(alpha = 0.7f)
                        )
                    ) {
                        Text("❌", fontSize = 12.sp)
                    }
                }
            }
        }

        // Bouton de connexion
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            )
        ) {
            GoogleSignInButton(
                onClick = onConnexionGoogle,
                modifier = Modifier.fillMaxWidth(),
                text = "Se connecter avec Google"
            )
        }

        // Affichage de l'erreur
        if (etatUi.erreur != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                )
            ) {
                Text(
                    text = etatUi.erreur,
                    color = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Logs de debug
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            ),
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "📋 LOGS DE DEBUG (${etatUi.logsDebug.size} entrées)",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(etatUi.logsDebug.takeLast(50)) { log ->
                        Text(
                            text = log,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interface de connexion avec ton image en arrière-plan
 */
@Composable
private fun InterfaceConnexion(
    etatUi: EtatLoginUi,
    onConnexionGoogle: () -> Unit,
    onEffacerErreur: () -> Unit,
    onBasculerDebug: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.weight(0.1f))

        // Titre avec fond semi-transparent pour lisibilité
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "💰",
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = "Toutie Budget",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                Text(
                    text = "Votre compagnon financier",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bouton de connexion Google avec fond
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            )
        ) {
            GoogleSignInButton(
                onClick = onConnexionGoogle,
                modifier = Modifier.fillMaxWidth(),
                text = "Se connecter avec Google"
            )
        }

        // Affichage de l'erreur, s'il y en a une
        if (etatUi.erreur != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                )
            ) {
                Text(
                    text = etatUi.erreur,
                    color = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.2f))

        // Note en bas de page avec bouton debug
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.5f)
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "🔒 Connexion sécurisée\nGérez votre budget en toute confiance",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onBasculerDebug,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Blue.copy(alpha = 0.3f)
                    )
                ) {
                    Text("🔧 Mode Debug", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// =====================================================
// 🔧 PREVIEWS COMPOSE POUR ANDROID STUDIO
// =====================================================

/**
 * Provider pour simuler différents états du LoginViewModel
 */
class EtatLoginPreviewProvider : PreviewParameterProvider<EtatLoginUi> {
    override val values = sequenceOf(
        // État normal (prêt à se connecter)
        EtatLoginUi(
            estEnChargement = false,
            connexionReussie = false,
            erreur = null,
            messageChargement = "",
            modeDebug = false,
            logsDebug = emptyList()
        ),
        // État de chargement
        EtatLoginUi(
            estEnChargement = true,
            connexionReussie = false,
            erreur = null,
            messageChargement = "Connexion avec Google en cours...",
            modeDebug = false,
            logsDebug = emptyList()
        ),
        // État avec erreur
        EtatLoginUi(
            estEnChargement = false,
            connexionReussie = false,
            erreur = "Impossible de se connecter au serveur PocketBase. Vérifiez votre connexion internet.",
            messageChargement = "",
            modeDebug = false,
            logsDebug = emptyList()
        ),
        // État de connexion réussie
        EtatLoginUi(
            estEnChargement = false,
            connexionReussie = true,
            erreur = null,
            messageChargement = "Connexion réussie !",
            modeDebug = false,
            logsDebug = emptyList()
        )
    )
}

/**
 * Preview principal du LoginScreen
 */
@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun LoginScreenPreview(@PreviewParameter(EtatLoginPreviewProvider::class) etatUi: EtatLoginUi) {
    com.xburnsx.toutiebudget.ui.theme.ToutieBudgetTheme {
        LoginScreenContenu(
            etatUi = etatUi,
            onConnexionGoogle = { /* Preview - pas d'action */ },
            onEffacerErreur = { /* Preview - pas d'action */ },
            onBasculerDebug = { /* Preview - pas d'action */ }
        )
    }
}

/**
 * Composant séparé pour les previews (sans dépendances ViewModel)
 */
@Composable
private fun LoginScreenContenu(
    etatUi: EtatLoginUi,
    onConnexionGoogle: () -> Unit,
    onEffacerErreur: () -> Unit,
    onBasculerDebug: () -> Unit
) {
    val contexte = LocalContext.current

    // Interface avec TON image de fond
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // TON IMAGE DE FOND
        Image(
            painter = painterResource(id = R.drawable.login),
            contentDescription = "Image de fond de connexion Toutie Budget",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Overlay semi-transparent
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f))
        )

        // Contenu selon l'état
        when {
            etatUi.estEnChargement -> {
                InterfaceChargement(etatUi.messageChargement)
            }
            else -> {
                InterfaceConnexion(
                    etatUi = etatUi,
                    onConnexionGoogle = onConnexionGoogle,
                    onEffacerErreur = onEffacerErreur,
                    onBasculerDebug = onBasculerDebug
                )
            }
        }
    }
}