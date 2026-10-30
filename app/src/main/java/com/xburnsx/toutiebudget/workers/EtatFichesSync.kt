package com.xburnsx.toutiebudget.workers

import android.content.Context
import androidx.core.content.edit
import com.xburnsx.toutiebudget.di.PocketBaseClient

/**
 * Mémoire locale de la synchronisation, par utilisateur :
 * - SJ8 : pierres tombales des fiches supprimées ici (60 jours) : ni la synchro temps réel, ni l'import
 *   initial, ni un ancien envoi ne peuvent les faire réapparaître pendant la convergence.
 * - SJ6 : dernière version serveur (« updated ») connue de chaque fiche envoyée, pour reconnaître une
 *   modification faite ailleurs entre-temps, et les conflits que l'utilisateur a décidé d'écraser.
 */
object EtatFichesSync {
    private const val DUREE_TOMBE_MS = 60L * 24 * 60 * 60 * 1000

    private fun prefs(context: Context, nom: String) = context.applicationContext.getSharedPreferences(
        "${nom}_" + (PocketBaseClient.obtenirUtilisateurConnecte()?.id ?: "local"), Context.MODE_PRIVATE
    )

    // ----- SJ8 : pierres tombales -----
    fun noterSuppression(context: Context, collection: String, id: String) {
        if (id.isBlank()) return
        val p = prefs(context, "sync_tombes")
        val maintenant = System.currentTimeMillis()
        p.edit {
            p.all.forEach { (cle, v) -> if ((v as? Long)?.let { maintenant - it > DUREE_TOMBE_MS } == true) remove(cle) }
            putLong("$collection/$id", maintenant)
        }
    }

    fun estSupprimee(context: Context, collection: String, id: String): Boolean {
        val quand = prefs(context, "sync_tombes").getLong("$collection/$id", 0L)
        return quand > 0L && System.currentTimeMillis() - quand <= DUREE_TOMBE_MS
    }

    // ----- SJ6 : versions connues et conflits acceptés -----
    fun noterVersion(context: Context, collection: String, id: String, updated: String?) {
        if (id.isBlank() || updated.isNullOrBlank()) return
        prefs(context, "sync_versions").edit { putString("$collection/$id", updated) }
    }

    fun versionConnue(context: Context, collection: String, id: String): String? =
        prefs(context, "sync_versions").getString("$collection/$id", null)

    /** L'utilisateur a choisi « Utiliser ma version » pour cette tâche en conflit. */
    fun forcerTache(context: Context, jobId: String) {
        prefs(context, "sync_forcees").edit { putBoolean(jobId, true) }
    }

    fun estForcee(context: Context, jobId: String): Boolean = prefs(context, "sync_forcees").getBoolean(jobId, false)

    fun oublierTache(context: Context, jobId: String) {
        prefs(context, "sync_forcees").edit { remove(jobId) }
    }

    /** Date PocketBase « 2026-10-28 20:00:00.123Z » en millisecondes (UTC). */
    fun millisPocketBase(valeur: String?): Long? = valeur?.trim()?.takeIf { it.length >= 19 }?.let {
        runCatching {
            java.time.LocalDateTime.parse(it.take(19).replace(' ', 'T')).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()
    }
}
