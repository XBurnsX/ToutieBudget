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
            ajouterLogDebug("🚀 Initialisation du LoginViewModel...")
            
            _etatUi.update {
                it.copy(
                    estEnChargement = true,
                    messageChargement = "Initialisation de la connexion...",
                    modeDebug = true // Mode debug activé par défaut
                )
            }

            try {
                ajouterLogDebug("📡 Initialisation du client PocketBase...")
                PocketBaseClient.initialiser()
                
                ajouterLogDebug("✅ Client PocketBase initialisé avec succès")
                _etatUi.update {
                    it.copy(
                        estEnChargement = false,
                        messageChargement = ""
                    )
                }
            } catch (e: Exception) {
                ajouterLogDebug("❌ Erreur d'initialisation : ${e.message}")
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
     * Ajoute un log au mode debug
     */
    private fun ajouterLogDebug(message: String) {
        println("🔍 [DEBUG] $message")
        _etatUi.update { etat ->
            etat.copy(
                logsDebug = etat.logsDebug + "${System.currentTimeMillis()}: $message"
            )
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
        idToken: String? = null
    ) {
        viewModelScope.launch {
            ajouterLogDebug("🔐 === DÉBUT CONNEXION GOOGLE AVEC COMPTE ===")
            ajouterLogDebug("📧 Email: $email")
            ajouterLogDebug("👤 Nom: $nom")
            ajouterLogDebug("🔑 Code autorisation: ${codeAutorisation?.take(20) ?: "Non disponible"}")
            ajouterLogDebug("🎫 ID Token: ${idToken?.take(20) ?: "Non disponible"}")

            _etatUi.update {
                it.copy(
                    estEnChargement = true,
                    erreur = null,
                    messageChargement = "Connexion en cours..."
                )
            }

            // Vérification des données reçues
            if (codeAutorisation.isNullOrBlank() && idToken.isNullOrBlank()) {
                ajouterLogDebug("❌ Aucun code d'autorisation ni ID token reçu")
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
                ajouterLogDebug("🔄 Tentative PocketBase avec code d'autorisation...")
                _etatUi.update {
                    it.copy(messageChargement = "Connexion à PocketBase...")
                }

                val resultat = PocketBaseClient.connecterAvecGoogle(codeAutorisation)

                resultat.onSuccess {
                    ajouterLogDebug("✅ Connexion PocketBase réussie !")
                    _etatUi.update {
                        it.copy(
                            estEnChargement = false,
                            connexionReussie = true,
                            messageChargement = "Connexion réussie !"
                        )
                    }
                    return@launch
                }.onFailure { erreur ->
                    ajouterLogDebug("❌ Erreur PocketBase : ${erreur.message}")
                    
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
                ajouterLogDebug("🔄 Tentative PocketBase avec ID Token...")
                _etatUi.update {
                    it.copy(messageChargement = "Connexion avec ID Token...")
                }

                // TODO: Implémenter la connexion avec ID Token si nécessaire
                ajouterLogDebug("⚠️ Connexion avec ID Token non implémentée")
                _etatUi.update {
                    it.copy(
                        estEnChargement = false,
                        connexionReussie = false,
                        erreur = "Mode de connexion non supporté. Contactez le support.",
                        messageChargement = ""
                    )
                }
            } else {
                ajouterLogDebug("✅ Pas de code serveur. Connexion locale acceptée pour $email")
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
    fun gererConnexionGoogle(codeAutorisation: String?) {
        ajouterLogDebug("🔐 === CONNEXION GOOGLE (LEGACY) ===")
        
        if (codeAutorisation == null) {
            ajouterLogDebug("❌ Aucun code d'autorisation reçu")
            _etatUi.update {
                it.copy(
                    erreur = "L'authentification Google a été annulée ou a échoué. Vérifiez votre connexion internet et réessayez.",
                    estEnChargement = false
                )
            }
            return
        }

        // Utiliser la nouvelle méthode avec fallback
        gererConnexionGoogleAvecCompte("utilisateur@gmail.com", "Utilisateur", codeAutorisation)
    }

    /**
     * Réinitialise l'état d'erreur
     */
    fun effacerErreur() {
        ajouterLogDebug("🧹 Effacement de l'erreur")
        _etatUi.update { it.copy(erreur = null) }
    }

    /**
     * Vérifie si l'utilisateur est déjà connecté
     */
    fun verifierConnexionExistante() {
        ajouterLogDebug("🔍 Vérification de connexion existante...")
        if (PocketBaseClient.estConnecte()) {
            ajouterLogDebug("✅ Utilisateur déjà connecté")
            _etatUi.update {
                it.copy(
                    connexionReussie = true,
                    messageChargement = "Reconnexion automatique..."
                )
            }
        } else {
            ajouterLogDebug("❌ Aucune connexion existante")
        }
    }

    /**
     * Force une déconnexion complète
     */
    fun deconnecter() {
        ajouterLogDebug("👋 Déconnexion de l'utilisateur")
        PocketBaseClient.deconnecter()
        _etatUi.update {
            EtatLoginUi(modeDebug = true) // Réinitialiser complètement l'état
        }
    }

    /**
     * Bascule le mode debug
     */
    fun basculerModeDebug() {
        _etatUi.update { etat ->
            etat.copy(modeDebug = !etat.modeDebug)
        }
        ajouterLogDebug("🔧 Mode debug ${if (_etatUi.value.modeDebug) "activé" else "désactivé"}")
    }

    /**
     * Efface les logs de debug
     */
    fun effacerLogsDebug() {
        _etatUi.update { etat ->
            etat.copy(logsDebug = emptyList())
        }
        ajouterLogDebug("🧹 Logs de debug effacés")
    }

    /**
     * Lance le diagnostic complet de PocketBase
     */
    fun lancerDiagnosticPocketBase() {
        viewModelScope.launch {
            ajouterLogDebug("🔍 Lancement du diagnostic PocketBase...")
            
            try {
                val rapport = com.xburnsx.toutiebudget.utils.TestPocketBase.diagnosticComplet()
                ajouterLogDebug("📋 Diagnostic complet:")
                rapport.split("\n").forEach { ligne ->
                    if (ligne.isNotBlank()) {
                        ajouterLogDebug(ligne)
                    }
                }
            } catch (e: Exception) {
                ajouterLogDebug("❌ Erreur lors du diagnostic: ${e.message}")
            }
        }
    }
}