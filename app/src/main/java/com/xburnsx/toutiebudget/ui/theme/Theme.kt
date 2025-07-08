package com.xburnsx.toutiebudget.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color


private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB30000),  // Rouge principal
    secondary = Color(0xFFF44336),  // Rouge clair
    tertiary = Color(0xFFB71C1C),  // Rouge foncé
    background = Color(0xFF121212),  // Noir
    surface = Color(0xFF121212),  // Gris très foncé pour les surfaces
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,  // Texte en blanc sur fond noir
    onSurface = Color.White,
    error = Color(0xFFEF5350),  // Rouge d'erreur plus clair
    onError = Color.Black
)

@Composable
fun ToutieBudgetTheme(
    content: @Composable () -> Unit
) {
    // On applique directement notre palette sombre personnalisée
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}