package com.xburnsx.toutiebudget.utils.notifications

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xburnsx.toutiebudget.R
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.utils.PreferencesManager
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import com.xburnsx.toutiebudget.utils.MoneyFormatter

class RappelsWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!PreferencesManager.getNotificationsEnabled(ctx)) return Result.success()

        val budgetVm = AppModule.provideBudgetViewModel()
        // S’assurer que les données sont fraîches
        budgetVm.rafraichirDonnees()

        val state = budgetVm.uiState.value

        // 1) Enveloppes négatives (exclure la catégorie "Dettes")
        state.categoriesEnveloppes.flatMap { cat ->
            if (cat.nomCategorie.equals("Dettes", ignoreCase = true)) emptyList() else cat.enveloppes
        }.filter { it.solde < 0 }.forEach { env ->
            notify("Enveloppe négative", "${env.nom} est en négatif: ${env.solde}")
        }

        // 2) Objectifs à X jours
        val joursAvant = PreferencesManager.getNotifObjJoursAvant(ctx)
        val calToday = Calendar.getInstance()
        val calLimit = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, joursAvant) }
        state.categoriesEnveloppes.flatMap { it.enveloppes }.forEach { env ->
            val dateStr = env.dateObjectif ?: return@forEach
            try {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val target = when {
                    // Cas objectifs mensuels: on a parfois uniquement le jour (ex: "10")
                    dateStr.matches(Regex("\\d{1,2}")) -> {
                        val day = dateStr.toInt().coerceAtLeast(1)
                        Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                            val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
                            set(Calendar.DAY_OF_MONTH, day.coerceAtMost(maxDay))
                        }
                    }
                    // Cas yyyy-MM-dd
                    dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> {
                        val parts = dateStr.split("-")
                        Calendar.getInstance().apply {
                            set(Calendar.YEAR, parts[0].toInt())
                            set(Calendar.MONTH, parts[1].toInt() - 1)
                            set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }
                    }
                    else -> null
                }
                if (target != null && target.timeInMillis in calToday.timeInMillis..calLimit.timeInMillis && env.alloueCumulatif < env.objectif) {
                    val montantManquant = (env.objectif - env.alloueCumulatif).coerceAtLeast(0.0)
                    val montantTxt = MoneyFormatter.formatAmount(montantManquant)
                    val dateTxt = SimpleDateFormat("d MMM", Locale.getDefault()).format(target.time)
                    val titre = "Verifiez ${env.nom} d'ici le ${dateTxt} - ${montantTxt} necessaire"
                    val descriptif = "Objectif: ${MoneyFormatter.formatAmount(env.objectif)} • Alloué: ${MoneyFormatter.formatAmount(env.alloueCumulatif)} • Reste: ${montantTxt}"
                    notify(titre, descriptif)
                }
            } catch (_: Exception) { /* ignore */ }
        }

        // Retiré: rappel paiement dettes/cartes par jour du mois

        // Archivage automatique supprimé (non pertinent pour l'app budget)

        return Result.success()
    }

    private fun notify(title: String, text: String) {
        val channelId = "toutie_rappels"
        ensureChannel(channelId)
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify((Math.random() * 100000).toInt(), notification)
    }

    private fun ensureChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(channelId)
            if (existing == null) {
                val channel = NotificationChannel(channelId, "Rappels Budget", NotificationManager.IMPORTANCE_DEFAULT)
                channel.description = "Rappels d'objectifs et notifications budget"
                nm.createNotificationChannel(channel)
            }
        }
    }
}

