// chemin/simule: /ui/theme/Theme.kt
package com.xburnsx.toutiebudget.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Enum pour les couleurs de thème disponibles
enum class CouleurTheme(val couleur: Color, val nom: String) {
    PINK(ToutiePink, "Rose"),
    RED(ToutieRed, "Rouge")
}

private fun createDarkColorScheme(couleurPrimaire: Color) = darkColorScheme(
    primary = couleurPrimaire,
    onPrimary = TextPrimary,
    secondary = couleurPrimaire,
    onSecondary = TextPrimary,
    error = couleurPrimaire,
    onError = TextPrimary,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
)

@Composable
fun ToutieBudgetTheme(
    darkTheme: Boolean = true,
    couleurTheme: CouleurTheme = CouleurTheme.PINK,
    content: @Composable () -> Unit
) {
    val colorScheme = createDarkColorScheme(couleurTheme.couleur)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
