// chemin/simule: app/src/main/java/com/xburnsx/toutiebudget/ui/login/LoginScreen.kt
// Dépendances: LoginViewModel, GoogleSignInButton

package com.xburnsx.toutiebudget.ui.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            .requestServerAuthCode(webClientId, /* forceRefresh = */ true)
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

    // Interface utilisateur avec image de fond
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Image de fond en plein écran
        Image(
            painter = painterResource(id = R.drawable.login),
            contentDescription = "Fond de connexion",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Contenu par-dessus l'image
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
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
        }
    }
}