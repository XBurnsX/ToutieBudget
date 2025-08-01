package com.xburnsx.toutiebudget.data.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonElement
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
import java.util.concurrent.TimeUnit

/**
 * Service de synchronisation temps réel avec PocketBase.
 * Écoute les changements via WebSocket et notifie les ViewModels.
 * INVISIBLE à l'utilisateur - fonctionne en arrière-plan.
 */
@Singleton
class RealtimeSyncService @Inject constructor() {

    private val client = PocketBaseClient
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS) // Optimisé pour les connexions rapides
        .readTimeout(30, TimeUnit.SECONDS) // Long pour les SSE
        .writeTimeout(3, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(okhttp3.ConnectionPool(2, 5, TimeUnit.MINUTES)) // Pool dédié pour SSE
        .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Accept-Encoding", "gzip, deflate")
                .addHeader("Connection", "keep-alive")
                .build()
            chain.proceed(request)
        }
        .build()
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
                    "comptes_cheques",
                    "comptes_credits",
                    "comptes_dettes",
                    "comptes_investissement",
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
                    _budgetUpdated.emit(Unit)
                }
                "comptes_cheques", "comptes_credits", "comptes_dettes", "comptes_investissement" -> {
                    _comptesUpdated.emit(Unit)
                    _budgetUpdated.emit(Unit)
                }
                "enveloppes" -> {
                    _budgetUpdated.emit(Unit)
                }
                "categories" -> {
                    _categoriesUpdated.emit(Unit)
                    _budgetUpdated.emit(Unit)
                }
                "transactions" -> {
                    _transactionsUpdated.emit(Unit)
                    _budgetUpdated.emit(Unit)
                    _comptesUpdated.emit(Unit)
                }
                else -> {
                    // Collection non reconnue
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
        stopRealtimeSync()
        startRealtimeSync()
    }

    /**
     * Démarre le service après que l'utilisateur soit connecté.
     * À appeler après un login réussi.
     */
    fun startAfterLogin() {
        startRealtimeSync()
    }

    // Suppression de la fonction de debug et de tous les println/logs
    suspend fun supprimerToutesLesDonnees(): Result<Unit> = runCatching {
        val userId = client.obtenirUtilisateurConnecte()?.id ?: throw Exception("Utilisateur non connecté")

        var totalSupprime = 0

        // Liste de TOUS les noms possibles basés sur l'analyse de ton projet
        val nomsCollectionsPossibles = listOf(
            // Nom confirmé dans CategorieRepositoryImpl.kt
            "categorie",

            // Variations pour enveloppes (utilisé avec Collections.ENVELOPPES)
            "enveloppe", "enveloppes", "Enveloppe", "Enveloppes",

            // Variations pour allocations (utilisé avec Collections.ALLOCATIONS)
            "allocation", "allocations", "allocation_mensuelle", "allocations_mensuelles",
            "Allocation", "Allocations", "Allocation_Mensuelle", "Allocations_Mensuelles",

            // Variations pour transactions (utilisé avec Collections.TRANSACTIONS)
            "transaction", "transactions", "Transaction", "Transactions",

            // Variations pour comptes
            "compte", "comptes", "compte_cheque", "comptes_cheque",
            "compte_epargne", "comptes_epargne", "compte_credit", "comptes_credit",
            "compte_dette", "comptes_dette"
        )

        val collectionsExistantes = mutableListOf<String>()

        // Phase 1: Découvrir quelles collections existent vraiment

        for (nomCollection in nomsCollectionsPossibles) {
            try {
                val response = client.effectuerRequeteGet("/api/collections/$nomCollection/records", mapOf("perPage" to "1"))
                // Si on arrive ici sans erreur 404, la collection existe !
                collectionsExistantes.add(nomCollection)
            } catch (e: Exception) {
                if (e.message?.contains("404") == true) {
                    // Collection n'existe pas, c'est normal
                } else {
                    // Erreur pour $nomCollection: ${e.message}
                }
            }
        }

        // Phase 2: Supprimer SEULEMENT tes données dans chaque collection
        for (collection in collectionsExistantes) {
            try {
                // Récupérer TOUS les éléments de cette collection
                val response = client.effectuerRequeteGet("/api/collections/$collection/records", mapOf(
                    "perPage" to "500"
                ))

                val jsonResponse = gson.fromJson(response, JsonObject::class.java)
                val items = jsonResponse.getAsJsonArray("items")

                if (items.size() > 0) {
                    items.forEach { item: JsonElement ->
                        try {
                            val obj = item.asJsonObject
                            val id = obj.get("id").asString
                            val nom = obj.get("nom")?.asString
                                ?: obj.get("name")?.asString
                                ?: obj.get("titre")?.asString
                                ?: "Élément"
                            val itemUserId = obj.get("utilisateur_id")?.asString

                            // SUPPRIMER SEULEMENT SI C'EST TON UTILISATEUR
                            if (itemUserId == userId) {
                                try {
                                    client.effectuerRequeteDelete("/api/collections/$collection/records/$id")
                                    totalSupprime++
                                } catch (deleteE: Exception) {
                                    // Erreur suppression $nom: ${deleteE.message}
                                }
                            }

                        } catch (e: Exception) {
                            // Erreur traitement élément: ${e.message}
                        }
                    }
                }

            } catch (e: Exception) {
                // Erreur pour collection $collection: ${e.message}
            }
        }

        if (totalSupprime == 0) {
            // Aucune donnée trouvée pour ton utilisateur $userId
        } else {
            // SUCCÈS! $totalSupprime éléments supprimés pour TOI SEULEMENT!
        }
    }
}
