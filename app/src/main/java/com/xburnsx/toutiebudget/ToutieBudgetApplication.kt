package com.xburnsx.toutiebudget

import android.app.Application
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.workers.SyncWorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Classe Application personnalisée pour ToutieBudget
 * Responsable de l'initialisation de la base de données Room
 */
class ToutieBudgetApplication : Application() {
    
    companion object {
        private var instance: ToutieBudgetApplication? = null
        
        fun getInstance(): ToutieBudgetApplication? {
            return instance
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialiser la base de données Room
        AppModule.initializeDatabase(this)
        
        // DÉMARRER LA SYNCHRONISATION AUTOMATIQUE QUAND INTERNET REVIENT
        // Le worker se déclenchera automatiquement dès que la connectivité est rétablie
        SyncWorkManager.planifierSynchronisationAutomatique(this)
        
        // 🆕 VÉRIFIER ET TRAITER LES SYNCJOB EXISTANTS À L'OUVERTURE
        // Si il y a des tâches en attente, on les traite immédiatement
        verifierEtTraiterSyncJobExistants()
    }
    
    /**
     * Vérifie s'il y a des SyncJob en attente et les traite immédiatement
     */
    private fun verifierEtTraiterSyncJobExistants() {
        // Utiliser une coroutine pour vérifier les SyncJob en arrière-plan
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val syncJobDao = AppModule.provideSyncJobDao(this@ToutieBudgetApplication)
                val syncJobsEnAttente = syncJobDao.getPendingSyncJobs()
                
                if (syncJobsEnAttente.isNotEmpty()) {
                    android.util.Log.d("ToutieBudgetApp", "🚀 ${syncJobsEnAttente.size} SyncJob en attente détectés à l'ouverture")
                    
                    // DÉCLENCHER IMMÉDIATEMENT LA SYNCHRONISATION
                    // Le worker se déclenchera et traitera tous les SyncJob en attente
                    SyncWorkManager.declencherSynchronisationAutomatique(this@ToutieBudgetApplication)
                } else {
                    android.util.Log.d("ToutieBudgetApp", "✅ Aucun SyncJob en attente à l'ouverture")
                }
            } catch (e: Exception) {
                android.util.Log.e("ToutieBudgetApp", "❌ Erreur lors de la vérification des SyncJob", e)
            }
        }
    }
}
