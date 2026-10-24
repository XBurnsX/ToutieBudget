package com.xburnsx.toutiebudget.ui.startup

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xburnsx.toutiebudget.R
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.delay
import android.util.Log

/**
 * Écran d'initialisation qui s'affiche APRÈS la connexion de l'utilisateur
 * Utilise le même design que StartupScreen mais implémente l'import des données de PocketBase
 */
@Composable
fun PostLoginStartupScreen(
    onInitializationComplete: () -> Unit
) {
    val currentStepState = remember { mutableStateOf(0) }
    val currentStep = currentStepState.value
    val totalSteps = 4
    
    // Animations pour le splash screen (mêmes que StartupScreen)
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

    // Lancer l'import initial des données depuis PocketBase
    LaunchedEffect(Unit) {
        // 🚀 DÉBUT DE L'INITIALISATION POST-LOGIN
        delay(1000) // Délai initial pour l'animation du logo
        
        try {
            Log.d("PostLoginStartup", "🚀 Début de l'import initial")
            
            // Vérifier que l'utilisateur est bien connecté avant de lancer l'import
            var tentatives = 0
            while (tentatives < 5 && !PocketBaseClient.estConnecte()) {
                Log.d("PostLoginStartup", "⏳ Attente de la connexion... (tentative ${tentatives + 1}/5)")
                delay(500)
                tentatives++
            }
            
            if (!PocketBaseClient.estConnecte()) {
                Log.e("PostLoginStartup", "❌ Utilisateur non connecté après 5 tentatives")
                currentStepState.value = -1
                onInitializationComplete()
                return@LaunchedEffect
            }
            
            val utilisateur = PocketBaseClient.obtenirUtilisateurConnecte()
            Log.d("PostLoginStartup", "✅ Utilisateur connecté: ${utilisateur?.id}")
            
            // 📱 Tentative de récupération du service d'import...
            val importService = AppModule.provideInitialImportService()
            Log.d("PostLoginStartup", "✅ Service d'import récupéré")
            
            // Configurer le callback de progression
            importService.onProgressUpdate = { step, message ->
                // 📊 PROGRESSION: Étape $step - $message
                currentStepState.value = step
            }
            
            // 🔥 LANCEMENT DE L'IMPORT RÉEL DES DONNÉES...
            // Lancer l'import réel des données
            val result = importService.importerDonneesInitiales()
            // 📋 RÉSULTAT IMPORT: $result
            
            if (result.isSuccess) {
                // 🎉 IMPORT RÉUSSI! Navigation vers l'écran principal
                Log.d("PostLoginStartup", "✅ Import réussi!")
                onInitializationComplete()
            } else {
                val erreur = result.exceptionOrNull()
                Log.e("PostLoginStartup", "❌ ERREUR IMPORT: ${erreur?.message}", erreur)
                // En cas d'erreur, on continue quand même (mode offline)
                // ⚠️ Mode offline - continuation sans import
                // Mais on affiche l'erreur à l'utilisateur
                currentStepState.value = -1 // Code d'erreur
                onInitializationComplete()
            }
            
        } catch (e: Exception) {
            Log.e("PostLoginStartup", "❌ Exception lors de l'import: ${e.message}", e)
            // En cas d'erreur, on continue quand même (mode offline)
            currentStepState.value = -1 // Code d'erreur
            onInitializationComplete()
        }
    }

    // Interface utilisateur avec fond sombre (même style que StartupScreen)
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
                modifier = Modifier.size(160.dp),
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

            // Texte de l'étape actuelle
            val stepText = when (currentStep) {
                0 -> "Préparation..."
                1 -> "Vérification de la connexion..."
                2 -> "Import des comptes et catégories..."
                3 -> "Import des transactions et enveloppes..."
                4 -> "Finalisation et synchronisation..."
                else -> "Initialisation en cours..."
            }
            
            Text(
                text = stepText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}
