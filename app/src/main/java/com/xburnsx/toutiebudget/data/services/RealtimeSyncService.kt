package com.xburnsx.toutiebudget.data.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.di.UrlResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service de synchronisation temps réel avec PocketBase.
 * Écoute les changements via WebSocket et notifie les ViewModels.
 * INVISIBLE à l'utilisateur - fonctionne en arrière-plan.
 */
@Singleton
class RealtimeSyncService @Inject constructor() {

    private val client = PocketBaseClient
    private val httpClient = OkHttpClient()
    private val gson = Gson()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var isConnected = false

    // Events pour notifier les ViewModels
    private val _budgetUpdated = MutableSharedFlow<Unit>()
    val budgetUpdated: SharedFlow<Unit> = _budgetUpdated.asSharedFlow()

    private val _comptesUpdated = MutableSharedFlow<Unit>()
    val comptesUpdated: SharedFlow<Unit> = _comptesUpdated.asSharedFlow()

    private val _categoriesUpdated = MutableSharedFlow<Unit>()
    val categoriesUpdated: SharedFlow<Unit> = _categoriesUpdated.asSharedFlow()

    private val _transactionsUpdated = MutableSharedFlow<Unit>()
    val transactionsUpdated: SharedFlow<Unit> = _transactionsUpdated.asSharedFlow()

    /**
     * Démarre la synchronisation temps réel.
     * Appelé automatiquement au démarrage de l'app.
     */
    fun startRealtimeSync() {
        if (isConnected) {
            return
        }

        serviceScope.launch {
            try {
                connectWebSocket()
            } catch (e: Exception) {
                println("[REALTIME] ❌ Erreur connexion: ${e.message}")
                // Retry après 5 secondes
                kotlinx.coroutines.delay(5000)
                startRealtimeSync()
            }
        }
    }

    /**
     * Arrête la synchronisation temps réel.
     */
    fun stopRealtimeSync() {
        webSocket?.close(1000, "App fermée")
        webSocket = null
        isConnected = false
    }

    fun triggerBudgetUpdate() {
        serviceScope.launch {
            _budgetUpdated.emit(Unit)
        }
    }

    /**
     * Déclenche une mise à jour du budget (méthode publique).
     * Utilisée par les ViewModels pour notifier les changements.
     */
    fun declencherMiseAJourBudget() {
        serviceScope.launch {
            _budgetUpdated.emit(Unit)
        }
    }

    /**
     * Déclenche une mise à jour des comptes (méthode publique).
     * Utilisée par les repositories pour notifier les changements.
     */
    fun declencherMiseAJourComptes() {
        serviceScope.launch {
            _comptesUpdated.emit(Unit)
        }
    }

    private suspend fun connectWebSocket() {
        if (!client.estConnecte()) {
            kotlinx.coroutines.delay(3000)
            startRealtimeSync() // Retry
            return
        }

        val token = client.obtenirToken()
        if (token == null) {
            return
        }

        startServerSentEvents(token)
    }

    /**
     * Démarre une connexion Server-Sent Events (SSE) pour le VRAI temps réel.
     * C'est l'API officielle de PocketBase pour le temps réel.
     */
    private suspend fun startServerSentEvents(token: String) {
        serviceScope.launch {
            try {
                val urlBase = UrlResolver.obtenirUrlActive()

                // Collections à écouter
                val collections = listOf(
                    "allocations_mensuelles",
                    "comptes_cheque",
                    "comptes_dette",
                    "enveloppes",
                    "categories",
                    "transactions"
                )

                // Construire l'URL SSE avec les collections en paramètres
                val collectionsParam = collections.joinToString(",")
                val sseUrl = "$urlBase/api/realtime?subscribe=$collectionsParam"

                val request = Request.Builder()
                    .url(sseUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "text/event-stream")
                    .addHeader("Cache-Control", "no-cache")
                    .build()

                val call = httpClient.newCall(request)
                val response = call.execute()

                if (response.isSuccessful) {
                    isConnected = true
                    println("[REALTIME] ✅ Connexion établie")

                    // Lire le stream en temps réel
                    response.body?.source()?.let { source ->
                        while (isConnected && !source.exhausted()) {
                            try {
                                val line = source.readUtf8Line()
                                if (line != null && line.startsWith("data: ")) {
                                    val data = line.substring(6) // Enlever "data: "
                                    if (data.isNotEmpty() && data != "{\"clientId\":") {
                                        handleRealtimeEvent(data)
                                    }
                                }
                            } catch (e: Exception) {
                                break
                            }
                        }
                    }
                } else {
                    println("[REALTIME] ❌ Erreur connexion: ${response.code}")
                    // Retry après 5 secondes
                    kotlinx.coroutines.delay(5000)
                    startRealtimeSync()
                }

            } catch (e: Exception) {
                isConnected = false
                // Retry après 5 secondes
                kotlinx.coroutines.delay(5000)
                startRealtimeSync()
            }
        }
    }

    /**
     * Traite les événements temps réel reçus de PocketBase.
     */
    private suspend fun handleRealtimeEvent(data: String) {
        try {
            val jsonEvent = gson.fromJson(data, JsonObject::class.java)
            val action = jsonEvent.get("action")?.asString
            val record = jsonEvent.get("record")?.asJsonObject
            val collection = record?.get("collectionName")?.asString

            // Notifier les ViewModels selon la collection modifiée
            when (collection) {
                "allocations_mensuelles" -> {
                    println("[REALTIME] 💰 Budget mis à jour")
                    _budgetUpdated.emit(Unit)
                }
                "comptes_cheque", "comptes_dette" -> {
                    println("[REALTIME] 🏦 Comptes mis à jour")
                    _comptesUpdated.emit(Unit)
                    _budgetUpdated.emit(Unit)
                }
                "enveloppes" -> {
                    println("[REALTIME] 📮 Enveloppes mises à jour")
                    _budgetUpdated.emit(Unit)
                }
                "categories" -> {
                    println("[REALTIME] 📂 Catégories mises à jour")
                    _categoriesUpdated.emit(Unit)
                    _budgetUpdated.emit(Unit)
                }
                "transactions" -> {
                    println("[REALTIME] 💸 Transactions mises à jour")
                    _transactionsUpdated.emit(Unit)
                    _budgetUpdated.emit(Unit)
                    _comptesUpdated.emit(Unit)
                }
            }

        } catch (e: Exception) {
            // Ignorer les erreurs de parsing silencieusement
        }
    }


    /**
     * Force une reconnexion (utile après login/logout).
     */
    fun reconnect() {
        println("[REALTIME] 🔄 Reconnexion forcée...")
        stopRealtimeSync()
        startRealtimeSync()
    }

    /**
     * Démarre le service après que l'utilisateur soit connecté.
     * À appeler après un login réussi.
     */
    fun startAfterLogin() {
        println("[REALTIME] 🔑 Démarrage après login...")
        startRealtimeSync()
    }
}
