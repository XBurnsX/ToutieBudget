package com.xburnsx.toutiebudget.workers

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Gestionnaire du WorkManager de synchronisation
 * Configure et planifie l'exécution du SyncWorker
 */
object SyncWorkManager {
    
    private const val SYNC_WORK_NAME = "sync_work"
    private const val SYNC_WORK_TAG = "sync_tag"
    
    /**
     * Démarre la synchronisation immédiatement
     */
    fun demarrerSynchronisation(context: Context) {
        val workRequest = creerWorkRequest()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
    
    /**
     * Planifie la synchronisation automatique quand internet revient
     * Le worker se déclenche automatiquement dès que la connectivité est rétablie
     * SUIVANT LE MD : Worker INTELLIGENT avec contraintes réseau
     */
    fun planifierSynchronisationAutomatique(context: Context) {
        // ✅ SUIVRE LE MD : Worker avec contraintes réseau qui se déclenche quand internet revient
        val workRequest = creerWorkRequestReseau()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
    
    /**
     * Arrête la synchronisation
     */
    fun arreterSynchronisation(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
    }
    
    /**
     * Déclenche la synchronisation automatique après une modification
     * Le worker se déclenchera automatiquement quand internet revient
     */
    fun declencherSynchronisationAutomatique(context: Context) {
        planifierSynchronisationAutomatique(context)
    }
    
    /**
     * Crée une requête de travail unique (synchronisation immédiate)
     */
    private fun creerWorkRequest(): OneTimeWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Seulement avec internet
            .setRequiresBatteryNotLow(false) // Pas besoin de batterie pleine
            .build()
        
        return OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .addTag(SYNC_WORK_TAG)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.MINUTES
            )
            .setInputData(workDataOf("triggered_by" to "manual"))
            .build()
    }
    
    /**
     * Crée une requête de travail avec contraintes réseau (SUIVANT LE MD)
     * Le worker se déclenche AUTOMATIQUEMENT dès qu'internet revient
     * Même si l'appli est fermée !
     */
    private fun creerWorkRequestReseau(): OneTimeWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Se déclenche dès qu'internet revient
            .setRequiresBatteryNotLow(false) // Pas besoin de batterie pleine
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
    fun estSynchronisationEnCours(context: Context): Boolean {
        val workInfo = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(SYNC_WORK_NAME)
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
