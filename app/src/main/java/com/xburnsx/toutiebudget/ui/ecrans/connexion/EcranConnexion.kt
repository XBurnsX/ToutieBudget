package com.xburnsx.toutiebudget.ui.ecrans.connexion

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.xburnsx.toutiebudget.R

@Composable
fun EcranConnexion(
    viewModel: ViewModelConnexion = hiltViewModel(),
    surConnexionReussie: () -> Unit
) {
    val state by viewModel.etat.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("127120738889-17c2uojtvtl2tuqbh4eksdv96faktm28.apps.googleusercontent.com")
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.idToken?.let { idToken ->
                    viewModel.connexionAvecGoogle(idToken)
                }
            } catch (e: ApiException) {
                Toast.makeText(context, "Erreur de connexion Google: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        }
    )

    LaunchedEffect(key1 = state) {
        if (state.connexionReussie) {
            Toast.makeText(context, "Connexion réussie !", Toast.LENGTH_SHORT).show()
            surConnexionReussie()
        }
        state.erreurConnexion?.let {
            Toast.makeText(context, "Erreur: $it", Toast.LENGTH_LONG).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Temporairement commenté pour éviter l'erreur de compilation
        // Image(
        //     painter = painterResource(id = R.drawable.login),
        //     contentDescription = "Image de fond",
        //     modifier = Modifier.fillMaxSize(),
        //     contentScale = ContentScale.Crop
        // )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Toutie Budget", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(modifier = Modifier.height(128.dp))
            if (state.isLoading) {
                CircularProgressIndicator()
            } else {
                Button(onClick = { launcher.launch(googleSignInClient.signInIntent) }) {
                    Text("Se connecter avec Google")
                }
            }
        }
    }
}
