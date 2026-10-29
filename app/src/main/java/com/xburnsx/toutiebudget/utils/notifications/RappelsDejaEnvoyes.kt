package com.xburnsx.toutiebudget.utils.notifications

import android.content.Context
import androidx.core.content.edit

/**
 * PR12 : mémoire des rappels déjà montrés. Le même rappel (même clé, même texte) n'est pas relancé
 * avant 24 h ; si le montant ou la date change, le texte change et le rappel est mis à jour.
 * La notification de test n'est jamais filtrée.
 */
object RappelsDejaEnvoyes {
    private const val PREFS = "rappels_envoyes"
    private const val DELAI_MS = 24 * 60 * 60 * 1000L

    fun aEnvoyer(context: Context, cle: String, texte: String, maintenant: Long = System.currentTimeMillis()): Boolean {
        if (cle.startsWith("test:")) return true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val signature = texte.hashCode().toString()
        val precedent = prefs.getString(cle, null)?.split('|', limit = 2)
        val dejaVu = precedent != null && precedent.size == 2 && precedent[1] == signature &&
            (precedent[0].toLongOrNull()?.let { maintenant - it < DELAI_MS } ?: false)
        if (dejaVu) return false
        prefs.edit {
            // Ménage : on ne garde que les entrées de moins de 60 jours
            prefs.all.forEach { (k, v) ->
                val t = (v as? String)?.substringBefore('|')?.toLongOrNull()
                if (t != null && maintenant - t > 60 * DELAI_MS) remove(k)
            }
            putString(cle, "$maintenant|$signature")
        }
        return true
    }

    /** Déconnexion / effacement : oublier les rappels de l'ancienne session. */
    fun effacer(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() }
    }
}
