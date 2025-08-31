package com.xburnsx.toutiebudget.data.services

import android.content.Context
// import android.util.Log
import com.xburnsx.toutiebudget.workers.SyncWorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service qui déclenche AUTOMATIQUEMENT la synchronisation dès qu'un SyncJob est créé
 * 🚀 SYNCJOBS EN TEMPS RÉEL - Plus d'attente !
 * 
 * UTILISATION : Appeler declencherSynchronisationArrierePlan() APRÈS chaque insertSyncJob()
 */
object SyncJobAutoTriggerService {
    
    private const val TAG = "SyncJobAutoTrigger"
    
    /**
     * Déclenche IMMÉDIATEMENT la synchronisation après création d'un SyncJob
     * Cette fonction doit être appelée APRÈS chaque insertSyncJob() dans TOUS les repositories
     */
    fun declencherSynchronisationImmediate(context: Context) {
        // 🚀 DÉCLENCHEMENT IMMÉDIAT de la synchronisation
        
        // Démarrer la synchronisation IMMÉDIATEMENT (avec contraintes réseau)
        SyncWorkManager.demarrerSynchronisation(context)
        
        // Planifier aussi la synchronisation automatique pour les futurs changements réseau
        SyncWorkManager.planifierSynchronisationAutomatique(context)
    }
    
    /**
     * Déclenche la synchronisation en arrière-plan (pour les cas où on n'a pas de Context)
     * Utilise le Context de l'application - FONCTION GLOBALE pour tous les repositories
     */
    fun declencherSynchronisationArrierePlan() {
        val context = getApplicationContext()
        if (context != null) {
            declencherSynchronisationImmediate(context)
        } else {
            // ⚠️ Impossible de déclencher la synchronisation - Context non disponible
        }
    }
    
    /**
     * Récupère le Context de l'application
     */
    private fun getApplicationContext(): Context? {
        return try {
            val application = com.xburnsx.toutiebudget.ToutieBudgetApplication.getInstance()
            application?.let { 
                // ✅ Context de l'application récupéré avec succès
                it
            } ?: run {
                // ⚠️ Application instance est null
                null
            }
        } catch (e: Exception) {
            // ❌ Erreur lors de la récupération du Context
            null
        }
    }
}
