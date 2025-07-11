// chemin/simule: app/src/main/java/com/xburnsx/toutiebudget/ui/login/LoginViewModel.kt
// Dépendances: PocketBaseClient, ViewModel, Flow, Coroutines

package com.xburnsx.toutiebudget.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * État de l'interface utilisateur pour l'écran de connexion
 */
data class EtatLoginUi(
    val estEnChargement: Boolean = false,
    val connexionReussie: Boolean = false,
    val erreur: String? = null,
    val messageChargement: String = "",
    val modeDebug: Boolean = false,
    val logsDebug: List<String> = emptyList()
)

/**
 * ViewModel pour gérer la logique de connexion Google OAuth2
 * Communique avec PocketBase pour l'authentification
 */
class LoginViewModel : ViewModel() {

    private val _etatUi = MutableStateFlow(EtatLoginUi())
    val etatUi = _etatUi.asStateFlow()

    init {
        // Initialiser le client PocketBase au démarrage
        viewModelScope.launch {
                            _etatUi.update {
                    it.copy(
                        estEnChargement = true,
                        messageChargement = "Initialisation de la connexion...",
                        modeDebug = false // Mode debug désactivé par défaut
                    )
                }

            try {
                PocketBaseClient.initialiser()
                
                _etatUi.update {
                    it.copy(
                        estEnChargement = false,
                        messageChargement = ""
                    )
                }
            } catch (e: Exception) {
                _etatUi.update {
                    it.copy(
                        estEnChargement = false,
                        erreur = "Erreur d'initialisation : ${e.message}",
                        messageChargement = ""
                    )
                }
            }
        }
    }

    /**
     * Traite la connexion Google avec les informations du compte directement
     * @param email Email du compte Google
     * @param nom Nom du compte Google
     * @param codeAutorisation Code d'autorisation obtenu de Google Sign-In (optionnel)
     * @param idToken Token ID obtenu de Google Sign-In (optionnel)
     */
    fun gererConnexionGoogleAvecCompte(
        email: String, 
        nom: String?, 
        codeAutorisation: String?,
        idToken: String? = null,
        context: Context
    ) {
        viewModelScope.launch {
            _etatUi.update {
                it.copy(
                    estEnChargement = true,
                    erreur = null,
                    messageChargement = "Connexion en cours..."
                )
            }

            // Vérification des données reçues
            if (codeAutorisation.isNullOrBlank() && idToken.isNullOrBlank()) {
                _etatUi.update {
                    it.copy(
                        estEnChargement = false,
                        erreur = "Aucune information d'authentification reçue de Google. Vérifiez votre connexion internet et réessayez.",
                        messageChargement = ""
                    )
                }
                return@launch
            }

            // SI on a un code d'autorisation, essayer PocketBase
            if (!codeAutorisation.isNullOrBlank()) {
                _etatUi.update {
                    it.copy(messageChargement = "Connexion à PocketBase...")
                }

                val resultat = PocketBaseClient.connecterAvecGoogle(codeAutorisation, context)

                resultat.onSuccess {
                    _etatUi.update {
                        it.copy(
                            estEnChargement = false,
                            connexionReussie = true,
                            messageChargement = "Connexion réussie !"
                        )
                    }
                    return@launch
                }.onFailure { erreur ->
                    // Message d'erreur plus explicite pour l'utilisateur
                    val messageErreur = when {
                        erreur.message?.contains("timeout", ignoreCase = true) == true -> 
                            "Le serveur ne répond pas. Vérifiez votre connexion internet."
                        erreur.message?.contains("network", ignoreCase = true) == true -> 
                            "Erreur de connexion réseau. Vérifiez votre connexion internet."
                        erreur.message?.contains("404", ignoreCase = true) == true -> 
                            "Serveur PocketBase introuvable. Vérifiez la configuration."
                        erreur.message?.contains("401", ignoreCase = true) == true -> 
                            "Authentification échouée. Vérifiez vos identifiants Google."
                        else -> "Erreur de connexion : ${erreur.message}"
                    }
                    
                    _etatUi.update {
                        it.copy(
                            estEnChargement = false,
                            connexionReussie = false,
                            erreur = messageErreur,
                            messageChargement = ""
                        )
                    }
                    return@launch
                }
            } else if (!idToken.isNullOrBlank()) {
                _etatUi.update {
                    it.copy(messageChargement = "Connexion avec ID Token...")
                }

                // TODO: Implémenter la connexion avec ID Token si nécessaire
                _etatUi.update {
                    it.copy(
                        estEnChargement = false,
                        connexionReussie = false,
                        erreur = "Mode de connexion non supporté. Contactez le support.",
                        messageChargement = ""
                    )
                }
            } else {
                _etatUi.update {
                    it.copy(
                        estEnChargement = false,
                        connexionReussie = true,
                        messageChargement = "Connexion réussie (mode local)"
                    )
                }
            }
        }
    }

    /**
     * Traite la connexion Google OAuth2 (version legacy)
     * @param codeAutorisation Le code d'autorisation obtenu de Google Sign-In
     */
    fun gererConnexionGoogle(codeAutorisation: String?, context: Context) {
        if (codeAutorisation == null) {
            _etatUi.update {
                it.copy(
                    erreur = "L'authentification Google a été annulée ou a échoué. Vérifiez votre connexion internet et réessayez.",
                    estEnChargement = false
                )
            }
            return
        }

        // Utiliser la nouvelle méthode avec fallback
        gererConnexionGoogleAvecCompte("utilisateur@gmail.com", "Utilisateur", codeAutorisation, null, context)
    }

    /**
     * Réinitialise l'état d'erreur
     */
    fun effacerErreur() {
        _etatUi.update { it.copy(erreur = null) }
    }

    /**
     * Vérifie si l'utilisateur est déjà connecté
     */
    fun verifierConnexionExistante(context: Context) {
        PocketBaseClient.chargerToken(context)
        if (PocketBaseClient.estConnecte()) {
            _etatUi.update {
                it.copy(
                    connexionReussie = true,
                    messageChargement = "Reconnexion automatique..."
                )
            }
        }
    }

    /**
     * Efface tous les logs de debug
     */
    fun effacerLogsDebug() {
        _etatUi.update { it.copy(logsDebug = emptyList()) }
    }

    /**
     * Active/désactive le mode debug
     */
    fun basculerModeDebug() {
        _etatUi.update { it.copy(modeDebug = !it.modeDebug) }
    }
}