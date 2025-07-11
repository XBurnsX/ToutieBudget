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
import com.xburnsx.toutiebudget.utils.TestGoogleSignIn
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

    // 🔧 CONFIGURATION GOOGLE SIGN-IN - Configuration PocketBase (serverAuthCode permanent)
    val optionsConnexionGoogle = remember {
        println("=== 🔧 CRÉATION CONFIG GOOGLE (PocketBase) ===")
        val webClientId = com.xburnsx.toutiebudget.BuildConfig.GOOGLE_WEB_CLIENT_ID
        val config = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(webClientId)
            .requestServerAuthCode(webClientId, /* forceRefresh = */ true) // Important : true pour recevoir un nouveau code à chaque connexion
            .build()
        println("✅ Config créée avec serverAuthCode (forceRefresh=true)")
        config
    }

    // 🔧 VÉRIFICATION GOOGLE PLAY SERVICES
    LaunchedEffect(Unit) {
        println("=== 🔧 VÉRIFICATION GOOGLE PLAY SERVICES ===")
        
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(contexte)
        
        when (resultCode) {
            ConnectionResult.SUCCESS -> {
                println("✅ Google Play Services : Disponible et à jour")
            }
            ConnectionResult.SERVICE_MISSING -> {
                println("❌ Google Play Services : Manquant")
                println("💡 Installez Google Play Services depuis le Play Store")
            }
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
                println("⚠️ Google Play Services : Mise à jour requise")
                println("💡 Mettez à jour Google Play Services depuis le Play Store")
            }
            ConnectionResult.SERVICE_DISABLED -> {
                println("❌ Google Play Services : Désactivé")
                println("💡 Activez Google Play Services dans les paramètres")
            }
            else -> {
                println("❌ Google Play Services : Erreur $resultCode")
                println("💡 Vérifiez l'état de Google Play Services")
            }
        }

        // Informations détaillées sur l'environnement
        println("=== 🔧 INFORMATIONS ENVIRONNEMENT ===")
        println("📱 Package Name: ${contexte.packageName}")
        println("🔧 Build Config: ${com.xburnsx.toutiebudget.BuildConfig.GOOGLE_WEB_CLIENT_ID}")
        println("🔧 Mode Debug: ${com.xburnsx.toutiebudget.BuildConfig.EST_MODE_DEBUG}")
        
        // Informations sur l'émulateur
        val detecteur = com.xburnsx.toutiebudget.utils.DetecteurEmulateur.obtenirInfoEnvironnement()
        println("🔍 Détection émulateur:")
        detecteur.split("\n").forEach { ligne ->
            println("   $ligne")
        }

        println("=== 🔧 TEST CONFIGURATIONS ===")

        // Test 1 : Config simple
        try {
            val clientSimple = GoogleSignIn.getClient(
                contexte,
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .build()
            )
            println("✅ Client simple : OK")
        } catch (e: Exception) {
            println("❌ Client simple : ${e.message}")
        }

        // Test 2 : Config avec Web Client ID
        try {
            val webClientId = com.xburnsx.toutiebudget.BuildConfig.GOOGLE_WEB_CLIENT_ID
            val clientAvecServerCode = GoogleSignIn.getClient(
                contexte,
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestServerAuthCode(webClientId)
                    .build()
            )
            println("✅ Client avec server code : OK")
        } catch (e: Exception) {
            println("❌ Client avec server code : ${e.message}")
        }

        println("=== FIN TEST CONFIGURATIONS ===")
        
        // 🔍 DIAGNOSTIC SHA-1
        Sha1Helper.afficherDiagnosticSha1(contexte)
        
        // 🔍 TEST GOOGLE SIGN-IN ISOLÉ
        println("🔍 === LANCEMENT TEST GOOGLE SIGN-IN ===")
        val testGoogleSignIn = TestGoogleSignIn.testerGoogleSignIn(contexte)
        println(testGoogleSignIn)
        
        // 🔍 TEST GOOGLE PLAY SERVICES
        val testGooglePlayServices = TestGoogleSignIn.testerGooglePlayServices(contexte)
        println(testGooglePlayServices)
        
        // 🔍 DIAGNOSTIC KEYSTORE DÉTAILLÉ
        KeystoreDiagnostic.afficherDiagnosticKeystore(contexte)
        KeystoreDiagnostic.comparerAvecDebugStandard()
    }

    // Configuration du launcher Google Sign-In avec diagnostic détaillé
    val lanceurConnexionGoogle = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { resultat: ActivityResult ->
        println("=== 🔧 DEBUG RETOUR GOOGLE SIGN-IN ===")
        println("📊 Result Code: ${resultat.resultCode}")
        println("📊 RESULT_OK = ${Activity.RESULT_OK}")
        println("📊 RESULT_CANCELED = ${Activity.RESULT_CANCELED}")
        println("📊 Intent Data: ${resultat.data}")

        when (resultat.resultCode) {
            Activity.RESULT_OK -> {
                println("✅ Résultat OK - Traitement...")
                val tache = GoogleSignIn.getSignedInAccountFromIntent(resultat.data)
                try {
                    val compte = tache.getResult(ApiException::class.java)
                    println("✅ Compte obtenu: ${compte?.email}")
                    println("✅ Display Name: ${compte?.displayName}")
                    println("✅ ID: ${compte?.id}")
                    println("✅ Server Auth Code: ${compte?.serverAuthCode}")
                    println("✅ ID Token: ${compte?.idToken}")

                    val codeServeur = compte?.serverAuthCode
                    val idToken = compte?.idToken
                    val email = compte?.email ?: "utilisateur@gmail.com"
                    val nom = compte?.displayName
                    
                    println("✅ Informations du compte Google:")
                    println("   📧 Email: $email")
                    println("   👤 Nom: $nom")
                    println("   🔑 Server Auth Code: ${codeServeur ?: "Non disponible"}")
                    println("   🎫 ID Token: ${idToken ?: "Non disponible"}")
                    
                    if (codeServeur != null && codeServeur.isNotBlank()) {
                        println("✅ Code serveur disponible - Connexion avec PocketBase")
                        viewModel.gererConnexionGoogleAvecCompte(email, nom, codeServeur, idToken, contexte)
                    } else if (idToken != null && idToken.isNotBlank()) {
                        println("✅ ID Token disponible - Connexion avec ID Token")
                        viewModel.gererConnexionGoogleAvecCompte(email, nom, null, idToken, contexte)
                    } else {
                        println("❌ Aucun code d'autorisation ni ID token reçu – échec de la connexion sécurisée")
                        viewModel.gererConnexionGoogle(null, contexte)
                    }
                } catch (e: Exception) {
                    println("❌ Erreur lors de la récupération du compte Google: ${e.message}")
                    viewModel.gererConnexionGoogle(null, contexte)
                }
            }
            Activity.RESULT_CANCELED -> {
                println("🚫 Connexion annulée - Analyse détaillée :")
                println("   📋 Intent data: ${resultat.data}")
                println("   📋 Intent extras: ${resultat.data?.extras}")
                
                // Vérifier s'il y a des informations d'erreur dans l'intent
                resultat.data?.let { data ->
                    val errorKey = "errorCode"
                    if (data.hasExtra(errorKey)) {
                        val errorCode = data.getIntExtra(errorKey, -1)
                        println("   🔍 Code d'erreur trouvé: $errorCode")
                        
                        when (errorCode) {
                            12500 -> println("   -> SIGN_IN_REQUIRED")
                            12501 -> println("   -> SIGN_IN_CANCELLED")
                            12502 -> println("   -> SIGN_IN_CURRENTLY_IN_PROGRESS")
                            10 -> println("   -> DEVELOPER_ERROR (Configuration incorrecte)")
                            else -> println("   -> Code d'erreur inconnu: $errorCode")
                        }
                    }
                    
                    // Vérifier d'autres clés d'erreur possibles
                    val errorKeys = listOf("error", "errorMessage", "status", "statusCode")
                    for (key in errorKeys) {
                        if (data.hasExtra(key)) {
                            val value = data.getStringExtra(key) ?: data.getIntExtra(key, -1)
                            println("   🔍 $key: $value")
                        }
                    }
                }
                
                // Vérifier l'état de Google Play Services
                val googleApiAvailability = GoogleApiAvailability.getInstance()
                val playServicesStatus = googleApiAvailability.isGooglePlayServicesAvailable(contexte)
                
                if (playServicesStatus != ConnectionResult.SUCCESS) {
                    println("   ⚠️ Google Play Services non disponible: $playServicesStatus")
                    println("   💡 Cela peut causer l'échec immédiat de Google Sign-In")
                    
                    // Informations sur le statut
                    val messageErreur = when (playServicesStatus) {
                        ConnectionResult.SERVICE_MISSING -> "Google Play Services manquant"
                        ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> "Mise à jour Google Play Services requise"
                        ConnectionResult.SERVICE_DISABLED -> "Google Play Services désactivé"
                        else -> "Erreur Google Play Services: $playServicesStatus"
                    }
                    println("   🔧 $messageErreur")
                }
                
                // Vérifier la configuration Google
                println("   🔧 Vérification de la configuration:")
                println("   - Client ID: ${com.xburnsx.toutiebudget.BuildConfig.GOOGLE_WEB_CLIENT_ID}")
                println("   - Package: ${contexte.packageName}")
                
                // Test de connectivité réseau
                println("   🌐 Test de connectivité réseau...")
                try {
                    val connectivityManager = contexte.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                    val networkInfo = connectivityManager.activeNetworkInfo
                    if (networkInfo?.isConnected == true) {
                        println("   ✅ Connexion réseau disponible")
                    } else {
                        println("   ❌ Pas de connexion réseau")
                    }
                } catch (e: Exception) {
                    println("   ⚠️ Impossible de vérifier la connectivité: ${e.message}")
                }
                
                println("   💡 Possible causes:")
                println("   - Google Play Services manquant ou pas à jour")
                println("   - Configuration SHA-1 incorrecte")
                println("   - Client ID incorrect")
                println("   - Permissions manquantes")
                println("   - Problème de connectivité réseau")
                println("   - Compte Google non configuré sur l'appareil")
                
                viewModel.gererConnexionGoogle(null, contexte)
            }
            else -> {
                println("❓ Code de résultat inattendu: ${resultat.resultCode}")
                viewModel.gererConnexionGoogle(null, contexte)
            }
        }
        println("=== FIN DEBUG RETOUR ===")
    }

    val clientConnexionGoogle = remember {
        println("🔧 === CRÉATION CLIENT GOOGLE SIGN-IN ===")
        try {
            val client = GoogleSignIn.getClient(contexte, optionsConnexionGoogle)
            println("✅ Client Google Sign-In créé avec succès")
            println("📋 Options utilisées: ${optionsConnexionGoogle.toString()}")
            client
        } catch (e: Exception) {
            println("❌ Erreur création client Google Sign-In: ${e.message}")
            println("📋 Stack trace: ${e.stackTrace.joinToString("\n")}")
            throw e
        }
    }

    // Interface avec TON image de fond
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // TON IMAGE LOGIN.PNG comme fond !
        Image(
            painter = painterResource(id = R.drawable.login),
            contentDescription = "Image de fond de connexion Toutie Budget",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Overlay semi-transparent pour lisibilité du texte
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        // Contenu principal selon l'état
        when {
            etatUi.estEnChargement -> {
                InterfaceChargement(etatUi.messageChargement)
            }
            etatUi.modeDebug -> {
                InterfaceDebug(
                    etatUi = etatUi,
                    onConnexionGoogle = {
                        println("🔧 === DÉBUT CONNEXION GOOGLE ===")
                        clientConnexionGoogle.signOut().addOnCompleteListener {
                            lanceurConnexionGoogle.launch(clientConnexionGoogle.signInIntent)
                        }
                    },
                    onEffacerErreur = { viewModel.effacerErreur() },
                    onBasculerDebug = { viewModel.basculerModeDebug() },
                    onEffacerLogs = { viewModel.effacerLogsDebug() },
                    onDiagnostic = { viewModel.lancerDiagnosticPocketBase() }
                )
            }
            else -> {
                InterfaceConnexion(
                    etatUi = etatUi,
                    onConnexionGoogle = {
                        println("🔧 === DÉBUT CONNEXION GOOGLE ===")
                        clientConnexionGoogle.signOut().addOnCompleteListener {
                            lanceurConnexionGoogle.launch(clientConnexionGoogle.signInIntent)
                        }
                    },
                    onEffacerErreur = { viewModel.effacerErreur() },
                    onBasculerDebug = { viewModel.basculerModeDebug() }
                )
            }
        }
    }

    // Vérification de connexion existante au démarrage
    LaunchedEffect(Unit) {
        viewModel.verifierConnexionExistante(contexte)
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