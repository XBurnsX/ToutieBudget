package com.xburnsx.toutiebudget.utils

import android.content.Context
import androidx.core.content.edit

/**
 * Gestionnaire centralisé des préférences UI et notifications
 */
object PreferencesManager {
    private const val PREFS_NAME = "toutie_prefs"

    private const val KEY_FIGER_PRET_A_PLACER = "figer_pret_a_placer"
    private const val KEY_NOTIF_ENABLED = "notif_enabled"
    private const val KEY_NOTIF_OBJ_JOURS_AVANT = "notif_obj_jours_avant"
    private const val KEY_NOTIF_ENV_NEGATIF = "notif_env_negatif"
    private const val KEY_NOTIF_PAIEMENT_JOUR = "notif_paiement_jour"
    private const val KEY_SHOW_ARCHIVED = "show_archived"
    private const val KEY_ARCHIVE_AUTO_ENABLED = "archive_auto_enabled"
    private const val KEY_ARCHIVE_MONTHS = "archive_months"
    private const val KEY_WELCOME_SHOWN = "welcome_animation_shown"
    private const val KEY_WELCOME_SHOWN_V2 = "welcome_animation_shown_v2"

    fun setFigerPretAPlacer(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_FIGER_PRET_A_PLACER, enabled) }
    }

    fun getFigerPretAPlacer(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FIGER_PRET_A_PLACER, false)
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_NOTIF_ENABLED, enabled) }
    }

    fun getNotificationsEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NOTIF_ENABLED, true)
    }

    fun setNotifObjJoursAvant(context: Context, jours: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putInt(KEY_NOTIF_OBJ_JOURS_AVANT, jours) }
    }

    fun getNotifObjJoursAvant(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_NOTIF_OBJ_JOURS_AVANT, 3)
    }

    fun setNotifEnveloppeNegative(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_NOTIF_ENV_NEGATIF, enabled) }
    }

    fun getNotifEnveloppeNegative(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NOTIF_ENV_NEGATIF, true)
    }

    fun setNotifPaiementJour(context: Context, jour: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putInt(KEY_NOTIF_PAIEMENT_JOUR, jour) }
    }

    fun getNotifPaiementJour(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_NOTIF_PAIEMENT_JOUR, 1)
    }

    // Affichage des archivés
    fun setShowArchived(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_SHOW_ARCHIVED, enabled) }
    }

    fun getShowArchived(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_ARCHIVED, false)
    }

    // Archivage auto
    fun setArchiveAutoEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_ARCHIVE_AUTO_ENABLED, enabled) }
    }

    fun getArchiveAutoEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ARCHIVE_AUTO_ENABLED, false)
    }

    fun setArchiveMonths(context: Context, months: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putInt(KEY_ARCHIVE_MONTHS, months) }
    }

    fun getArchiveMonths(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_ARCHIVE_MONTHS, 6)
    }

    // Animation de bienvenue (affichée une seule fois au premier lancement)
    fun setWelcomeShown(context: Context, shown: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_WELCOME_SHOWN, shown) }
    }

    fun getWelcomeShown(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_WELCOME_SHOWN, false)
    }

    // Nouvelle version de l'animation (emplacement/visuel revu)
    fun setWelcomeShownV2(context: Context, shown: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_WELCOME_SHOWN_V2, shown) }
    }

    fun getWelcomeShownV2(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_WELCOME_SHOWN_V2, false)
    }
}

// (Plus d'extensions top-level — tout est géré dans l'objet)

