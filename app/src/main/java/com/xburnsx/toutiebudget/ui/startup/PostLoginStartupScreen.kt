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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    // Échec d'import : message + choix explicite (LD8, LD10, LD19) au lieu d'aller au Budget en silence
    var erreurImport by remember { mutableStateOf<String?>(null) }
    var donneesLocalesDisponibles by remember { mutableStateOf(false) }
    var tentativeImport by remember { mutableIntStateOf(0) }
    var navigationFaite by remember { mutableStateOf(false) }
    // LD18 : étapes réellement terminées, avec ce qui vient du serveur et ce qui reste de l'appareil
    var etapesFaites by remember { mutableStateOf(listOf<String>()) }
    val terminer = {
        // Un seul parcours de navigation, même en cas de double appui (LD14)
        if (!navigationFaite) {
            navigationFaite = true
            onInitializationComplete()
        }
    }

    // Lancer l'import initial des données depuis PocketBase (relancé par « Réessayer »)
    LaunchedEffect(tentativeImport) {
        erreurImport = null
        currentStepState.value = 0
        delay(if (tentativeImport == 0) 1000 else 300) // Délai initial pour l'animation du logo

        val importService = AppModule.provideInitialImportService()
        try {
            // Vérifier que l'utilisateur est bien connecté avant de lancer l'import
            var tentatives = 0
            while (tentatives < 5 && !PocketBaseClient.estConnecte()) {
                delay(500)
                tentatives++
            }
            if (!PocketBaseClient.estConnecte()) {
                donneesLocalesDisponibles = false
                erreurImport = "Session introuvable. Reconnecte-toi."
                return@LaunchedEffect
            }

            importService.onProgressUpdate = { step, _ -> currentStepState.value = step }
            etapesFaites = emptyList()
            importService.onCollectionImportee = { collection, recues, gardees ->
                val nom = when (collection) {
                    "comptes_cheques" -> "Comptes chèques"; "comptes_credits" -> "Cartes"; "comptes_dettes" -> "Dettes"
                    "comptes_investissement" -> "Placements"; "categories" -> "Catégories"; "tiers" -> "Tiers"
                    "pret_personnel" -> "Prêts"; "enveloppes" -> "Enveloppes"; "allocations_mensuelles" -> "Allocations"
                    "transactions" -> "Transactions"; "historique_allocation" -> "Journal du budget"
                    "alias_bancaires" -> "Alias bancaires"; "operations_bancaires_attente" -> "Opérations bancaires"
                    else -> collection
                }
                etapesFaites = etapesFaites + ("✓ $nom : $recues du serveur" + if (gardees > 0) ", $gardees gardées de l'appareil" else "")
            }
            val result = importService.importerDonneesInitiales()

            if (result.isSuccess) {
                terminer()
            } else {
                Log.e("PostLoginStartup", "Import incomplet: ${result.exceptionOrNull()?.javaClass?.simpleName}")
                donneesLocalesDisponibles = importService.possedeDonneesLocales()
                erreurImport = result.exceptionOrNull()?.message ?: "L'import des données a échoué."
            }
        } catch (e: Exception) {
            Log.e("PostLoginStartup", "Exception lors de l'import: ${e.javaClass.simpleName}")
            donneesLocalesDisponibles = runCatching { importService.possedeDonneesLocales() }.getOrDefault(false)
            erreurImport = e.message ?: "L'import des données a échoué."
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

            if (erreurImport == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }

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
            
            val erreur = erreurImport
            if (erreur == null) {
                Text(
                    text = stepText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                etapesFaites.takeLast(6).forEach { etape ->
                    Text(etape, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                }
            } else {
                Text(
                    text = erreur,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFF8A80),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { tentativeImport++ }) { Text("Réessayer") }
                if (donneesLocalesDisponibles) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { terminer() }) {
                        Text("Continuer avec les données locales", color = Color.White)
                    }
                    Text(
                        text = "Certaines données du serveur pourraient manquer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
