package com.xburnsx.toutiebudget.ui.startup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * États possibles lors du démarrage de l'application
 */
sealed class StartupState {
    object Loading : StartupState()
    object ServerError : StartupState()
    object UserNotAuthenticated : StartupState()
    object UserAuthenticated : StartupState()
}

/**
 * ViewModel pour gérer la logique de démarrage de l'application
 * Vérifie l'état du serveur et l'authentification de l'utilisateur
 */
class StartupViewModel : ViewModel() {

    private val _state = MutableStateFlow<StartupState>(StartupState.Loading)
    val state: StateFlow<StartupState> = _state.asStateFlow()

    /**
     * Lance la vérification des conditions de démarrage avec le contexte Android
     */
    fun checkStartupConditions(context: Context) {
        viewModelScope.launch {
            try {
                // 1. Initialiser PocketBase et vérifier la connexion serveur
                PocketBaseClient.initialiser()

                // 2. Vérifier si le serveur est accessible
                val serverHealthy = PocketBaseClient.verifierConnexionServeur()
                if (!serverHealthy) {
                    _state.value = StartupState.ServerError
                    return@launch
                }

                // 3. Charger l'authentification sauvegardée et vérifier si l'utilisateur est connecté
                val isUserAuthenticated = PocketBaseClient.chargerAuthentificationSauvegardee(context)

                if (isUserAuthenticated) {
                    // Planifier le worker quotidien de rappels
                    try {
                        val work = androidx.work.PeriodicWorkRequestBuilder<com.xburnsx.toutiebudget.utils.notifications.RappelsWorker>(
                            24, java.util.concurrent.TimeUnit.HOURS
                        ).build()
                        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                            "rappelsBudget", androidx.work.ExistingPeriodicWorkPolicy.UPDATE, work
                        )
                    } catch (_: Exception) {}
                    _state.value = StartupState.UserAuthenticated
                } else {
                    _state.value = StartupState.UserNotAuthenticated
                }

            } catch (_: Exception) {
                _state.value = StartupState.ServerError
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
}
