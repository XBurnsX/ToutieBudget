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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class RappelsWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!PreferencesManager.getNotificationsEnabled(ctx)) return Result.success()
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return Result.success()
        // PR14 : le test est un vrai message, même avec zéro enveloppe ou objectif.
        if (inputData.getBoolean("notification_test", false)) {
            notify("test:notification", "ToutieBudget — notification de test",
                "Les notifications fonctionnent sur cet appareil.")
            return Result.success()
        }

        val budgetVm = AppModule.provideBudgetViewModel()
        // PR11 : rafraichirDonnees() déclenche une coroutine et retourne AVANT
        // le résultat. Attendre un nouvel état final au lieu d'utiliser les
        // données vides/périmées du StateFlow.
        val etatAvant = budgetVm.uiState.value
        budgetVm.rafraichirDonnees()
        val state = withTimeoutOrNull(20_000L) {
            budgetVm.uiState.first { !it.isLoading && it !== etatAvant }
        } ?: return Result.retry()
        if (state.erreur != null) return Result.retry()

        // 1) Enveloppes négatives (exclure la catégorie "Dettes")
        if (PreferencesManager.getNotifEnveloppeNegative(ctx)) {
            state.categoriesEnveloppes.flatMap { cat ->
                if (cat.nomCategorie.equals("Dettes", ignoreCase = true)) emptyList() else cat.enveloppes
            }.filter { kotlin.math.round(it.solde * 100.0).toLong() < 0L }.forEach { env ->
                notify("deficit:${env.id}", "Enveloppe négative",
                    "${env.nom} est en négatif : ${MoneyFormatter.formatAmount(env.solde)}")
            }
        }

        // 2) Objectifs à X jours
        val joursAvant = PreferencesManager.getNotifObjJoursAvant(ctx)
        val calToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val calLimit = (calToday.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, joursAvant.coerceAtLeast(0))
        }
        state.categoriesEnveloppes.flatMap { it.enveloppes }.forEach { env ->
            val dateStr = env.dateObjectif ?: return@forEach
            try {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val target = when {
                    // Cas objectifs mensuels: on a parfois uniquement le jour (ex: "10")
                    dateStr.matches(Regex("\\d{1,2}")) -> {
                        val day = dateStr.toInt()
                        if (day !in 1..31) throw IllegalArgumentException("Jour d'objectif invalide")
                        (calToday.clone() as Calendar).apply {
                            set(Calendar.DAY_OF_MONTH, day.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
                            if (before(calToday)) {
                                add(Calendar.MONTH, 1)
                                set(Calendar.DAY_OF_MONTH, day.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
                            }
                        }
                    }
                    // Cas yyyy-MM-dd
                    dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> {
                        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
                        fmt.parse(dateStr)?.let { date ->
                            Calendar.getInstance().apply {
                                time = date
                                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                            }
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
                    notify("objectif:${env.id}:${target.get(Calendar.YEAR)}:${target.get(Calendar.DAY_OF_YEAR)}", titre, descriptif)
                }
            } catch (_: Exception) { /* ignore */ }
        }

        // Retiré: rappel paiement dettes/cartes par jour du mois

        // Archivage automatique supprimé (non pertinent pour l'app budget)

        return Result.success()
    }

    private fun notify(cle: String, title: String, text: String) {
        val channelId = "toutie_rappels"
        ensureChannel(channelId)
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // PR12 : une même alerte met à jour sa notification au lieu de la multiplier.
        val identifiant = cle.hashCode() and Int.MAX_VALUE
        try {
            NotificationManagerCompat.from(applicationContext).notify(identifiant, notification)
        } catch (_: SecurityException) {
            // PR15 : permission refusée ou révoquée pendant l'exécution.
        }
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

