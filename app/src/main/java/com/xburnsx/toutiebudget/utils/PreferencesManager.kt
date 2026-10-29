package com.xburnsx.toutiebudget.utils

import android.content.Context
import androidx.core.content.edit

/**
 * Gestionnaire centralisé des préférences UI et notifications
 */
object PreferencesManager {
    private const val PREFS_NAME = "toutie_prefs"
    private const val KEY_COPIE_FAITE = "copie_depuis_preferences_communes"

    /**
     * PR9 : préférences propres à chaque utilisateur de l'appareil. La première fois, on part des
     * préférences communes existantes pour ne rien perdre ; ensuite chaque compte a les siennes.
     * Sans session (écran de connexion), les préférences communes servent.
     */
    private fun prefs(context: Context): android.content.SharedPreferences {
        val uid = com.xburnsx.toutiebudget.di.PocketBaseClient.obtenirUtilisateurConnecte()?.id
            ?: return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val perso = context.getSharedPreferences("${PREFS_NAME}_$uid", Context.MODE_PRIVATE)
        if (!perso.getBoolean(KEY_COPIE_FAITE, false)) {
            val communes = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all
            perso.edit {
                communes.forEach { (cle, valeur) ->
                    @Suppress("UNCHECKED_CAST")
                    when (valeur) {
                        is Boolean -> putBoolean(cle, valeur)
                        is Int -> putInt(cle, valeur)
                        is Long -> putLong(cle, valeur)
                        is Float -> putFloat(cle, valeur)
                        is String -> putString(cle, valeur)
                        is Set<*> -> putStringSet(cle, valeur as Set<String>)
                    }
                }
                putBoolean(KEY_COPIE_FAITE, true)
            }
        }
        return perso
    }

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
        prefs(context).edit { putBoolean(KEY_MASQUER_COMPTES_ZERO, enabled) }
    }

    fun getMasquerComptesAZero(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MASQUER_COMPTES_ZERO, false)

    // CA3 : groupes de comptes repliés
    fun setGroupesComptesFermes(context: Context, groupes: Set<String>) {
        prefs(context).edit { putStringSet(KEY_GROUPES_COMPTES_FERMES, groupes) }
    }

    fun getGroupesComptesFermes(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_GROUPES_COMPTES_FERMES, null)?.toSet()
            ?: emptySet()

    // VA6 : confirmation facultative des gros virements (désactivée par défaut)
    fun setConfirmerGrosVirements(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_CONFIRMER_GROS_VIREMENTS, enabled) }
    }

    fun getConfirmerGrosVirements(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CONFIRMER_GROS_VIREMENTS, false)

    fun setSeuilGrosVirement(context: Context, dollars: Int) {
        prefs(context).edit { putInt(KEY_SEUIL_GROS_VIREMENT, dollars) }
    }

    fun getSeuilGrosVirement(context: Context): Int =
        prefs(context).getInt(KEY_SEUIL_GROS_VIREMENT, 500)

    // VA4 : couples source → destination récemment utilisés (« mode|source|destination »), le plus récent d'abord
    fun ajouterVirementRecent(context: Context, cle: String) {
        val prefs = prefs(context)
        val liste = (listOf(cle) + getVirementsRecents(context).filter { it != cle }).take(NB_VIREMENTS_RECENTS)
        prefs.edit { putString(KEY_VIREMENTS_RECENTS, liste.joinToString("\n")) }
    }

    fun getVirementsRecents(context: Context): List<String> =
        prefs(context)
            .getString(KEY_VIREMENTS_RECENTS, null)
            ?.split("\n")?.filter { it.isNotBlank() }
            ?: emptyList()

    fun setFigerPretAPlacer(context: Context, enabled: Boolean, depuisServeur: Boolean = false) {
        val prefs = prefs(context)
        prefs.edit { putBoolean(KEY_FIGER_PRET_A_PLACER, enabled) }
        // PR7 : un changement fait ici part vers PocketBase (ou attend le retour du réseau)
        if (!depuisServeur) PreferencesSync.marquerModifiee(context, PreferencesSync.FIGER)
    }

    fun getFigerPretAPlacer(context: Context): Boolean {
        val prefs = prefs(context)
        return prefs.getBoolean(KEY_FIGER_PRET_A_PLACER, false)
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean, depuisServeur: Boolean = false) {
        val prefs = prefs(context)
        prefs.edit { putBoolean(KEY_NOTIF_ENABLED, enabled) }
        // PR7 : un changement fait ici part vers PocketBase (ou attend le retour du réseau)
        if (!depuisServeur) PreferencesSync.marquerModifiee(context, PreferencesSync.NOTIFS)
    }

    fun getNotificationsEnabled(context: Context): Boolean {
        val prefs = prefs(context)
        return prefs.getBoolean(KEY_NOTIF_ENABLED, true)
    }

    fun setNotifObjJoursAvant(context: Context, jours: Int, depuisServeur: Boolean = false) {
        val prefs = prefs(context)
        prefs.edit { putInt(KEY_NOTIF_OBJ_JOURS_AVANT, jours) }
        // PR7 : un changement fait ici part vers PocketBase (ou attend le retour du réseau)
        if (!depuisServeur) PreferencesSync.marquerModifiee(context, PreferencesSync.JOURS_AVANT)
    }

    fun getNotifObjJoursAvant(context: Context): Int {
        val prefs = prefs(context)
        return prefs.getInt(KEY_NOTIF_OBJ_JOURS_AVANT, 3)
    }

    fun setNotifEnveloppeNegative(context: Context, enabled: Boolean, depuisServeur: Boolean = false) {
        val prefs = prefs(context)
        prefs.edit { putBoolean(KEY_NOTIF_ENV_NEGATIF, enabled) }
        // PR7 : un changement fait ici part vers PocketBase (ou attend le retour du réseau)
        if (!depuisServeur) PreferencesSync.marquerModifiee(context, PreferencesSync.ENV_NEGATIF)
    }

    fun getNotifEnveloppeNegative(context: Context): Boolean {
        val prefs = prefs(context)
        return prefs.getBoolean(KEY_NOTIF_ENV_NEGATIF, true)
    }

    /** DA3 : rappel d'action volontaire, désactivé par défaut. */
    fun getNotifEcheanceDettes(context: Context): Boolean =
        prefs(context)
            .getBoolean(KEY_NOTIF_ECHEANCE_DETTES, false)

    fun setNotifEcheanceDettes(context: Context, active: Boolean) {
        prefs(context)
            .edit { putBoolean(KEY_NOTIF_ECHEANCE_DETTES, active) }
    }

    /** ARA6 : archivage automatique des comptes soldés, désactivé par défaut. */
    fun getArchivageAutomatique(context: Context): Boolean =
        prefs(context)
            .getBoolean(KEY_ARCHIVAGE_AUTO, false)

    fun setArchivageAutomatique(context: Context, active: Boolean) {
        prefs(context)
            .edit { putBoolean(KEY_ARCHIVAGE_AUTO, active) }
    }

    /** Dernier jour (yyyy-MM-dd) où l'archivage automatique a tourné : une fois par jour au plus. */
    fun getDernierArchivageAutomatique(context: Context): String? =
        prefs(context)
            .getString(KEY_DERNIER_ARCHIVAGE_AUTO, null)

    fun setDernierArchivageAutomatique(context: Context, jour: String) {
        prefs(context)
            .edit { putString(KEY_DERNIER_ARCHIVAGE_AUTO, jour) }
    }

    fun setNotifPaiementJour(context: Context, jour: Int) {
        val prefs = prefs(context)
        prefs.edit { putInt(KEY_NOTIF_PAIEMENT_JOUR, jour) }
    }

    fun getNotifPaiementJour(context: Context): Int {
        val prefs = prefs(context)
        return prefs.getInt(KEY_NOTIF_PAIEMENT_JOUR, 1)
    }

    // Affichage des archivés
    fun setShowArchived(context: Context, enabled: Boolean) {
        val prefs = prefs(context)
        prefs.edit { putBoolean(KEY_SHOW_ARCHIVED, enabled) }
    }

    fun getShowArchived(context: Context): Boolean {
        val prefs = prefs(context)
        return prefs.getBoolean(KEY_SHOW_ARCHIVED, false)
    }

    // Archivage auto
    fun setArchiveAutoEnabled(context: Context, enabled: Boolean) {
        val prefs = prefs(context)
        prefs.edit { putBoolean(KEY_ARCHIVE_AUTO_ENABLED, enabled) }
    }

    fun getArchiveAutoEnabled(context: Context): Boolean {
        val prefs = prefs(context)
        return prefs.getBoolean(KEY_ARCHIVE_AUTO_ENABLED, false)
    }

    fun setArchiveMonths(context: Context, months: Int) {
        val prefs = prefs(context)
        prefs.edit { putInt(KEY_ARCHIVE_MONTHS, months) }
    }

    fun getArchiveMonths(context: Context): Int {
        val prefs = prefs(context)
        return prefs.getInt(KEY_ARCHIVE_MONTHS, 6)
    }

    // Animation de bienvenue (affichée une seule fois au premier lancement)
    fun setWelcomeShown(context: Context, shown: Boolean) {
        val prefs = prefs(context)
        prefs.edit { putBoolean(KEY_WELCOME_SHOWN, shown) }
    }

    fun getWelcomeShown(context: Context): Boolean {
        val prefs = prefs(context)
        return prefs.getBoolean(KEY_WELCOME_SHOWN, false)
    }

    // Nouvelle version de l'animation (emplacement/visuel revu)
    fun setWelcomeShownV2(context: Context, shown: Boolean) {
        val prefs = prefs(context)
        prefs.edit { putBoolean(KEY_WELCOME_SHOWN_V2, shown) }
    }

    fun getWelcomeShownV2(context: Context): Boolean {
        val prefs = prefs(context)
        return prefs.getBoolean(KEY_WELCOME_SHOWN_V2, false)
    }
}

// (Plus d'extensions top-level — tout est géré dans l'objet)

