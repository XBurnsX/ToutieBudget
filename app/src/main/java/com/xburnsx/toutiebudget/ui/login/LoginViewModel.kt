// chemin/simule: app/src/main/java/com/xburnsx/toutiebudget/ui/login/LoginViewModel.kt
// Dépendances: PocketBaseClient, ViewModel, Flow, Coroutines

package com.xburnsx.toutiebudget.ui.login

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
    val messageChargement: String = ""
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
                    messageChargement = "Initialisation de la connexion..."
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
     * Traite la connexion Google OAuth2
     * @param codeAutorisation Le code d'autorisation obtenu de Google Sign-In
     */
    fun gererConnexionGoogle(codeAutorisation: String?) {
        if (codeAutorisation == null) {
            _etatUi.update {
                it.copy(
                    erreur = "L'authentification Google a été annulée ou a échoué",
                    estEnChargement = false
                )
            }
            return
        }

        viewModelScope.launch {
            _etatUi.update {
                it.copy(
                    estEnChargement = true,
                    erreur = null,
                    messageChargement = "Connexion PocketBase en cours..."
                )
            }

            println("🔐 === VRAIE CONNEXION POCKETBASE ===")
            println("📤 Code Google reçu: ${codeAutorisation.take(20)}...")

            // VRAIE CONNEXION - PAS DE BYPASS
            val resultat = PocketBaseClient.connecterAvecGoogle(codeAutorisation)

            resultat.onSuccess {
                println("✅ Connexion PocketBase réussie !")
                _etatUi.update {
                    it.copy(
                        estEnChargement = false,
                        connexionReussie = true,
                        messageChargement = "Connexion PocketBase réussie !"
                    )
                }

            }.onFailure { erreur ->
                println("❌ Erreur PocketBase : ${erreur.message}")
                _etatUi.update {
                    it.copy(
                        estEnChargement = false,
                        erreur = "Erreur PocketBase: ${erreur.message}",
                        messageChargement = ""
                    )
                }
            }

            println("🔐 === FIN CONNEXION POCKETBASE ===")
        }
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
    fun verifierConnexionExistante() {
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
     * Force une déconnexion complète
     */
    fun deconnecter() {
        PocketBaseClient.deconnecter()
        _etatUi.update {
            EtatLoginUi() // Réinitialiser complètement l'état
        }
    }
}