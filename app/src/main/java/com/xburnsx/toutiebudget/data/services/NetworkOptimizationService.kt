package com.xburnsx.toutiebudget.data.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service d'optimisation réseau qui surveille la qualité de la connexion
 * et ajuste les paramètres de performance en conséquence
 */
@Singleton
class NetworkOptimizationService @Inject constructor(
    private val context: Context
) {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkQuality = MutableStateFlow(NetworkQuality.UNKNOWN)
    val networkQuality: StateFlow<NetworkQuality> = _networkQuality.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    init {
        startNetworkMonitoring()
    }

    /**
     * Démarre la surveillance du réseau
     */
    private fun startNetworkMonitoring() {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                serviceScope.launch {
                    _isConnected.value = true
                    updateNetworkQuality(network)
                }
            }

            override fun onLost(network: Network) {
                serviceScope.launch {
                    _isConnected.value = false
                    _networkQuality.value = NetworkQuality.UNKNOWN
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                serviceScope.launch {
                    updateNetworkQuality(network)
                }
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    /**
     * Met à jour la qualité du réseau basée sur les capacités
     */
    private fun updateNetworkQuality(network: Network) {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        val quality = when {
            capabilities == null -> NetworkQuality.UNKNOWN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                when {
                    capabilities.linkDownstreamBandwidthKbps >= 10000 -> NetworkQuality.EXCELLENT
                    capabilities.linkDownstreamBandwidthKbps >= 5000 -> NetworkQuality.GOOD
                    capabilities.linkDownstreamBandwidthKbps >= 1000 -> NetworkQuality.FAIR
                    else -> NetworkQuality.POOR
                }
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                when {
                    capabilities.linkDownstreamBandwidthKbps >= 5000 -> NetworkQuality.GOOD
                    capabilities.linkDownstreamBandwidthKbps >= 1000 -> NetworkQuality.FAIR
                    else -> NetworkQuality.POOR
                }
            }
            else -> NetworkQuality.UNKNOWN
        }

        _networkQuality.value = quality
        println("[NetworkOptimization] Qualité réseau: $quality")
    }

    /**
     * Obtient les paramètres de timeout optimisés selon la qualité du réseau
     */
    fun getOptimizedTimeouts(): NetworkTimeouts {
        return when (_networkQuality.value) {
            NetworkQuality.EXCELLENT -> NetworkTimeouts(
                connectTimeout = 2L,
                readTimeout = 5L,
                writeTimeout = 3L
            )
            NetworkQuality.GOOD -> NetworkTimeouts(
                connectTimeout = 3L,
                readTimeout = 8L,
                writeTimeout = 5L
            )
            NetworkQuality.FAIR -> NetworkTimeouts(
                connectTimeout = 5L,
                readTimeout = 12L,
                writeTimeout = 8L
            )
            NetworkQuality.POOR -> NetworkTimeouts(
                connectTimeout = 8L,
                readTimeout = 20L,
                writeTimeout = 12L
            )
            NetworkQuality.UNKNOWN -> NetworkTimeouts(
                connectTimeout = 5L,
                readTimeout = 10L,
                writeTimeout = 5L
            )
        }
    }

    /**
     * Vérifie si on peut utiliser des optimisations agressives
     */
    fun canUseAggressiveOptimizations(): Boolean {
        return _networkQuality.value in listOf(NetworkQuality.EXCELLENT, NetworkQuality.GOOD)
    }

    /**
     * Obtient la bande passante estimée en Kbps
     */
    fun getEstimatedBandwidth(): Int {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.linkDownstreamBandwidthKbps ?: 0
    }

    enum class NetworkQuality {
        EXCELLENT, GOOD, FAIR, POOR, UNKNOWN
    }

    data class NetworkTimeouts(
        val connectTimeout: Long,
        val readTimeout: Long,
        val writeTimeout: Long
    )
} 