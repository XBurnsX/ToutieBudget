// chemin/simule: app/src/main/java/com/xburnsx/toutiebudget/ui/login/LoginScreen.kt
// Dépendances: LoginViewModel, GoogleSignInButton

package com.xburnsx.toutiebudget.ui.login

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.xburnsx.toutiebudget.R
import com.xburnsx.toutiebudget.ui.login.composants.GoogleSignInButton

/**
 * Écran de connexion avec image de fond et authentification Google OAuth2
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
            .requestServerAuthCode(webClientId, /* forceCodeForRefreshToken = */ true)
            .build()
        config
    }

    // Vérification de connexion existante
    LaunchedEffect(Unit) {
        viewModel.verifierConnexionExistante(contexte)
    }

    // Configuration du launcher Google Sign-In
    val lanceurConnexionGoogle = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { resultat: ActivityResult ->
        Log.d("LoginScreen", "📱 Résultat Google Sign-In reçu - Code: ${resultat.resultCode}")
        Log.d("LoginScreen", "📱 Intent data: ${resultat.data}")
        
        when (resultat.resultCode) {
            Activity.RESULT_OK -> {
                Log.d("LoginScreen", "✅ Google Sign-In OK, traitement du compte...")
                val tache = GoogleSignIn.getSignedInAccountFromIntent(resultat.data)
                try {
                    val compte = tache.getResult(ApiException::class.java)
                    val codeServeur = compte?.serverAuthCode
                    val idToken = compte?.idToken
                    val email = compte?.email ?: "utilisateur@gmail.com"
                    val nom = compte?.displayName
                    
                    Log.d("LoginScreen", "🔑 Code serveur: ${if (codeServeur.isNullOrBlank()) "VIDE" else "PRÉSENT"}")
                    Log.d("LoginScreen", "🎫 ID Token: ${if (idToken.isNullOrBlank()) "VIDE" else "PRÉSENT"}")
                    
                    if (codeServeur != null && codeServeur.isNotBlank()) {
                        Log.d("LoginScreen", "✅ Utilisation du code serveur pour l'authentification")
                        viewModel.gererConnexionGoogleAvecCompte(email, nom, codeServeur, idToken, contexte)
                    } else if (idToken != null && idToken.isNotBlank()) {
                        Log.d("LoginScreen", "⚠️ Pas de code serveur, utilisation de l'ID Token")
                        viewModel.gererConnexionGoogleAvecCompte(email, nom, null, idToken, contexte)
                    } else {
                        Log.e("LoginScreen", "❌ Aucun token reçu - problème de configuration SHA-1 probable")
                        // Aucun token reçu - probablement problème de configuration SHA-1
                        viewModel.gererConnexionGoogleAvecCompte(email, nom, null, null, contexte)
                    }
                } catch (e: ApiException) {
                    Log.e("LoginScreen", "❌ Erreur ApiException: ${e.statusCode} - ${e.message}", e)
                    Log.e("LoginScreen", "❌ Stack trace: ${e.stackTraceToString()}")
                    // Gérer l'exception API de manière plus explicite
                    val codeErreur = e.statusCode
                    viewModel.gererErreurGoogleSignIn(codeErreur, contexte)
                } catch (e: Exception) {
                    Log.e("LoginScreen", "❌ Erreur inattendue lors du traitement: ${e.message}", e)
                    Log.e("LoginScreen", "❌ Stack trace: ${e.stackTraceToString()}")
                    viewModel.gererConnexionGoogleAvecCompte("utilisateur@gmail.com", "Utilisateur", null, null, contexte)
                }
            }
            Activity.RESULT_CANCELED -> {
                Log.w("LoginScreen", "🚫 Code RESULT_CANCELED reçu")
                // Essayer quand même de récupérer les infos au cas où il y aurait une exception cachée
                try {
                    val tache = GoogleSignIn.getSignedInAccountFromIntent(resultat.data)
                    val compte = tache.getResult(ApiException::class.java)
                    Log.d("LoginScreen", "⚠️ Compte récupéré malgré CANCELED")
                    val codeServeur = compte?.serverAuthCode
                    val idToken = compte?.idToken
                    if (!codeServeur.isNullOrBlank() || !idToken.isNullOrBlank()) {
                        Log.d("LoginScreen", "✅ Tokens présents malgré CANCELED, tentative de connexion...")
                        viewModel.gererConnexionGoogleAvecCompte(
                            compte?.email ?: "utilisateur@gmail.com",
                            compte?.displayName,
                            codeServeur,
                            idToken,
                            contexte
                        )
                        return@rememberLauncherForActivityResult
                    }
                } catch (e: ApiException) {
                    Log.e("LoginScreen", "❌ ApiException dans RESULT_CANCELED: ${e.statusCode} - ${e.message}", e)
                    Log.e("LoginScreen", "❌ C'est probablement une erreur de configuration, pas une annulation!")
                    viewModel.gererErreurGoogleSignIn(e.statusCode, contexte)
                    return@rememberLauncherForActivityResult
                } catch (e: Exception) {
                    Log.e("LoginScreen", "❌ Exception dans RESULT_CANCELED: ${e.message}", e)
                }
                
                Log.d("LoginScreen", "🚫 Vraie annulation par l'utilisateur")
                // L'utilisateur a vraiment annulé la connexion
                viewModel.gererConnexionGoogleAvecCompte("utilisateur@gmail.com", "Utilisateur", null, null, contexte)
            }
            else -> {
                Log.e("LoginScreen", "❌ Résultat inattendu: ${resultat.resultCode}")
                // Essayer quand même de récupérer les infos
                try {
                    val tache = GoogleSignIn.getSignedInAccountFromIntent(resultat.data)
                    val compte = tache.getResult(ApiException::class.java)
                    val codeServeur = compte?.serverAuthCode
                    val idToken = compte?.idToken
                    if (!codeServeur.isNullOrBlank() || !idToken.isNullOrBlank()) {
                        viewModel.gererConnexionGoogleAvecCompte(
                            compte?.email ?: "utilisateur@gmail.com",
                            compte?.displayName,
                            codeServeur,
                            idToken,
                            contexte
                        )
                        return@rememberLauncherForActivityResult
                    }
                } catch (e: Exception) {
                    Log.e("LoginScreen", "❌ Erreur lors de la récupération: ${e.message}", e)
                }
                viewModel.gererConnexionGoogleAvecCompte("utilisateur@gmail.com", "Utilisateur", null, null, contexte)
            }
        }
    }

    // Interface utilisateur avec image de fond et cartes
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Image de fond en plein écran
        Image(
            painter = painterResource(id = R.drawable.login),
            contentDescription = "Fond de connexion",
            modifier = Modifier
                .fillMaxSize()
                .height(IntrinsicSize.Max),
            contentScale = ContentScale.FillBounds
        )

        // Overlay semi-transparent
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f))
        )

        // Contenu avec cartes
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.weight(0.05f))

            // Titre avec fond semi-transparent pour lisibilité
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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

            Spacer(modifier = Modifier.height(8.dp))

            // Bouton de connexion Google avec fond
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                )
            ) {
                GoogleSignInButton(
                    onClick = {
                        Log.d("LoginScreen", "🖱️ Bouton Google Sign-In cliqué")
                        // 🎯 FORCER LA SÉLECTION DE COMPTE : déconnecter d'abord
                        val clientGoogle = GoogleSignIn.getClient(contexte, optionsConnexionGoogle)
                        Log.d("LoginScreen", "🚪 Déconnexion Google en cours...")
                        clientGoogle.signOut().addOnCompleteListener {
                            Log.d("LoginScreen", "✅ Déconnexion terminée, lancement de Google Sign-In...")
                            // Après déconnexion, lancer la connexion (forcera le sélecteur)
                            lanceurConnexionGoogle.launch(clientGoogle.signInIntent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    text = "Se connecter avec Google"
                )
            }

            // Affichage de l'erreur, s'il y en a une
            etatUi.erreur?.let { erreur ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                    )
                ) {
                    Text(
                        text = erreur,
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Indicateur de chargement
            if (etatUi.estEnChargement) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(20.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )

                        if (etatUi.messageChargement.isNotBlank()) {
                            Text(
                                text = etatUi.messageChargement,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.3f))

            // Note en bas de page
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "🔒 Connexion sécurisée\nGérez votre budget en toute confiance\navec Toutie! Meow!",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}