package com.xburnsx.toutiebudget.ui.server

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.data.services.ServerStatusService
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ServerStatusUiState(
    val isChecking: Boolean = true,
    val isServerAvailable: Boolean = false,
    val errorMessage: String = "",
    val showDialog: Boolean = false,
    val shouldNavigateToLogin: Boolean = false
)

class ServerStatusViewModel(
    private val serverStatusService: ServerStatusService,
    @SuppressLint("StaticFieldLeak") private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerStatusUiState())
    val uiState: StateFlow<ServerStatusUiState> = _uiState.asStateFlow()

    init {
        verifierServeur()
    }

    /**
     * Vérifie l'état du serveur PocketBase
     */
    fun verifierServeur() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true, showDialog = false)

            val (isAvailable, errorMessage) = serverStatusService.verifierConnectiviteDetaillee()

            if (!isAvailable) {
                // Déconnecter l'utilisateur si le serveur n'est pas disponible
                deconnecterUtilisateur()
            }

            _uiState.value = _uiState.value.copy(
                isChecking = false,
                isServerAvailable = isAvailable,
                errorMessage = errorMessage,
                showDialog = !isAvailable,
                shouldNavigateToLogin = isAvailable // Ajout de l'état de navigation
            )
        }
    }

    // La logique de vérification détaillée est implémentée dans le service

    /**
     * Déconnecte l'utilisateur en cas de problème serveur
     */
    private fun deconnecterUtilisateur() {
        try {
            PocketBaseClient.deconnecter(context)
        } catch (_: Exception) {
            // Log silencieux
        }
    }
}
