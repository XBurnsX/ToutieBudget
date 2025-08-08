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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Installer le splash screen natif
        installSplashScreen()
        
        super.onCreate(savedInstanceState)

        // Initialisation cache supprimée

        setContent {
            // Le thème est maintenant géré dynamiquement dans AppNavigation
            Surface(modifier = Modifier.fillMaxSize()) {
                AppNavigation()
            }
        }
    }
}
