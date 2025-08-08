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

    // ✅ FORCE TOUS LES DIALOGS À AVOIR LA MÊME COULEUR QUE LE BACKGROUND !
    surface = DarkBackground,
    onSurface = TextPrimary,
    surfaceVariant = TextPrimary,           // ✅ Même couleur !
    onSurfaceVariant = TextPrimary,
    surfaceTint = TextPrimary,              // ✅ AJOUTE ÇA
    surfaceBright = TextPrimary,            // ✅ AJOUTE ÇA
    surfaceDim = TextPrimary,               // ✅ AJOUTE ÇA
    surfaceContainer = TextPrimary,
    surfaceContainerHigh = TextPrimary,
    surfaceContainerHighest = TextPrimary,
    surfaceContainerLow = TextPrimary,      // ✅ AJOUTE ÇA
    surfaceContainerLowest = TextPrimary,   // ✅ AJOUTE ÇA
    inverseSurface = TextPrimary,           // ✅ AJOUTE ÇA
)

@Suppress("DEPRECATION")
@Composable
fun ToutieBudgetTheme(
    darkTheme: Boolean = true,
    couleurTheme: CouleurTheme = CouleurTheme.RED,
    content: @Composable () -> Unit
) {
    val colorScheme = createDarkColorScheme(couleurTheme.couleur)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Utiliser WindowInsetsController
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            // Utiliser les méthodes au lieu des propriétés dépréciées
            window.setStatusBarColor(colorScheme.background.toArgb())
            window.setNavigationBarColor(colorScheme.background.toArgb())
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}