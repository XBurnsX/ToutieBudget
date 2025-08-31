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
 * 🆕 MODE HORS LIGNE : Surveillance automatique de la connectivité réseau
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
        
        // 🆕 NOUVEAU : Démarrer la surveillance de la connectivité réseau
        // Cela permettra la synchronisation automatique quand internet revient
        demarrerSurveillanceReseau()
        
        // DÉMARRER LA SYNCHRONISATION AUTOMATIQUE QUAND INTERNET REVIENT
        // Le worker se déclenchera automatiquement dès que la connectivité est rétablie
        SyncWorkManager.planifierSynchronisationAutomatique(this)
        
        // 🆕 VÉRIFIER ET TRAITER LES SYNCJOB EXISTANTS À L'OUVERTURE
        // Si il y a des tâches en attente, on les traite immédiatement
        verifierEtTraiterSyncJobExistants()
    }
    
    /**
     * 🆕 NOUVEAU : Démarre la surveillance de la connectivité réseau
     * Permet la synchronisation automatique dès qu'internet revient
     */
    private fun demarrerSurveillanceReseau() {
        try {
            val networkService = AppModule.provideNetworkConnectivityService(this)
            networkService.startNetworkMonitoring()
            // ✅ Surveillance réseau démarrée
        } catch (e: Exception) {
            // ❌ Erreur lors du démarrage de la surveillance réseau
        }
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
                    // 🚀 ${syncJobsEnAttente.size} SyncJob en attente détectés à l'ouverture
                    
                    // DÉCLENCHER IMMÉDIATEMENT LA SYNCHRONISATION
                    // Le worker se déclenchera et traitera tous les SyncJob en attente
                    SyncWorkManager.declencherSynchronisationAutomatique(this@ToutieBudgetApplication)
                } else {
                    // ✅ Aucun SyncJob en attente à l'ouverture
                }
            } catch (e: Exception) {
                // ❌ Erreur lors de la vérification des SyncJob
            }
        }
    }
}
