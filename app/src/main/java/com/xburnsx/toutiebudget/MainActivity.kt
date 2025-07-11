// chemin/simule: /MainActivity.kt
// Dépendances: ui/theme/ToutieBudgetTheme.kt, ui/navigation/AppNavigation.kt

package com.xburnsx.toutiebudget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.xburnsx.toutiebudget.ui.navigation.AppNavigation
import com.xburnsx.toutiebudget.ui.theme.ToutieBudgetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // On applique notre thème personnalisé
            ToutieBudgetTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // On lance la navigation principale de l'application
                    AppNavigation()
                }
            }
        }
    }
}
