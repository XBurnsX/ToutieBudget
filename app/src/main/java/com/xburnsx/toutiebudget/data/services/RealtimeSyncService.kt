package com.xburnsx.toutiebudget.data.services

import com.google.gson.Gson
import com.google.gson.JsonObject
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
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.TimeUnit
import java.net.URLEncoder
// import android.util.Log

/**
 * Service de synchronisation temps réel avec PocketBase.
 * Écoute les changements via WebSocket et notifie les ViewModels.
 * INVISIBLE à l'utilisateur - fonctionne en arrière-plan.
 */
@Singleton
class RealtimeSyncService @Inject constructor() {

    companion object {
        private const val PREFS_SYNC = "realtime_sync"
        private const val MARGE_PREMIER_LANCEMENT_MS = 5 * 60 * 1000L
    }

    private val client = PocketBaseClient
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS) // Optimisé pour les connexions rapides
        .readTimeout(6, TimeUnit.MINUTES) // SSE : PocketBase coupe les clients inactifs après ~5 min
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
    private var isConnected = false
    private val logTag = "PrefsSync"

    // Events pour notifier les ViewModels
    private val _budgetUpdated = MutableSharedFlow<Unit>()
    val budgetUpdated: SharedFlow<Unit> = _budgetUpdated.asSharedFlow()

    private val _comptesUpdated = MutableSharedFlow<Unit>()
    val comptesUpdated: SharedFlow<Unit> = _comptesUpdated.asSharedFlow()

    private val _categoriesUpdated = MutableSharedFlow<Unit>()

    /** CE13 : événement réservé aux mutations d'organisation reçues du
     * serveur après leur application dans Room. N'est pas émis pour chaque
     * transaction financière : évite de recharger les catégories inutilement. */
    private val _organisationUpdated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val organisationUpdated: SharedFlow<Unit> = _organisationUpdated.asSharedFlow()

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
            } catch (_: Exception) {
                // Retry après 5 secondes
                kotlinx.coroutines.delay(5000)
                startRealtimeSync()
            }
        }
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
     * Connexion Server-Sent Events (SSE) à PocketBase (v0.23+).
     *
     * Protocole : GET /api/realtime ouvre le flux et renvoie un événement PB_CONNECT avec
     * un clientId ; il faut ensuite POST /api/realtime {clientId, subscriptions} pour
     * recevoir les événements des collections. Sans ce POST, aucun événement n'arrive.
     */
    private fun startServerSentEvents(token: String) {
        serviceScope.launch {
            try {
                val urlBase = UrlResolver.obtenirUrlActive()

                val request = Request.Builder()
                    .url("$urlBase/api/realtime")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "text/event-stream")
                    .addHeader("Cache-Control", "no-cache")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    isConnected = true

                    val source = response.body?.source() ?: return@use
                    var nomEvenement = ""
                    val donnees = StringBuilder()

                    // Un événement SSE = lignes "event:" / "data:" terminées par une ligne vide
                    while (isConnected && !source.exhausted()) {
                        val ligne = source.readUtf8Line() ?: break
                        when {
                            ligne.isEmpty() -> {
                                if (donnees.isNotEmpty()) {
                                    traiterEvenementSse(nomEvenement, donnees.toString(), urlBase, token)
                                }
                                nomEvenement = ""
                                donnees.clear()
                            }
                            ligne.startsWith("event:") -> nomEvenement = ligne.removePrefix("event:").trim()
                            ligne.startsWith("data:") -> donnees.append(ligne.removePrefix("data:").trimStart())
                        }
                    }
                }
            } catch (_: Exception) {
                // Coupure réseau, timeout ou fermeture par le serveur : on se reconnecte plus bas
            }

            // Le flux s'est terminé (PocketBase coupe les clients inactifs) : se reconnecter
            isConnected = false
            kotlinx.coroutines.delay(5000)
            startRealtimeSync()
        }
    }

    private suspend fun traiterEvenementSse(nomEvenement: String, donnees: String, urlBase: String, token: String) {
        if (nomEvenement == "PB_CONNECT") {
            val clientId = try {
                gson.fromJson(donnees, JsonObject::class.java).get("clientId")?.asString
            } catch (_: Exception) {
                null
            } ?: return
            abonnerCollections(clientId, urlBase, token)
            // Rattraper ce qui a changé pendant qu'on n'écoutait pas (app fermée, hors ligne...)
            rattraperChangementsManques(urlBase, token)
            return
        }
        handleRealtimeEvent(donnees)
    }

    private fun abonnerCollections(clientId: String, urlBase: String, token: String) {
        val corps = JsonObject().apply {
            addProperty("clientId", clientId)
            add("subscriptions", com.google.gson.JsonArray().apply {
                PocketBaseMapper.collectionsSynchronisees.forEach { collection ->
                    if (collection !in PocketBaseMapper.collectionsFacultatives ||
                        collectionOptionnelleDisponible(collection, urlBase, token)) add("$collection/*")
                }
            })
        }
        val request = Request.Builder()
            .url("$urlBase/api/realtime")
            .addHeader("Authorization", "Bearer $token")
            .post(corps.toString().toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { reponse ->
            if (!reponse.isSuccessful) throw Exception("Abonnement temps réel refusé: ${reponse.code}")
        }
    }

    /**
     * Traite les événements temps réel reçus de PocketBase :
     * écrit la fiche dans Room, PUIS prévient les ViewModels (qui relisent Room).
     */
    private suspend fun handleRealtimeEvent(data: String) {
        try {
            val jsonEvent = gson.fromJson(data, JsonObject::class.java)
            val action = jsonEvent.get("action")?.asString
            val record = jsonEvent.get("record")?.asJsonObject
            val collection = record?.get("collectionName")?.asString ?: return

            if (action != "create" && action != "update" && action != "delete") return

            appliquerFicheDansRoom(collection, action, record)
            notifierChangement(collection)
        } catch (_: Exception) {
            // Ignorer les événements illisibles
        }
    }

    private suspend fun notifierChangement(collection: String) {
        when (collection) {
            "allocations_mensuelles" -> _budgetUpdated.emit(Unit)
            "enveloppes" -> {
                _budgetUpdated.emit(Unit)
                _organisationUpdated.emit(Unit)
            }
            "comptes_cheques", "comptes_credits", "comptes_dettes", "comptes_investissement" -> {
                _comptesUpdated.emit(Unit)
                _budgetUpdated.emit(Unit)
            }
            "categories" -> {
                _categoriesUpdated.emit(Unit)
                _budgetUpdated.emit(Unit)
                _organisationUpdated.emit(Unit)
            }
            "transactions" -> {
                _transactionsUpdated.emit(Unit)
                _budgetUpdated.emit(Unit)
                _comptesUpdated.emit(Unit)
            }
            "tiers", "pret_personnel", "alias_bancaires", "operations_bancaires_attente" -> _comptesUpdated.emit(Unit)
        }
    }

    /**
     * Écrit une fiche reçue du serveur dans Room.
     * Règle de conflit : si une modification locale de cette fiche attend encore d'être envoyée
     * (SyncJob PENDING/FAILED), la version locale gagne et la fiche du serveur est ignorée ;
     * elle sera écrasée sur le serveur quand le SyncJob passera.
     */
    /**
     * ARA4 : un serveur sans les champs d'archivage (script pas encore lancé) renvoie null ;
     * on garde alors la date et la raison du téléphone tant que la fiche reste archivée.
     */
    private fun archiveLeGarde(item: ItemPocketBase, archive: Boolean, serveur: String?, local: String?): String? =
        if (!archive) null else serveur ?: local.takeIf { item.archive_le == null }

    private fun raisonGardee(item: ItemPocketBase, archive: Boolean, serveur: String?, local: String?): String? =
        if (!archive) null else serveur ?: local.takeIf { item.raison_archivage == null }

    /** Optionnel : ne jamais bloquer les abonnements existants si le backend alias manque. */
    private fun collectionOptionnelleDisponible(collection: String, urlBase: String, token: String): Boolean {
        val request = Request.Builder()
            .url("$urlBase/api/collections/$collection/records?perPage=1")
            .addHeader("Authorization", "Bearer $token")
            .build()
        return try {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * SJ6 : « Garder la version du serveur » pour une tâche en conflit. La tâche locale doit déjà être retirée ;
     * la fiche actuelle du serveur remplace alors la version de l'appareil (ou la supprime si elle n'existe plus).
     */
    suspend fun reappliquerDepuisServeur(collection: String, recordId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = client.obtenirToken() ?: throw IllegalStateException("Connexion requise.")
            val urlBase = UrlResolver.obtenirUrlActive()
            val request = Request.Builder()
                .url("$urlBase/api/collections/$collection/records/$recordId")
                .addHeader("Authorization", "Bearer $token")
                .get().build()
            httpClient.newCall(request).execute().use { r ->
                when {
                    r.isSuccessful -> appliquerFicheDansRoom(collection, "update",
                        gson.fromJson(r.body?.string(), JsonObject::class.java))
                    r.code == 404 -> appliquerFicheDansRoom(collection, "delete",
                        JsonObject().apply { addProperty("id", recordId) })
                    else -> throw IllegalStateException("Serveur : HTTP ${r.code}")
                }
            }
            when (collection) {
                "allocations_mensuelles", "enveloppes", "categories" -> _budgetUpdated.emit(Unit)
                else -> _comptesUpdated.emit(Unit)
            }
        }
    }

    private suspend fun appliquerFicheDansRoom(collection: String, action: String, record: JsonObject) {
        val context = com.xburnsx.toutiebudget.ToutieBudgetApplication.getInstance() ?: return
        val db = com.xburnsx.toutiebudget.data.room.ToutieBudgetDatabase.getDatabase(context)
        val item = gson.fromJson(record, ItemPocketBase::class.java) ?: return

        // Ne garder que les fiches de l'utilisateur connecté
        val utilisateurId = client.obtenirUtilisateurConnecte()?.id ?: return
        if (!item.utilisateur_id.isNullOrBlank() && item.utilisateur_id != utilisateurId) return
        // SJ8 : une fiche supprimée sur cet appareil ne réapparaît pas par un ancien événement serveur
        if (action != "delete" && com.xburnsx.toutiebudget.workers.EtatFichesSync.estSupprimee(context, collection, item.id)) return
        // Une règle d'alias sans propriétaire n'est jamais importée.
        if (collection in PocketBaseMapper.collectionsFacultatives &&
            item.utilisateur_id != utilisateurId) return

        // CE13 : la comparaison avec les SyncJobs et l'écriture Room
        // doivent être atomiques. Sinon un événement serveur peut passer
        // entre un changement local et son entrée dans la file d'envoi.
        db.withTransaction {
            if (db.syncJobDao().compterTachesEnAttentePourFiche(item.id) > 0) {
                return@withTransaction
            }

        if (action == "delete") {
            when (collection) {
                "comptes_cheques" -> db.compteChequeDao().deleteCompteById(item.id)
                "comptes_credits" -> db.compteCreditDao().deleteCompteById(item.id)
                "comptes_dettes" -> db.compteDetteDao().deleteCompteById(item.id)
                "comptes_investissement" -> db.compteInvestissementDao().deleteCompteById(item.id)
                "categories" -> db.categorieDao().deleteCategorieById(item.id)
                "enveloppes" -> db.enveloppeDao().deleteEnveloppeById(item.id)
                "allocations_mensuelles" -> db.allocationMensuelleDao().deleteAllocationById(item.id)
                "transactions" -> db.transactionDao().deleteTransactionById(item.id)
                "tiers" -> db.tiersDao().deleteTiersById(item.id)
                "alias_bancaires" -> db.aliasBancaireDao().supprimer(item.id, utilisateurId)
                "operations_bancaires_attente" -> db.operationBancaireEnAttenteDao().supprimerDepuisServeur(item.id, utilisateurId)
                "pret_personnel" -> db.pretPersonnelDao().deletePretById(item.id)
                "historique_allocation" -> db.historiqueAllocationDao().supprimer(item.id, utilisateurId)
            }
            return@withTransaction
        }

        // create / update : les DAO insèrent en REPLACE, donc c'est un upsert.
        // Un montant ABSENT de la fiche serveur (champ inexistant dans la collection, ou non renvoyé) n'est pas un 0 :
        // on garde la valeur du téléphone au lieu d'écraser un solde connu (ex. CELI créé à 500 $ ramené à 0 $).
        when (val entite = PocketBaseMapper.versEntite(collection, item)) {
            is com.xburnsx.toutiebudget.data.room.entities.CompteCheque -> {
                val local = db.compteChequeDao().getCompteById(entite.id)
                db.compteChequeDao().insertCompte(
                    if (local == null) entite else entite.copy(
                        solde = if (item.solde == null) local.solde else entite.solde,
                        pretAPlacerRaw = if (item.pret_a_placer == null) local.pretAPlacerRaw else entite.pretAPlacerRaw,
                        archiveLe = archiveLeGarde(item, entite.estArchive, entite.archiveLe, local.archiveLe),
                        raisonArchivage = raisonGardee(item, entite.estArchive, entite.raisonArchivage, local.raisonArchivage)
                    )
                )
            }
            is com.xburnsx.toutiebudget.data.room.entities.CompteCredit -> {
                val local = db.compteCreditDao().getCompteById(entite.id)
                db.compteCreditDao().insertCompte(
                    (if (local == null || item.solde_utilise != null) entite else entite.copy(soldeUtilise = local.soldeUtilise))
                        .let { e -> if (local == null) e else e.copy(
                            archiveLe = archiveLeGarde(item, e.estArchive, e.archiveLe, local.archiveLe),
                            raisonArchivage = raisonGardee(item, e.estArchive, e.raisonArchivage, local.raisonArchivage)
                        ) }
                )
            }
            is com.xburnsx.toutiebudget.data.room.entities.CompteDette -> {
                val local = db.compteDetteDao().getCompteById(entite.id)
                db.compteDetteDao().insertCompte(
                    (if (local == null || item.solde_dette != null) entite else entite.copy(soldeDette = local.soldeDette))
                        .let { e -> if (local == null) e else e.copy(
                            archiveLe = archiveLeGarde(item, e.estArchive, e.archiveLe, local.archiveLe),
                            raisonArchivage = raisonGardee(item, e.estArchive, e.raisonArchivage, local.raisonArchivage)
                        ) }
                )
            }
            is com.xburnsx.toutiebudget.data.room.entities.CompteInvestissement -> {
                val local = db.compteInvestissementDao().getCompteById(entite.id)
                db.compteInvestissementDao().insertCompte(
                    if (local == null) entite else entite.copy(
                        solde = if (item.solde == null) local.solde else entite.solde,
                        detailsInvestissementJson = if (item.details_investissement_json == null) local.detailsInvestissementJson
                            else entite.detailsInvestissementJson,
                        archiveLe = archiveLeGarde(item, entite.estArchive, entite.archiveLe, local.archiveLe),
                        raisonArchivage = raisonGardee(item, entite.estArchive, entite.raisonArchivage, local.raisonArchivage)
                    )
                )
            }
            is com.xburnsx.toutiebudget.data.room.entities.Categorie -> db.categorieDao().insertCategorie(entite)
            is com.xburnsx.toutiebudget.data.room.entities.Enveloppe -> {
                val local = db.enveloppeDao().getEnveloppeById(entite.id)
                db.enveloppeDao().insertEnveloppe(
                    if (local == null) entite else entite.copy(
                        archiveLe = archiveLeGarde(item, entite.estArchive, entite.archiveLe, local.archiveLe),
                        raisonArchivage = raisonGardee(item, entite.estArchive, entite.raisonArchivage, local.raisonArchivage)
                    )
                )
            }
            is com.xburnsx.toutiebudget.data.room.entities.AllocationMensuelle -> {
                // GN3 : un serveur sans le champ ne remet pas le report du téléphone à 0
                val local = if (item.montant_reporte == null) db.allocationMensuelleDao().getAllocationById(entite.id) else null
                db.allocationMensuelleDao().insertAllocation(
                    if (local == null) entite else entite.copy(montantReporte = local.montantReporte)
                )
            }
            is com.xburnsx.toutiebudget.data.room.entities.Transaction -> db.transactionDao().insertTransaction(entite)
            is com.xburnsx.toutiebudget.data.room.entities.Tiers -> db.tiersDao().insertTiers(entite)
            is com.xburnsx.toutiebudget.data.room.entities.AliasBancaire -> db.aliasBancaireDao().enregistrer(entite)
            is com.xburnsx.toutiebudget.data.room.entities.OperationBancaireEnAttente ->
                db.operationBancaireEnAttenteDao().enregistrerDepuisServeur(entite)
            is com.xburnsx.toutiebudget.data.room.entities.PretPersonnel -> db.pretPersonnelDao().insertPret(entite)
            // HG4 : événement venu d'un autre appareil (même ID = jamais en double)
            is com.xburnsx.toutiebudget.data.room.entities.HistoriqueAllocation ->
                db.historiqueAllocationDao().insertHistorique(entite)
        }
        }
    }

    /**
     * Récupère les fiches modifiées sur le serveur depuis la dernière synchro (champ `updated`)
     * et les écrit dans Room. Les suppressions faites pendant l'absence ne sont pas rattrapées
     * (PocketBase ne garde pas de trace des fiches supprimées).
     *
     * Premier lancement : on part de maintenant (moins une marge) au lieu de tout retélécharger,
     * pour ne pas écraser des données locales qui n'auraient jamais été envoyées.
     */
    private suspend fun rattraperChangementsManques(urlBase: String, token: String) {
        val context = com.xburnsx.toutiebudget.ToutieBudgetApplication.getInstance() ?: return
        val prefs = context.getSharedPreferences(PREFS_SYNC, android.content.Context.MODE_PRIVATE)
        val utilisateurId = client.obtenirUtilisateurConnecte()?.id ?: return

        for (collection in PocketBaseMapper.collectionsSynchronisees) {
            val cle = "dernier_updated_$collection"
            val depuis = prefs.getString(cle, null)
            if (depuis == null && collection !in PocketBaseMapper.collectionsFacultatives) {
                prefs.edit().putString(cle, horodatageServeur(System.currentTimeMillis() - MARGE_PREMIER_LANCEMENT_MS)).apply()
                continue
            }
            // Contrairement aux anciennes collections, importer TOUS les alias au
            // premier passage : certains utilisateurs ont déjà fini leur import initial.
            val dateDepuis = depuis ?: "1970-01-01 00:00:00.000Z"

            try {
                var plusRecent: String = dateDepuis
                var page = 1
                while (true) {
                    val filtre = URLEncoder.encode(
                        "utilisateur_id='$utilisateurId' && updated>'$dateDepuis'",
                        Charsets.UTF_8.name()
                    )
                    val request = Request.Builder()
                        .url("$urlBase/api/collections/$collection/records?filter=$filtre&sort=updated&perPage=200&page=$page")
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                    val items = httpClient.newCall(request).execute().use { reponse ->
                        if (!reponse.isSuccessful) throw Exception("HTTP ${reponse.code}")
                        gson.fromJson(reponse.body?.string() ?: "{}", JsonObject::class.java)
                            .getAsJsonArray("items")
                    } ?: break

                    for (element in items) {
                        val record = element.asJsonObject
                        appliquerFicheDansRoom(collection, "update", record)
                        record.get("updated")?.asString?.let { if (it > plusRecent) plusRecent = it }
                    }
                    if (items.size() > 0) notifierChangement(collection)
                    if (items.size() < 200) break
                    page++
                }
                prefs.edit().putString(cle, plusRecent).apply()
            } catch (_: Exception) {
                // On garde l'ancien repère : on réessaiera à la prochaine connexion
            }
        }
    }

    /** Format des dates PocketBase ("2025-01-31 14:05:00.000Z"), en UTC. */
    private fun horodatageServeur(millis: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(millis))

    /**
     * Démarre le service après que l'utilisateur soit connecté.
     * À appeler après un login réussi.
     */
    fun startAfterLogin() {
        startRealtimeSync()
    }


    /**
     * Met à jour (ou crée) l'entrée de préférences utilisateur dans PocketBase.
     * Collection: user_preferences, clé relation: utilisateur_id (unique).
     */
    suspend fun mettreAJourPreferencesUtilisateur(changements: Map<String, Any?>) = withContext(Dispatchers.IO) {
        val user = client.obtenirUtilisateurConnecte() ?: throw Exception("Utilisateur non connecté")
        val token = client.obtenirToken() ?: throw Exception("Token manquant")
        val urlBase = UrlResolver.obtenirUrlActive()

        // 1) Lire l'existant pour cet utilisateur
        val rawFilter = "utilisateur_id=\"${user.id}\""
        val encodedFilter = URLEncoder.encode(rawFilter, Charsets.UTF_8.name())
        val getUrl = "$urlBase/api/collections/user_preferences/records?filter=$encodedFilter&perPage=1"

        // GET prefs → userId=${user.id}
        // GET url=${getUrl}

        val getReq = Request.Builder()
            .url(getUrl)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        val getResp = httpClient.newCall(getReq).execute()
        // GET code=${getResp.code}
        val items = if (getResp.isSuccessful) {
            try {
                val bodyStr = getResp.body?.string() ?: "{}"
                // GET body=${bodyStr}
                val obj = gson.fromJson(bodyStr, com.google.gson.JsonObject::class.java)
                obj.getAsJsonArray("items")
            } catch (_: Exception) { null }
        } else null

        val media = "application/json; charset=utf-8".toMediaType()

        if (items != null && items.size() > 0) {
            // PATCH sur le record existant
            val recordId = items[0].asJsonObject.get("id").asString
            // PATCH recordId=${recordId}
            val patchBody = gson.toJson(changements).toRequestBody(media)
            val patchUrl = "$urlBase/api/collections/user_preferences/records/$recordId"
            // PATCH url=${patchUrl}
            // PATCH json=${gson.toJson(changements)}
            val patchReq = Request.Builder()
                .url(patchUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .patch(patchBody)
                .build()
            val patchResp = httpClient.newCall(patchReq).execute()
            // val patchStr = patchResp.body?.string()
            // PATCH code=${patchResp.code}
            // PATCH body=${patchStr}
            if (!patchResp.isSuccessful) throw Exception("Echec MAJ préférences: ${patchResp.code}")
            // PATCH OK
        } else {
            // POST pour créer avec utilisateur_id + changements
            val bodyMap = HashMap<String, Any?>()
            bodyMap.putAll(changements)
            bodyMap["utilisateur_id"] = user.id
            val postBody = gson.toJson(bodyMap).toRequestBody(media)
            val postUrl = "$urlBase/api/collections/user_preferences/records"
            // POST url=${postUrl}
            // POST json=${gson.toJson(bodyMap)}
            val postReq = Request.Builder()
                .url(postUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .post(postBody)
                .build()
            val postResp = httpClient.newCall(postReq).execute()
            // val postStr = postResp.body?.string()
            // POST code=${postResp.code}
            // POST body=${postStr}
            if (!postResp.isSuccessful) throw Exception("Echec création préférences: ${postResp.code}")
            // POST OK
        }
    }

    /**
     * Récupère les préférences utilisateur à partir de PocketBase (collection user_preferences).
     * Retourne une map clé/valeur simple, les clés possibles: "theme", "figer_pret_a_placer",
     * "notifications_enabled", "notif_obj_jours_avant", "notif_enveloppe_negatif", "categories_ouvertes".
     */
    suspend fun recupererPreferencesUtilisateur(): Map<String, Any?> = withContext(Dispatchers.IO) {
        val utilisateur = client.obtenirUtilisateurConnecte() ?: throw Exception("Utilisateur non connecté")
        val token = client.obtenirToken() ?: throw Exception("Token manquant")
        val urlBase = UrlResolver.obtenirUrlActive()

        val rawFilter = "utilisateur_id=\"${utilisateur.id}\""
        val encodedFilter = java.net.URLEncoder.encode(rawFilter, Charsets.UTF_8.name())
        val getUrl = "$urlBase/api/collections/user_preferences/records?filter=$encodedFilter&perPage=1"

        // FETCH prefs → userId=${utilisateur.id}
        val getReq = okhttp3.Request.Builder()
            .url(getUrl)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        val response = httpClient.newCall(getReq).execute()
        if (!response.isSuccessful) {
            // FETCH prefs failed code=${response.code}
            return@withContext emptyMap<String, Any?>()
        }
        val bodyStr = response.body?.string().orEmpty()
        try {
            val root = gson.fromJson(bodyStr, com.google.gson.JsonObject::class.java)
            val items = root.getAsJsonArray("items")
            if (items == null || items.size() == 0) return@withContext emptyMap<String, Any?>()
            val record = items[0].asJsonObject
            val map = mutableMapOf<String, Any?>()
            if (record.has("theme")) map["theme"] = record.get("theme").asString
            if (record.has("figer_pret_a_placer")) map["figer_pret_a_placer"] = record.get("figer_pret_a_placer").asBoolean
            if (record.has("notifications_enabled")) map["notifications_enabled"] = record.get("notifications_enabled").asBoolean
            if (record.has("notif_obj_jours_avant")) map["notif_obj_jours_avant"] = record.get("notif_obj_jours_avant").asInt
            if (record.has("notif_enveloppe_negatif")) map["notif_enveloppe_negatif"] = record.get("notif_enveloppe_negatif").asBoolean
            if (record.has("categories_ouvertes")) {
                try {
                    val categoriesJson = record.get("categories_ouvertes")
                    if (!categoriesJson.isJsonNull) {
                        val categoriesMap = gson.fromJson(categoriesJson, Map::class.java)
                        map["categories_ouvertes"] = categoriesMap
                    }
                } catch (e: Exception) {
                    // Erreur parsing categories_ouvertes: ${e.message}
                }
            }
            if (record.has("enveloppes_favorites")) {
                try {
                    val favoritesJson = record.get("enveloppes_favorites")
                    if (favoritesJson.isJsonArray) {
                        map["enveloppes_favorites"] = favoritesJson.asJsonArray.map { it.asString }
                    }
                } catch (_: Exception) {
                    // Champ absent ou mal formé : les favoris restent ceux de l'appareil
                }
            }
            return@withContext map
        } catch (_: Exception) {
            return@withContext emptyMap<String, Any?>()
        }
    }

    /** PR5 : ce qui a vraiment été fait, pour un message exact (jamais un faux succès). */
    data class RapportSuppression(val collectionsVidees: List<String>, val collectionsAbsentes: List<String>)

    /**
     * PR1–PR5 : supprime les données PocketBase de l'utilisateur connecté SEULEMENT, puis cet appareil.
     * - Le serveur doit répondre avant que quoi que ce soit soit touché (hors ligne = rien ne change).
     * - La file SyncJobs est arrêtée et vidée d'abord : aucune ancienne tâche ne peut recréer des données.
     * - Chaque collection est vidée page par page (pas de limite à 500), puis VÉRIFIÉE vide.
     * - [garderPreferences] : « nouveau budget vide » garde thème et réglages ; l'effacement définitif non.
     */
    suspend fun supprimerToutesLesDonnees(garderPreferences: Boolean = false): Result<RapportSuppression> = runCatching {
        val userId = client.obtenirUtilisateurConnecte()?.id ?: throw Exception("Utilisateur non connecté")
        val filtre = "utilisateur_id = '$userId'"
        // Ordre enfants -> parents pour respecter d'éventuelles contraintes de relation.
        val collections = buildList {
            addAll(listOf("historique_allocation", "operations_bancaires_attente", "alias_bancaires",
                "transactions", "allocations_mensuelles", "pret_personnel", "tiers", "enveloppes", "categories",
                "comptes_investissement", "comptes_dettes", "comptes_credits", "comptes_cheques"))
            if (!garderPreferences) add("user_preferences")
        }
        // Collections facultatives : absentes sur un ancien serveur, ce n'est pas une erreur
        val facultatives = PocketBaseMapper.collectionsFacultatives + "user_preferences"

        // PR4 : serveur joignable AVANT toute action
        try {
            client.effectuerRequeteGet("/api/collections/comptes_cheques/records", mapOf("perPage" to "1", "filter" to filtre))
        } catch (e: Exception) {
            throw Exception("Serveur injoignable : rien n'a été supprimé (${e.message})")
        }
        com.xburnsx.toutiebudget.di.AppModule.arreterEtViderSynchronisation()

        val erreurs = mutableListOf<String>()
        val absentes = mutableListOf<String>()
        val videes = mutableListOf<String>()
        fun estAbsente(e: Exception) = e.message?.contains("HTTP 404") == true

        for (collection in collections) {
            var itemsCount: Int
            var suppressionEchouee = false
            var absente = false
            do {
                val json = try {
                    gson.fromJson(
                        client.effectuerRequeteGet(
                            "/api/collections/$collection/records",
                            // toujours la page 1 : les suppressions font remonter la suite
                            mapOf("page" to "1", "perPage" to "200", "filter" to filtre)
                        ),
                        JsonObject::class.java
                    )
                } catch (e: Exception) {
                    if (collection in facultatives && estAbsente(e)) absente = true
                    else erreurs += "$collection (lecture : ${e.message})"
                    break
                }
                val items = json.getAsJsonArray("items") ?: com.google.gson.JsonArray()
                itemsCount = items.size()
                for (item in items) {
                    val obj = item.asJsonObject
                    // PR3 : jamais une fiche d'un autre utilisateur, même si le filtre serveur échouait
                    if (obj.get("utilisateur_id")?.asString != userId) {
                        erreurs += "$collection/${obj.get("id")?.asString} (propriétaire inattendu)"
                        suppressionEchouee = true
                        continue
                    }
                    val id = obj.get("id")?.asString.orEmpty()
                    try {
                        client.effectuerRequeteDelete("/api/collections/$collection/records/$id")
                    } catch (e: Exception) {
                        erreurs += "$collection/$id (${e.message})"
                        suppressionEchouee = true
                    }
                }
            } while (itemsCount > 0 && !suppressionEchouee)
            if (absente) {
                absentes += collection
                continue
            }

            // PR4/PR5 : vérification finale, la collection doit être réellement vide pour cet utilisateur
            try {
                val reste = gson.fromJson(
                    client.effectuerRequeteGet("/api/collections/$collection/records", mapOf("perPage" to "1", "filter" to filtre)),
                    JsonObject::class.java
                ).get("totalItems")?.asInt ?: 0
                if (reste > 0) erreurs += "$collection ($reste fiche(s) restante(s))" else videes += collection
            } catch (e: Exception) {
                erreurs += "$collection (vérification : ${e.message})"
            }
        }
        if (erreurs.isNotEmpty()) {
            throw Exception(
                "Suppression INCOMPLÈTE sur le serveur (${erreurs.size} problème(s)) : ${erreurs.take(6).joinToString()}" +
                    (if (erreurs.size > 6) "…" else "") + ". Cet appareil n'a pas été effacé ; la synchro est en pause."
            )
        }

        // PR4/PR5 : seulement après un serveur vérifié vide
        com.xburnsx.toutiebudget.di.AppModule.effacerToutesLesDonneesLocales()
        RapportSuppression(videes, absentes)
    }
}
