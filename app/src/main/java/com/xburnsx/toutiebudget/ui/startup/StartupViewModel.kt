package com.xburnsx.toutiebudget.ui.startup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log

/**
 * États possibles lors du démarrage de l'application
 */
sealed class StartupState {
    object Loading : StartupState()
    object ServerError : StartupState()
    object OfflineMode : StartupState() // 🆕 NOUVEAU : Mode hors ligne
    object UserNotAuthenticated : StartupState()
    object UserAuthenticated : StartupState()
}

/**
 * ViewModel pour gérer la logique de démarrage de l'application
 * Vérifie l'état du serveur et l'authentification de l'utilisateur
 * 🆕 SUPPORT HORS LIGNE : Permet l'ouverture même sans connexion serveur
 */
class StartupViewModel : ViewModel() {

    private val _state = MutableStateFlow<StartupState>(StartupState.Loading)
    val state: StateFlow<StartupState> = _state.asStateFlow()

    /**
     * Lance la vérification des conditions de démarrage avec le contexte Android
     * 🆕 MODE HORS LIGNE : L'app s'ouvre même sans connexion serveur
     */
    fun checkStartupConditions(context: Context) {
        viewModelScope.launch {
            try {
                Log.d("StartupViewModel", "🚀 Vérification des conditions de démarrage...")
                
                // 1. Initialiser la base de données Room en premier (toujours disponible)
                AppModule.initializeDatabase(context)
                Log.d("StartupViewModel", "✅ Base de données Room initialisée")
                
                // 2. Charger l'authentification sauvegardée (toujours disponible)
                val isUserAuthenticated = PocketBaseClient.chargerAuthentificationSauvegardee(context)
                Log.d("StartupViewModel", "🔐 Authentification chargée: $isUserAuthenticated")
                
                // 3. Essayer d'initialiser PocketBase et vérifier la connexion serveur
                var serverHealthy = false
                try {
                    val initSuccess = PocketBaseClient.initialiser()
                    if (initSuccess) {
                        serverHealthy = PocketBaseClient.verifierConnexionServeur()
                    }
                    Log.d("StartupViewModel", "🌐 Serveur accessible: $serverHealthy")
                } catch (e: Exception) {
                    Log.w("StartupViewModel", "⚠️ Erreur de connexion serveur: ${e.message}")
                    serverHealthy = false
                }
                
                // 4. Logique de décision avec support hors ligne
                when {
                    // 🎯 CAS 1 : Serveur accessible + utilisateur connecté
                    serverHealthy && isUserAuthenticated -> {
                        Log.d("StartupViewModel", "✅ Mode connecté - utilisateur authentifié")
                        planifierWorkerRappels(context)
                        _state.value = StartupState.UserAuthenticated
                    }
                    
                    // 🎯 CAS 2 : Serveur accessible + utilisateur non connecté
                    serverHealthy && !isUserAuthenticated -> {
                        Log.d("StartupViewModel", "🔐 Mode connecté - utilisateur non authentifié")
                        _state.value = StartupState.UserNotAuthenticated
                    }
                    
                    // 🎯 CAS 3 : Serveur inaccessible + utilisateur connecté = MODE HORS LIGNE
                    !serverHealthy && isUserAuthenticated -> {
                        Log.d("StartupViewModel", "📱 MODE HORS LIGNE - utilisateur authentifié localement")
                        
                        // Planifier la synchronisation automatique quand internet revient
                        planifierSynchronisationAutomatique(context)
                        
                        // Permettre l'ouverture de l'app en mode hors ligne
                        _state.value = StartupState.OfflineMode
                    }
                    
                    // 🎯 CAS 4 : Serveur inaccessible + utilisateur non connecté
                    !serverHealthy && !isUserAuthenticated -> {
                        Log.w("StartupViewModel", "❌ Impossible d'ouvrir l'app - pas de serveur ni d'auth locale")
                        _state.value = StartupState.ServerError
                    }
                }
                
            } catch (e: Exception) {
                Log.e("StartupViewModel", "❌ Erreur lors de la vérification des conditions", e)
                
                // 🆕 GESTION D'ERREUR AMÉLIORÉE : Essayer le mode hors ligne
                try {
                    val isUserAuthenticated = PocketBaseClient.chargerAuthentificationSauvegardee(context)
                    if (isUserAuthenticated) {
                        Log.d("StartupViewModel", "🔄 Basculement en mode hors ligne après erreur")
                        _state.value = StartupState.OfflineMode
                    } else {
                        _state.value = StartupState.ServerError
                    }
                } catch (_: Exception) {
                    _state.value = StartupState.ServerError
                }
            }
        }
    }

    /**
     * Relance la vérification (en cas d'erreur serveur par exemple)
     */
    fun retry(context: Context) {
        _state.value = StartupState.Loading
        checkStartupConditions(context)
    }
    
    /**
     * 🆕 NOUVEAU : Force l'ouverture en mode hors ligne
     * Permet à l'utilisateur d'utiliser l'app même sans connexion
     */
    fun forcerModeHorsLigne(context: Context) {
        viewModelScope.launch {
            try {
                Log.d("StartupViewModel", "📱 Forçage du mode hors ligne...")
                
                // Initialiser la base de données Room
                AppModule.initializeDatabase(context)
                
                // Charger l'authentification locale
                val isUserAuthenticated = PocketBaseClient.chargerAuthentificationSauvegardee(context)
                
                if (isUserAuthenticated) {
                    Log.d("StartupViewModel", "✅ Mode hors ligne activé avec succès")
                    
                    // Planifier la synchronisation automatique
                    planifierSynchronisationAutomatique(context)
                    
                    _state.value = StartupState.OfflineMode
                } else {
                    Log.w("StartupViewModel", "❌ Impossible d'activer le mode hors ligne - pas d'auth locale")
                    _state.value = StartupState.ServerError
                }
                
            } catch (e: Exception) {
                Log.e("StartupViewModel", "❌ Erreur lors de l'activation du mode hors ligne", e)
                _state.value = StartupState.ServerError
            }
        }
    }
    
    /**
     * Planifie le worker quotidien de rappels
     */
    private fun planifierWorkerRappels(context: Context) {
        try {
            val work = androidx.work.PeriodicWorkRequestBuilder<com.xburnsx.toutiebudget.utils.notifications.RappelsWorker>(
                24, java.util.concurrent.TimeUnit.HOURS
            ).build()
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "rappelsBudget", androidx.work.ExistingPeriodicWorkPolicy.UPDATE, work
            )
            Log.d("StartupViewModel", "✅ Worker de rappels planifié")
        } catch (e: Exception) {
            Log.w("StartupViewModel", "⚠️ Erreur lors de la planification du worker de rappels", e)
        }
    }
    
    /**
     * 🆕 NOUVEAU : Planifie la synchronisation automatique
     * Se déclenche dès que la connectivité revient
     */
    private fun planifierSynchronisationAutomatique(context: Context) {
        try {
            // Utiliser le SyncWorkManager existant pour planifier la synchronisation
            val syncWorkManager = AppModule.provideSyncWorkManager(context)
            syncWorkManager.planifierSynchronisationAutomatique(context)
            Log.d("StartupViewModel", "✅ Synchronisation automatique planifiée")
        } catch (e: Exception) {
            Log.w("StartupViewModel", "⚠️ Erreur lors de la planification de la synchronisation", e)
        }
    }
}
