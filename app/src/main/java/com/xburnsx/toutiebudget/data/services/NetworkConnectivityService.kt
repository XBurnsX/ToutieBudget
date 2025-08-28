package com.xburnsx.toutiebudget.data.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.xburnsx.toutiebudget.workers.SyncWorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service de gestion de la connectivité réseau
 * Détecte automatiquement quand internet revient et déclenche la synchronisation
 * 🆕 MODE HORS LIGNE : Synchronisation automatique dès que la connectivité est rétablie
 */
class NetworkConnectivityService(private val context: Context) {
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private var isNetworkCallbackRegistered = false
    private var wasOffline = false
    
    companion object {
        private const val TAG = "NetworkConnectivity"
    }
    
    /**
     * Démarre la surveillance de la connectivité réseau
     */
    fun startNetworkMonitoring() {
        if (isNetworkCallbackRegistered) {
            Log.d(TAG, "⚠️ Surveillance réseau déjà active")
            return
        }
        
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build()
            
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            isNetworkCallbackRegistered = true
            
            // Vérifier l'état initial
            val isCurrentlyOnline = isNetworkAvailable()
            wasOffline = !isCurrentlyOnline
            
            Log.d(TAG, "✅ Surveillance réseau démarrée - État actuel: ${if (isCurrentlyOnline) "En ligne" else "Hors ligne"}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors du démarrage de la surveillance réseau", e)
        }
    }
    
    /**
     * Arrête la surveillance de la connectivité réseau
     */
    fun stopNetworkMonitoring() {
        if (isNetworkCallbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
                isNetworkCallbackRegistered = false
                Log.d(TAG, "✅ Surveillance réseau arrêtée")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur lors de l'arrêt de la surveillance réseau", e)
            }
        }
    }
    
    /**
     * Callback de changement de connectivité réseau
     */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        
        override fun onAvailable(network: Network) {
            Log.d(TAG, "🌐 Réseau disponible: $network")
            
            if (wasOffline) {
                Log.i(TAG, "🚀 INTERNET REVIENT - Déclenchement de la synchronisation automatique")
                wasOffline = false
                
                // Déclencher la synchronisation en arrière-plan
                scope.launch {
                    try {
                        // Délai pour s'assurer que la connexion est stable
                        kotlinx.coroutines.delay(2000)
                        
                        // Vérifier que la connexion est toujours active
                        if (isNetworkAvailable()) {
                            Log.d(TAG, "✅ Connexion stable confirmée - Lancement de la synchronisation")
                            
                            // Démarrer la synchronisation immédiatement
                            SyncWorkManager.demarrerSynchronisation(context)
                            
                            // Planifier aussi la synchronisation automatique pour les futurs changements
                            SyncWorkManager.planifierSynchronisationAutomatique(context)
                            
                        } else {
                            Log.w(TAG, "⚠️ Connexion instable - Synchronisation reportée")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erreur lors du déclenchement de la synchronisation", e)
                    }
                }
            }
        }
        
        override fun onLost(network: Network) {
            Log.d(TAG, "❌ Réseau perdu: $network")
            wasOffline = true
        }
        
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val hasValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            
            Log.d(TAG, "🔄 Capacités réseau changées - Internet: $hasInternet, Validé: $hasValidated")
        }
    }
    
    /**
     * Vérifie si le réseau est actuellement disponible
     */
    fun isNetworkAvailable(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors de la vérification de la connectivité", e)
            false
        }
    }
    
    /**
     * Vérifie si l'application était en mode hors ligne
     */
    fun wasInOfflineMode(): Boolean = wasOffline
    
    /**
     * Force la vérification de la connectivité et déclenche la synchronisation si nécessaire
     */
    fun checkConnectivityAndSync() {
        scope.launch {
            try {
                if (isNetworkAvailable() && wasOffline) {
                    Log.i(TAG, "🔍 Vérification manuelle - Internet disponible, déclenchement de la synchronisation")
                    wasOffline = false
                    
                    // Délai pour s'assurer que la connexion est stable
                    kotlinx.coroutines.delay(1000)
                    
                    if (isNetworkAvailable()) {
                        SyncWorkManager.demarrerSynchronisation(context)
                        SyncWorkManager.planifierSynchronisationAutomatique(context)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur lors de la vérification manuelle", e)
            }
        }
    }
}
