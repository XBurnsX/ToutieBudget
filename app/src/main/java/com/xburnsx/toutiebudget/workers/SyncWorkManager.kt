package com.xburnsx.toutiebudget.workers

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Gestionnaire du WorkManager de synchronisation.
 *
 * Plus aucun envoi en cours n'est annulé (SJ2) : les déclenchements s'ajoutent à la suite
 * (APPEND_OR_REPLACE), et le verrou du SyncWorker garantit un seul envoi à la fois.
 */
object SyncWorkManager {

    private const val SYNC_WORK_NAME = "sync_work"          // déclenchements immédiats, à la suite
    private const val SYNC_RESEAU_NAME = "sync_reseau"      // au retour d'Internet
    private const val SYNC_REESSAI_NAME = "sync_reessai"    // nouvel essai après erreur réessayable
    private const val SYNC_WORK_TAG = "sync_tag"

    /**
     * Démarre la synchronisation immédiatement, à la suite de celle en cours s'il y en a une.
     */
    fun demarrerSynchronisation(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .addTag(SYNC_WORK_TAG)
            .setInputData(workDataOf("triggered_by" to "immediate"))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest
        )
    }

    /**
     * Planifie la synchronisation automatique quand internet revient.
     * Le worker se déclenche dès que la connectivité est rétablie, même app fermée.
     * KEEP : une demande déjà en attente suffit, elle n'est pas remplacée.
     */
    fun planifierSynchronisationAutomatique(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_RESEAU_NAME,
            ExistingWorkPolicy.KEEP,
            creerWorkRequestReseau()
        )
    }

    /**
     * Nouvel essai après des erreurs réessayables (réseau, 408, 429, 5xx), avec un délai
     * qui grandit selon le nombre d'essais : 1, 2, 4, 8... minutes, au plus 1 heure (SJ10).
     */
    fun planifierNouvelEssai(context: Context, nombreEssais: Int) {
        val delaiMinutes = (1L shl (nombreEssais - 1).coerceIn(0, 6)).coerceAtMost(60L)
        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setInitialDelay(delaiMinutes, TimeUnit.MINUTES)
            .addTag(SYNC_WORK_TAG)
            .setInputData(workDataOf("triggered_by" to "reessai"))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_REESSAI_NAME,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Arrête la synchronisation
     */
    fun arreterSynchronisation(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(SYNC_WORK_TAG)
    }

    /**
     * Déclenche la synchronisation après une modification
     */
    fun declencherSynchronisationAutomatique(context: Context) {
        demarrerSynchronisation(context)
        planifierSynchronisationAutomatique(context)
    }

    /**
     * Crée une requête de travail avec contraintes réseau.
     * Le worker se déclenche AUTOMATIQUEMENT dès qu'internet revient, même app fermée.
     */
    private fun creerWorkRequestReseau(): OneTimeWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false)
            .build()

        return OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .addTag(SYNC_WORK_TAG)
            .setInputData(workDataOf("triggered_by" to "network_restored"))
            .build()
    }

    /**
     * Vérifie si une synchronisation est en cours
     */
    /** SJ20 : état du worker observé (Flow), sans .get() bloquant ni sondage. */
    fun etatSynchronisation(context: Context): kotlinx.coroutines.flow.Flow<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosByTagFlow(SYNC_WORK_TAG)

    fun estSynchronisationEnCours(context: Context): Boolean {
        val workInfo = WorkManager.getInstance(context)
            .getWorkInfosByTag(SYNC_WORK_TAG)
            .get()

        return workInfo.any { it.state == WorkInfo.State.RUNNING }
    }

    /**
     * Récupère le statut de la dernière synchronisation
     */
    fun getStatutDerniereSynchronisation(context: Context): WorkInfo.State? {
        val workInfo = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(SYNC_WORK_NAME)
            .get()

        return workInfo.maxByOrNull { it.id }?.state
    }
}
