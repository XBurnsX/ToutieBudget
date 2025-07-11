// chemin/simule: /ui/login/LoginScreen.kt
package com.xburnsx.toutiebudget.ui.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.xburnsx.toutiebudget.BuildConfig
import com.xburnsx.toutiebudget.R
import com.xburnsx.toutiebudget.ui.login.composants.GoogleSignInButton

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) {
            onLoginSuccess()
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                // On appelle la bonne fonction du ViewModel
                viewModel.handleGoogleLogin(account?.serverAuthCode)
            } catch (e: ApiException) {
                viewModel.handleGoogleLogin(null)
            }
        } else {
            viewModel.handleGoogleLogin(null)
        }
    }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestServerAuthCode(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.login),
            contentDescription = "Image de fond de connexion",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
        )
        if (uiState.isLoading) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Spacer(modifier = Modifier.weight(2.4f))

                // CORRECTION : On utilise notre nouveau bouton personnalisé
                GoogleSignInButton(
                    onClick = {
                        val signInIntent = googleSignInClient.signInIntent
                        googleSignInLauncher.launch(signInIntent)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.weight(0.5f))
            }
        }
    }
}
