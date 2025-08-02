// chemin/simule: /MainActivity.kt
// Dépendances: ui/navigation/AppNavigation.kt

package com.xburnsx.toutiebudget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.xburnsx.toutiebudget.ui.navigation.AppNavigation
import com.xburnsx.toutiebudget.di.AppModule

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Installer le splash screen natif
        installSplashScreen()
        
        super.onCreate(savedInstanceState)

        // Initialiser les services de cache avec priorité aux modifications
        AppModule.initializeCacheServices(this)

        setContent {
            // Le thème est maintenant géré dynamiquement dans AppNavigation
            Surface(modifier = Modifier.fillMaxSize()) {
                AppNavigation()
            }
        }
    }
}
