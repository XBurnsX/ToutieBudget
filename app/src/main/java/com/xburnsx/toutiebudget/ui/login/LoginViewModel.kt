// chemin/simule: app/src/main/java/com/xburnsx/toutiebudget/ui/login/LoginViewModel.kt
// Dépendances: PocketBaseClient, ViewModel, Flow, Coroutines

package com.xburnsx.toutiebudget.ui.login

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.utils.DetecteurEmulateur
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
            Log.d("LoginViewModel", "🔐 Début de la gestion de connexion Google")
            Log.d("LoginViewModel", "🔑 Code autorisation: ${if (codeAutorisation.isNullOrBlank()) "VIDE" else "PRÉSENT"}")
            Log.d("LoginViewModel", "🎫 ID Token: ${if (idToken.isNullOrBlank()) "VIDE" else "PRÉSENT"}")
            
            _etatUi.update {
                it.copy(
                    estEnChargement = true,
                    erreur = null,
                    messageChargement = "Connexion en cours..."
                )
            }

            // Vérification des données reçues
            if (codeAutorisation.isNullOrBlank() && idToken.isNullOrBlank()) {
                Log.e("LoginViewModel", "❌ Aucune information d'authentification reçue!")
                val estEmulateur = DetecteurEmulateur.estEmulateur()
                val messageErreur = if (estEmulateur) {
                    "Aucune information d'authentification reçue de Google sur l'émulateur.\n\n" +
                    "Cela est probablement dû à une configuration SHA-1 manquante dans Google Cloud Console.\n\n" +
                    "Solution : Ajoutez le SHA-1 de votre clé de debug dans Google Cloud Console > APIs & Services > Credentials."
                } else {
                    "Aucune information d'authentification reçue de Google. Vérifiez votre connexion internet et réessayez."
                }
                _etatUi.update {
                    it.copy(
                        estEnChargement = false,
                        erreur = messageErreur,
                        messageChargement = ""
                    )
                }
                return@launch
            }

            // SI on a un code d'autorisation, essayer PocketBase
            if (!codeAutorisation.isNullOrBlank()) {
                Log.d("LoginViewModel", "✅ Code d'autorisation présent, connexion à PocketBase...")
                _etatUi.update {
                    it.copy(messageChargement = "Connexion à PocketBase...")
                }

                val resultat = PocketBaseClient.connecterAvecGoogle(codeAutorisation, context)

                resultat.onSuccess {
                    Log.d("LoginViewModel", "✅ Connexion réussie!")
                    _etatUi.update {
                        it.copy(
                            estEnChargement = false,
                            connexionReussie = true,
                            messageChargement = "Connexion réussie !"
                        )
                    }

                    // 🚀 Démarrer le service temps réel après connexion réussie
                    AppModule.provideRealtimeSyncService().startAfterLogin()

                    return@launch
                }.onFailure { erreur ->
                    Log.e("LoginViewModel", "❌ Erreur de connexion: ${erreur.message}", erreur)
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
                        erreur = "Google n'a pas fourni le code d'autorisation requis. Recommencez la connexion Google et vérifiez la configuration OAuth.",
                        messageChargement = ""
                    )
                }
            } else {
                _etatUi.update {
                    it.copy(
                        estEnChargement = false,
                        connexionReussie = false,
                        erreur = "Connexion impossible : aucun code d'autorisation Google vérifiable.",
                        messageChargement = ""
                    )
                }
            }
        }
    }

    /**
     * Gère les erreurs spécifiques de Google Sign-In
     * @param codeErreur Code d'erreur de l'API Google Sign-In
     */
    fun gererErreurGoogleSignIn(codeErreur: Int, context: Context) {
        viewModelScope.launch {
            val estEmulateur = DetecteurEmulateur.estEmulateur()
            val messageErreur = when (codeErreur) {
                10 -> {
                    // DEVELOPER_ERROR - SHA-1 manquant ou incorrect
                    "❌ Erreur de configuration Google Sign-In (Code 10)\n\n" +
                    "Le SHA-1 de votre clé de signature n'est pas configuré dans Google Cloud Console.\n\n" +
                    "📋 Solution :\n" +
                    "1. Exécutez le script 'obtenir-sha1-debug.bat' dans le dossier du projet\n" +
                    "2. Copiez le SHA-1 affiché\n" +
                    "3. Allez sur https://console.cloud.google.com/\n" +
                    "4. Projet : toutiebudget-kotlin\n" +
                    "5. APIs & Services > Credentials\n" +
                    "6. Cliquez sur votre OAuth 2.0 Client ID Android\n" +
                    "7. Ajoutez le SHA-1 dans 'SHA-1 certificate fingerprints'\n" +
                    "8. Sauvegardez et attendez 2-3 minutes\n" +
                    "9. Relancez l'application\n\n" +
                    "💡 Pour release : utilisez 'obtenir-sha1-release.bat'"
                }
                12501 -> "Connexion annulée par l'utilisateur."
                7 -> "Erreur réseau. Vérifiez votre connexion internet."
                8 -> "Erreur interne. Réessayez plus tard."
                else -> {
                    "Erreur Google Sign-In (code $codeErreur).\n\n" +
                    "Si le problème persiste, vérifiez la configuration OAuth dans Google Cloud Console."
                }
            }
            
            Log.e("LoginViewModel", "❌ Erreur Google Sign-In code $codeErreur: $messageErreur")
            
            _etatUi.update {
                it.copy(
                    estEnChargement = false,
                    erreur = messageErreur,
                    messageChargement = ""
                )
            }
        }
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

            // 🚀 Démarrer le service temps réel après reconnexion automatique
            AppModule.provideRealtimeSyncService().startAfterLogin()
        }
    }

}