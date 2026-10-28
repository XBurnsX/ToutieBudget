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
    private const val KEY_NOTIF_ECHEANCE_DETTES = "notif_echeance_dettes"
    private const val KEY_ARCHIVAGE_AUTO = "archivage_automatique"
    private const val KEY_DERNIER_ARCHIVAGE_AUTO = "dernier_archivage_automatique"
    private const val KEY_SHOW_ARCHIVED = "show_archived"
    private const val KEY_ARCHIVE_AUTO_ENABLED = "archive_auto_enabled"
    private const val KEY_ARCHIVE_MONTHS = "archive_months"
    private const val KEY_WELCOME_SHOWN = "welcome_animation_shown"
    private const val KEY_WELCOME_SHOWN_V2 = "welcome_animation_shown_v2"

    private const val KEY_CONFIRMER_GROS_VIREMENTS = "confirmer_gros_virements"
    private const val KEY_SEUIL_GROS_VIREMENT = "seuil_gros_virement"
    private const val KEY_VIREMENTS_RECENTS = "virements_recents"
    private const val NB_VIREMENTS_RECENTS = 4

    private const val KEY_MASQUER_COMPTES_ZERO = "masquer_comptes_zero"
    private const val KEY_GROUPES_COMPTES_FERMES = "groupes_comptes_fermes"

    // CA4 : masquer les comptes à zéro dans la page Comptes (désactivé par défaut)
    fun setMasquerComptesAZero(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_MASQUER_COMPTES_ZERO, enabled) }
    }

    fun getMasquerComptesAZero(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_MASQUER_COMPTES_ZERO, false)

    // CA3 : groupes de comptes repliés
    fun setGroupesComptesFermes(context: Context, groupes: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putStringSet(KEY_GROUPES_COMPTES_FERMES, groupes) }
    }

    fun getGroupesComptesFermes(context: Context): Set<String> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getStringSet(KEY_GROUPES_COMPTES_FERMES, null)?.toSet()
            ?: emptySet()

    // VA6 : confirmation facultative des gros virements (désactivée par défaut)
    fun setConfirmerGrosVirements(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_CONFIRMER_GROS_VIREMENTS, enabled) }
    }

    fun getConfirmerGrosVirements(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_CONFIRMER_GROS_VIREMENTS, false)

    fun setSeuilGrosVirement(context: Context, dollars: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putInt(KEY_SEUIL_GROS_VIREMENT, dollars) }
    }

    fun getSeuilGrosVirement(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_SEUIL_GROS_VIREMENT, 500)

    // VA4 : couples source → destination récemment utilisés (« mode|source|destination »), le plus récent d'abord
    fun ajouterVirementRecent(context: Context, cle: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val liste = (listOf(cle) + getVirementsRecents(context).filter { it != cle }).take(NB_VIREMENTS_RECENTS)
        prefs.edit { putString(KEY_VIREMENTS_RECENTS, liste.joinToString("\n")) }
    }

    fun getVirementsRecents(context: Context): List<String> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_VIREMENTS_RECENTS, null)
            ?.split("\n")?.filter { it.isNotBlank() }
            ?: emptyList()

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

    /** DA3 : rappel d'action volontaire, désactivé par défaut. */
    fun getNotifEcheanceDettes(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIF_ECHEANCE_DETTES, false)

    fun setNotifEcheanceDettes(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_NOTIF_ECHEANCE_DETTES, active) }
    }

    /** ARA6 : archivage automatique des comptes soldés, désactivé par défaut. */
    fun getArchivageAutomatique(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ARCHIVAGE_AUTO, false)

    fun setArchivageAutomatique(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_ARCHIVAGE_AUTO, active) }
    }

    /** Dernier jour (yyyy-MM-dd) où l'archivage automatique a tourné : une fois par jour au plus. */
    fun getDernierArchivageAutomatique(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DERNIER_ARCHIVAGE_AUTO, null)

    fun setDernierArchivageAutomatique(context: Context, jour: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_DERNIER_ARCHIVAGE_AUTO, jour) }
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

