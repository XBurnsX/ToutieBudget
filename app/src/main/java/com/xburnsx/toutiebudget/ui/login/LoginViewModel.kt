// chemin/simule: /ui/login/LoginViewModel.kt
package com.xburnsx.toutiebudget.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val loginSuccess: Boolean = false,
    val error: String? = null
)

class LoginViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * Appelé lorsque Google a renvoyé un code d'autorisation.
     * Cette fonction appelle la seule méthode de connexion de notre client : loginWithGoogle.
     * @param authCode Le code à envoyer à PocketBase.
     */
    fun handleGoogleLogin(authCode: String?) {
        if (authCode == null) {
            _uiState.update { it.copy(error = "L'authentification Google a échoué.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // On appelle la fonction qui existe dans ton client personnalisé.
            val result = PocketBaseClient.loginWithGoogle(authCode)

            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
