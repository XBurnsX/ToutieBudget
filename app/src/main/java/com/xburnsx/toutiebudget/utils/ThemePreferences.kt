package com.xburnsx.toutiebudget.utils

import android.content.Context
import android.content.SharedPreferences
import com.xburnsx.toutiebudget.ui.theme.CouleurTheme

/**
 * Gestionnaire des préférences de thème
 * Sauvegarde et charge la couleur du thème sélectionnée par l'utilisateur
 */
object ThemePreferences {
    private const val PREFS_NAME = "theme_preferences"
    private const val KEY_COULEUR_THEME = "couleur_theme"

    /**
     * Sauvegarde la couleur du thème sélectionnée
     */
    fun sauvegarderCouleurTheme(context: Context, couleurTheme: CouleurTheme) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_COULEUR_THEME, couleurTheme.name).apply()
    }

    /**
     * Charge la couleur du thème sauvegardée
     * @return La couleur sauvegardée ou PINK par défaut
     */
    fun chargerCouleurTheme(context: Context): CouleurTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val couleurSauvegardee = prefs.getString(KEY_COULEUR_THEME, CouleurTheme.PINK.name)

        return try {
            CouleurTheme.valueOf(couleurSauvegardee ?: CouleurTheme.PINK.name)
        } catch (e: IllegalArgumentException) {
            // Si la couleur sauvegardée n'est pas valide, retourner PINK par défaut
            CouleurTheme.PINK
        }
    }
}
