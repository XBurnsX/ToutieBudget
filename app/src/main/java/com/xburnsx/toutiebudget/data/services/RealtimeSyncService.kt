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
        println("[REALTIME] 🚀 Démarrage du service temps réel...")

        if (isConnected) {
            println("[REALTIME] ⚠️ Déjà connecté, ignoré")
            return
        }

        serviceScope.launch {
            try {
                println("[REALTIME] 🔄 Tentative de connexion WebSocket...")
                connectWebSocket()
            } catch (e: Exception) {
                println("[REALTIME] ❌ Erreur connexion WebSocket: ${e.message}")
                println("[REALTIME] 🔄 Retry dans 5 secondes...")
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

    private suspend fun connectWebSocket() {
        println("[REALTIME] 🔍 Vérification de la connexion client...")

        if (!client.estConnecte()) {
            println("[REALTIME] ⚠️ Client non connecté, retry dans 3 secondes...")
            kotlinx.coroutines.delay(3000)
            startRealtimeSync() // Retry
            return
        }
        println("[REALTIME] ✅ Client connecté")

        val token = client.obtenirToken()
        if (token == null) {
            println("[REALTIME] ❌ Token manquant, abandon")
            return
        }
        println("[REALTIME] ✅ Token récupéré: ${token.take(10)}...")

        // 🚀 VRAIE SOLUTION TEMPS RÉEL : Server-Sent Events (SSE)
        println("[REALTIME] 🔄 Connexion SSE temps réel à PocketBase...")
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

                println("[REALTIME] 🌐 URL SSE: $sseUrl")

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
                    println("[REALTIME] ✅ Connexion SSE établie")
                    println("[REALTIME] ✅ Abonné aux collections: $collectionsParam")

                    // Lire le stream en temps réel
                    response.body?.source()?.let { source ->
                        while (isConnected && !source.exhausted()) {
                            try {
                                val line = source.readUtf8Line()
                                if (line != null) {
                                    println("[REALTIME] 📥 Ligne reçue: $line")
                                    if (line.startsWith("data: ")) {
                                        val data = line.substring(6) // Enlever "data: "
                                        handleRealtimeEvent(data)
                                    }
                                }
                            } catch (e: Exception) {
                                println("[REALTIME] ⚠️ Erreur lecture SSE: ${e.message}")
                                break
                            }
                        }
                    }
                } else {
                    println("[REALTIME] ❌ Erreur SSE: ${response.code} ${response.message}")
                    // Retry après 5 secondes
                    kotlinx.coroutines.delay(5000)
                    startRealtimeSync()
                }

            } catch (e: Exception) {
                println("[REALTIME] ❌ Erreur connexion SSE: ${e.message}")
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
            println("[REALTIME] 📨 Événement reçu: $data")

            val jsonEvent = gson.fromJson(data, JsonObject::class.java)
            val action = jsonEvent.get("action")?.asString
            val record = jsonEvent.get("record")?.asJsonObject
            val collection = record?.get("collectionName")?.asString

            println("[REALTIME] 🔄 Action: $action, Collection: $collection")

            // Notifier les ViewModels selon la collection modifiée
            when (collection) {
                "allocations_mensuelles" -> {
                    println("[REALTIME] 💰 Mise à jour budget (allocations)")
                    _budgetUpdated.emit(Unit)
                }
                "comptes_cheque", "comptes_dette" -> {
                    println("[REALTIME] 🏦 Mise à jour comptes")
                    _comptesUpdated.emit(Unit)
                    _budgetUpdated.emit(Unit) // Budget dépend aussi des comptes
                }
                "enveloppes" -> {
                    println("[REALTIME] 📮 Mise à jour budget (enveloppes)")
                    _budgetUpdated.emit(Unit)
                }
                "categories" -> {
                    println("[REALTIME] 📂 Mise à jour catégories")
                    _categoriesUpdated.emit(Unit)
                    _budgetUpdated.emit(Unit)
                }
                "transactions" -> {
                    println("[REALTIME] 💸 Mise à jour transactions")
                    _transactionsUpdated.emit(Unit)
                    _budgetUpdated.emit(Unit)
                    _comptesUpdated.emit(Unit)
                }
            }

        } catch (e: Exception) {
            println("[REALTIME] ❌ Erreur parsing événement: ${e.message}")
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
