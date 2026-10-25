package com.xburnsx.toutiebudget.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast

/**
 * Reçoit le résultat de l'installation lancée par [MiseAJourService.installer].
 */
class MiseAJourReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val statut = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Première mise à jour (ou Android < 12) : Android demande une confirmation
                val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmation?.let {
                    context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i("MiseAJour", "Mise à jour installée")
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.e("MiseAJour", "Échec de l'installation ($statut): $message")
                Toast.makeText(context, "Mise à jour échouée : ${message ?: statut}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
