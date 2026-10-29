package com.xburnsx.toutiebudget.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.edit

/** PA1/PA5 : état local/en ligne et date du dernier échange RÉUSSI avec PocketBase. */
object DiagnosticDonnees {
    private const val PREFS = "diagnostic_donnees"
    private const val KEY_DERNIER_ECHANGE = "dernier_echange_reussi"

    fun noterEchangeReussi(context: Context, quand: Long = System.currentTimeMillis()) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putLong(KEY_DERNIER_ECHANGE, quand) }
    }

    fun dernierEchange(context: Context): Long? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_DERNIER_ECHANGE, 0L).takeIf { it > 0L }

    fun estEnLigne(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun formater(millis: Long?): String = millis?.let {
        java.text.SimpleDateFormat("d MMM yyyy à HH:mm", java.util.Locale.CANADA_FRENCH).format(java.util.Date(it))
    } ?: "jamais sur cet appareil"
}
