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

        // PR11/PR13 : données lues dans les dépôts pour le mois RÉEL d'aujourd'hui,
        // jamais l'état du Budget (qui peut afficher un autre mois ou être en chargement).
        val zone = java.time.ZoneId.systemDefault()
        val aujourdHui = java.time.LocalDate.now(zone)
        val calToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val joursAvant = PreferencesManager.getNotifObjJoursAvant(ctx)
        val calLimit = (calToday.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, joursAvant.coerceAtLeast(0))
        }
        val enveloppeRepository = AppModule.provideEnveloppeRepository()
        val donnees = runCatching {
            val enveloppes = enveloppeRepository.recupererToutesLesEnveloppes().getOrThrow()
            val allocationsDuMois = enveloppeRepository.recupererAllocationsPourMois(
                java.util.Date.from(aujourdHui.withDayOfMonth(1).atStartOfDay(zone).toInstant())
            ).getOrThrow()
            val categoriesDettes = AppModule.provideCategorieRepository().recupererToutesLesCategories().getOrThrow()
                .filter { it.nom.equals("Dettes", ignoreCase = true) }.map { it.id }.toSet()
            Triple(enveloppes, allocationsDuMois, categoriesDettes)
        }.getOrElse { return Result.retry() }
        val (enveloppes, allocationsDuMois, categoriesDettes) = donnees

        // 1) Enveloppes négatives (exclure la catégorie "Dettes") — PR10 : seulement si l'option est active
        if (PreferencesManager.getNotifEnveloppeNegative(ctx)) {
            CalculRappels.deficits(enveloppes, allocationsDuMois, categoriesDettes).forEach { (env, solde) ->
                notify("deficit:${env.id}:${aujourdHui.year}-${aujourdHui.monthValue}", "Enveloppe négative",
                    "${env.nom} est en négatif : ${MoneyFormatter.formatAmount(solde)}")
            }
        }

        // 2) « Paiement à X planifié pour demain » selon l'objectif de l'enveloppe
        val paiementsDemain = CalculRappels.paiementsDemain(enveloppes, allocationsDuMois, aujourdHui, zone)
        paiementsDemain.forEach { p ->
            val montantTxt = MoneyFormatter.formatAmount(p.montant)
            notify(
                "paiement:${p.enveloppe.id}:${p.date}",
                "Paiement à ${p.enveloppe.nom} planifié pour demain",
                if (p.manque <= 0.0) "$montantTxt prévu demain. L'enveloppe est prête " +
                    "(${MoneyFormatter.formatAmount(p.disponible)} disponible)."
                else "$montantTxt prévu demain. Il manque ${MoneyFormatter.formatAmount(p.manque)} dans l'enveloppe " +
                    "(${MoneyFormatter.formatAmount(p.disponible)} disponible)."
            )
        }
        val dejaRappelees = paiementsDemain.map { it.enveloppe.id }.toSet()

        // 3) Objectifs dont l'échéance tombe dans les X prochains jours, avec le vrai montant restant
        enveloppes.filter { !it.estArchive && it.typeObjectif != com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Aucun }
            .filter { it.id !in dejaRappelees } // déjà avertie par le rappel « demain »
            .forEach { env ->
                val allocations = enveloppeRepository.recupererAllocationsEnveloppe(env.id).getOrNull() ?: return@forEach
                // 4) Paiement oublié : échéance passée (1 à 3 jours) sans aucun paiement fait dans l'enveloppe
                CalculRappels.paiementOublie(env, allocations, aujourdHui, zone = zone)?.let { o ->
                    val quand = if (o.date == aujourdHui.minusDays(1)) "hier"
                        else "le " + o.date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.CANADA_FRENCH))
                    notify(
                        "oublie:${env.id}:${o.date}",
                        "Paiement oublié ? ${env.nom}",
                        "Un paiement de ${MoneyFormatter.formatAmount(o.montant)} pour ${env.nom} était dû $quand, " +
                            "et aucun paiement n'a été fait dans l'enveloppe."
                    )
                }
                val rappel = CalculRappels.rappelObjectif(env, allocations, aujourdHui, joursAvant, zone) ?: return@forEach
                val montantTxt = MoneyFormatter.formatAmount(rappel.reste)
                val dateTxt = rappel.echeance.format(
                    java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.CANADA_FRENCH)
                )
                notify(
                    "objectif:${env.id}:${rappel.echeance}",
                    "${env.nom} : $montantTxt à mettre d'ici le $dateTxt",
                    "Objectif : ${MoneyFormatter.formatAmount(env.objectifMontant)} • " +
                        "Déjà mis : ${MoneyFormatter.formatAmount(rappel.progres)} • Reste : $montantTxt"
                )
            }

        // DA3 : rappel volontaire d'une action à faire, jamais d'un simple
        // changement de progression. Seules les dettes ayant une échéance
        // contractuelle ET un solde restant non payé sont concernées.
        if (PreferencesManager.getNotifEcheanceDettes(ctx)) {
            val comptes = AppModule.provideCompteRepository()
                .recupererTousLesComptes().getOrThrow()
                .filterIsInstance<com.xburnsx.toutiebudget.data.modeles.CompteDette>()
                .filter { !it.estArchive && it.soldeDette < -0.005 }
            val transactions = AppModule.provideTransactionRepository()
            comptes.forEach { dette ->
                val mouvements = transactions.recupererTransactionsPourCompte(
                    dette.id, "comptes_dettes"
                ).getOrThrow()
                val echeance = com.xburnsx.toutiebudget.domain.services.MoteurDette
                    .etatProchaineEcheance(dette, mouvements)
                    ?: return@forEach
                val dateJour = Calendar.getInstance().apply {
                    time = echeance.date
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (dateJour.timeInMillis in calToday.timeInMillis..calLimit.timeInMillis &&
                    echeance.reste > 0.005) {
                    val dateTxt = SimpleDateFormat("d MMM", Locale.CANADA_FRENCH).format(dateJour.time)
                    notify(
                        "dette:${dette.id}:${dateJour.get(Calendar.YEAR)}:${dateJour.get(Calendar.DAY_OF_YEAR)}",
                        "Paiement à faire : ${dette.nom}",
                        "${MoneyFormatter.formatAmount(echeance.reste)} attendu d'ici le ${dateTxt}. " +
                            "À confirmer avec le prêteur."
                    )
                }
            }
        }

        // Archivage automatique supprimé (non pertinent pour l'app budget)

        return Result.success()
    }

    private fun notify(cle: String, title: String, text: String) {
        val channelId = "toutie_rappels"
        ensureChannel(channelId)
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            // Icône de gauche : silhouette du chat Toutie (Android n'y affiche qu'une couleur),
            // teintée du rose ToutieBudget ; le vrai logo en couleur est la grande icône.
            .setSmallIcon(R.drawable.ic_notification_toutie)
            .setColor(0xFFD906AE.toInt())
            .setLargeIcon(logo())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(ouvrirApp())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // PR12 : une même alerte met à jour sa notification au lieu de la multiplier,
        // et un texte identique déjà montré dans les dernières 24 h n'est pas relancé.
        if (!RappelsDejaEnvoyes.aEnvoyer(applicationContext, cle, title + "|" + text)) return
        val identifiant = cle.hashCode() and Int.MAX_VALUE
        try {
            NotificationManagerCompat.from(applicationContext).notify(identifiant, notification)
        } catch (_: SecurityException) {
            // PR15 : permission refusée ou révoquée pendant l'exécution.
        }
    }

    /** Logo ToutieBudget en couleur (chat Toutie) pour la grande icône de la notification. */
    private fun logo(): android.graphics.Bitmap? =
        android.graphics.BitmapFactory.decodeResource(applicationContext.resources, R.mipmap.ic_launcher_foreground)

    /** Toucher la notification ouvre ToutieBudget. */
    private fun ouvrirApp(): android.app.PendingIntent? {
        val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: return null
        return android.app.PendingIntent.getActivity(
            applicationContext, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
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

