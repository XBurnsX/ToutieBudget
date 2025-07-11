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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.xburnsx.toutiebudget.R
import com.xburnsx.toutiebudget.ui.login.composants.GoogleSignInButton

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

    // 🔧 CONFIGURATION GOOGLE SIGN-IN - Version fonctionnelle
    val optionsConnexionGoogle = remember {
        println("=== 🔧 CRÉATION CONFIG GOOGLE ===")

        // Configuration simple qui fonctionne (testée et validée)
        val configSimple = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()

        println("✅ Config simple créée et utilisée")
        println("📋 Scopes demandés: email, profile")
        println("💡 Cette configuration fonctionne avec tous les comptes Google")
        println("=== FIN CRÉATION CONFIG ===")

        // Utiliser la configuration simple qui fonctionne
        configSimple
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
                    val email = compte?.email ?: "utilisateur@gmail.com"
                    val nom = compte?.displayName
                    
                    println("✅ Informations du compte Google:")
                    println("   📧 Email: $email")
                    println("   👤 Nom: $nom")
                    println("   🔑 Server Auth Code: ${codeServeur ?: "Non disponible"}")
                    
                    if (codeServeur != null && codeServeur.isNotBlank()) {
                        println("✅ Code serveur disponible - Connexion avec PocketBase")
                        viewModel.gererConnexionGoogleAvecCompte(email, nom, codeServeur)
                    } else {
                        println("⚠️ Pas de code serveur - Connexion locale seulement")
                        println("💡 L'utilisateur peut quand même utiliser l'app")
                        viewModel.gererConnexionGoogleAvecCompte(email, nom, null)
                    }

                } catch (e: ApiException) {
                    println("❌ Erreur ApiException:")
                    println("   Status Code: ${e.statusCode}")
                    println("   Message: ${e.message}")
                    println("   Localized Message: ${e.localizedMessage}")
                    println("   Cause: ${e.cause}")
                    println("   Stack Trace: ${e.stackTrace.joinToString("\n")}")
                    
                    // Codes d'erreur Google Sign-In spécifiques
                    when (e.statusCode) {
                        10 -> println("   -> DEVELOPER_ERROR: Configuration incorrecte")
                        12500 -> println("   -> SIGN_IN_REQUIRED: Utilisateur non connecté")
                        12501 -> println("   -> SIGN_IN_CANCELLED: Connexion annulée")
                        12502 -> println("   -> SIGN_IN_CURRENTLY_IN_PROGRESS: Connexion en cours")
                        else -> println("   -> Code d'erreur inconnu: ${e.statusCode}")
                    }
                    
                    viewModel.gererConnexionGoogle(null)
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
                }
                
                // Vérifier l'état de Google Play Services
                val googleApiAvailability = GoogleApiAvailability.getInstance()
                val playServicesStatus = googleApiAvailability.isGooglePlayServicesAvailable(contexte)
                
                if (playServicesStatus != ConnectionResult.SUCCESS) {
                    println("   ⚠️ Google Play Services non disponible: $playServicesStatus")
                    println("   💡 Cela peut causer l'échec immédiat de Google Sign-In")
                }
                
                println("   💡 Possible causes:")
                println("   - Google Play Services manquant ou pas à jour")
                println("   - Configuration SHA-1 incorrecte")
                println("   - Client ID incorrect")
                println("   - Permissions manquantes")
                
                viewModel.gererConnexionGoogle(null)
            }
            else -> {
                println("❓ Code de résultat inattendu: ${resultat.resultCode}")
                viewModel.gererConnexionGoogle(null)
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
            else -> {
                InterfaceConnexion(
                    etatUi = etatUi,
                    onConnexionGoogle = {
                        println("🔧 === DÉBUT CONNEXION GOOGLE ===")
                        
                        // Vérifier Google Play Services avant de lancer l'intent
                        val googleApiAvailability = GoogleApiAvailability.getInstance()
                        val playServicesStatus = googleApiAvailability.isGooglePlayServicesAvailable(contexte)
                        
                        if (playServicesStatus != ConnectionResult.SUCCESS) {
                            println("❌ Google Play Services non disponible: $playServicesStatus")
                            viewModel.gererConnexionGoogle(null)
                            return@InterfaceConnexion
                        }
                        
                        try {
                            println("🔄 Déconnexion préalable pour forcer la sélection de compte...")
                            // Déconnecter d'abord pour forcer la sélection du compte
                            clientConnexionGoogle.signOut().addOnCompleteListener {
                                println("✅ Déconnexion effectuée")
                                
                                println("📱 Création intent Google Sign-In...")
                                val intentConnexion = clientConnexionGoogle.signInIntent
                                println("✅ Intent créé avec succès")
                                println("📋 Intent: ${intentConnexion.toString()}")
                                
                                println("🚀 Lancement de l'intent...")
                                lanceurConnexionGoogle.launch(intentConnexion)
                                println("✅ Intent lancé - Sélection de compte forcée")
                            }
                        } catch (e: Exception) {
                            println("❌ Erreur lors du lancement de l'intent: ${e.message}")
                            println("📋 Stack trace: ${e.stackTrace.joinToString("\n")}")
                            viewModel.gererConnexionGoogle(null)
                        }
                    },
                    onEffacerErreur = { viewModel.effacerErreur() }
                )
            }
        }
    }

    // Vérification de connexion existante au démarrage
    LaunchedEffect(Unit) {
        viewModel.verifierConnexionExistante()
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
 * Interface de connexion avec ton image en arrière-plan
 */
@Composable
private fun InterfaceConnexion(
    etatUi: EtatLoginUi,
    onConnexionGoogle: () -> Unit,
    onEffacerErreur: () -> Unit
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

        // Affichage des erreurs avec fond
        etatUi.erreur?.let { messageErreur ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "❌ Erreur de connexion",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = messageErreur,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onEffacerErreur) {
                            Text("Réessayer")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Note de bas avec fond semi-transparent
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = "🔒 Connexion sécurisée\nGérez votre budget en toute confiance",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
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
            messageChargement = ""
        ),
        // État de chargement
        EtatLoginUi(
            estEnChargement = true,
            connexionReussie = false,
            erreur = null,
            messageChargement = "Connexion avec Google en cours..."
        ),
        // État avec erreur
        EtatLoginUi(
            estEnChargement = false,
            connexionReussie = false,
            erreur = "Impossible de se connecter au serveur PocketBase. Vérifiez votre connexion internet.",
            messageChargement = ""
        ),
        // État de connexion réussie
        EtatLoginUi(
            estEnChargement = false,
            connexionReussie = true,
            erreur = null,
            messageChargement = "Connexion réussie !"
        )
    )
}

/**
 * Preview principal du LoginScreen
 */
@Preview(
    name = "LoginScreen - État Normal",
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun LoginScreenPreview() {
    com.xburnsx.toutiebudget.ui.theme.ToutieBudgetTheme {
        LoginScreenContenu(
            etatUi = EtatLoginUi(
                estEnChargement = false,
                connexionReussie = false,
                erreur = null,
                messageChargement = ""
            ),
            onConnexionGoogle = { /* Preview - pas d'action */ },
            onEffacerErreur = { /* Preview - pas d'action */ }
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
    onEffacerErreur: () -> Unit
) {
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
                    onEffacerErreur = onEffacerErreur
                )
            }
        }
    }
}