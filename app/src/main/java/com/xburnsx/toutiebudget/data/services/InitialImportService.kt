package com.xburnsx.toutiebudget.data.services

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.xburnsx.toutiebudget.data.room.daos.*
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.di.UrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Import initial PocketBase -> Room.
 *
 * - Toutes les collections sont paginées jusqu'au bout (SJ13).
 * - Une erreur HTTP / réseau / JSON fait échouer l'import au lieu de passer pour une liste vide (SJ14, LD8, LD15).
 * - Fusion sans écrasement : une fiche avec une modification locale pas encore envoyée
 *   (SyncJob PENDING/FAILED) n'est pas remplacée par la version du serveur (LD2, SJ15).
 * - L'import est marqué complet par utilisateur ; tant qu'il ne l'est pas, il est relancé,
 *   même si Room contient déjà des transactions (LD1, LD10).
 */
class InitialImportService(
    private val compteChequeDao: CompteChequeDao,
    private val compteCreditDao: CompteCreditDao,
    private val compteDetteDao: CompteDetteDao,
    private val compteInvestissementDao: CompteInvestissementDao,
    private val transactionDao: TransactionDao,
    private val categorieDao: CategorieDao,
    private val enveloppeDao: EnveloppeDao,
    private val allocationMensuelleDao: AllocationMensuelleDao,
    private val tiersDao: TiersDao,
    private val aliasBancaireDao: AliasBancaireDao,
    private val operationBancaireEnAttenteDao: OperationBancaireEnAttenteDao,
    private val pretPersonnelDao: PretPersonnelDao,
    private val syncJobDao: SyncJobDao
) {

    /** Échec d'import d'une collection précise ; les collections déjà importées restent dans Room. */
    class ImportIncompletException(val collection: String, cause: Throwable?) :
        Exception("Import de « $collection » impossible : ${cause?.message ?: "erreur inconnue"}", cause)

    private val client = PocketBaseClient
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val gson: Gson = GsonBuilder().create()
    private val logTag = "InitialImport"

    // Callback pour la progression
    var onProgressUpdate: ((step: Int, message: String) -> Unit)? = null

    private fun prefs() = com.xburnsx.toutiebudget.ToutieBudgetApplication.getInstance()
        ?.getSharedPreferences(PREFS_IMPORT, Context.MODE_PRIVATE)

    /** Vrai si l'import complet a déjà réussi pour l'utilisateur connecté. */
    fun importEstComplet(): Boolean {
        val utilisateurId = client.obtenirUtilisateurConnecte()?.id ?: return false
        return prefs()?.getBoolean(cleImportComplet(utilisateurId), false) ?: false
    }

    /** Vrai si Room contient déjà des données de l'utilisateur (pour « Continuer avec les données locales »). */
    suspend fun possedeDonneesLocales(): Boolean = withContext(Dispatchers.IO) {
        val utilisateurId = client.obtenirUtilisateurConnecte()?.id ?: return@withContext false
        compteChequeDao.getComptesCount(utilisateurId) > 0 ||
            transactionDao.getTransactionsCount(utilisateurId) > 0 ||
            enveloppeDao.getEnveloppesCount(utilisateurId) > 0
    }

    /**
     * Lance l'import complet si l'utilisateur ne l'a pas encore terminé.
     * Les changements suivants arrivent par le temps réel et son rattrapage (RealtimeSyncService).
     */
    suspend fun importerDonneesInitiales(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            onProgressUpdate?.invoke(1, "Vérification de la connexion...")
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("Utilisateur non connecté. Veuillez vous reconnecter."))

            if (importEstComplet()) {
                onProgressUpdate?.invoke(4, "Données déjà synchronisées")
                return@withContext Result.success(Unit)
            }

            val token = client.obtenirToken()
                ?: return@withContext Result.failure(Exception("Session expirée. Veuillez vous reconnecter."))
            val urlBase = UrlResolver.obtenirUrlActive()
            if (!client.verifierConnexionServeur()) {
                return@withContext Result.failure(Exception("Serveur inaccessible. Vérifiez votre connexion internet."))
            }

            onProgressUpdate?.invoke(2, "Import des comptes et catégories...")
            importer("comptes_cheques", urlBase, token, utilisateurId) { compteChequeDao.insertAll(it.map(PocketBaseMapper::versCompteCheque)) }
            importer("comptes_credits", urlBase, token, utilisateurId) { compteCreditDao.insertAll(it.map(PocketBaseMapper::versCompteCredit)) }
            importer("comptes_dettes", urlBase, token, utilisateurId) { compteDetteDao.insertAll(it.map(PocketBaseMapper::versCompteDette)) }
            importer("comptes_investissement", urlBase, token, utilisateurId) { compteInvestissementDao.insertAll(it.map(PocketBaseMapper::versCompteInvestissement)) }
            importer("categories", urlBase, token, utilisateurId) { categorieDao.insertAll(it.map(PocketBaseMapper::versCategorie)) }
            importer("tiers", urlBase, token, utilisateurId) { tiersDao.insertAll(it.map(PocketBaseMapper::versTiers)) }
            // Une ancienne instance PocketBase peut ne pas avoir cette collection.
            // Son absence (404) ne doit pas rendre l'APK inutilisable.
            try {
                importer("alias_bancaires", urlBase, token, utilisateurId) { fiches ->
                    aliasBancaireDao.enregistrerToutes(
                        fiches.filter { it.utilisateur_id == utilisateurId }
                            .mapNotNull(PocketBaseMapper::versAliasBancaire)
                    )
                }
            } catch (e: ImportIncompletException) {
                if (e.cause?.message != "HTTP 404") throw e
                Log.w(logTag, "Collection alias_bancaires absente : configuration PocketBase requise")
            }
            try {
                importer("operations_bancaires_attente", urlBase, token, utilisateurId) { fiches ->
                    operationBancaireEnAttenteDao.enregistrerToutesDepuisServeur(
                        fiches.filter { it.utilisateur_id == utilisateurId }
                            .mapNotNull(PocketBaseMapper::versOperationBancaire)
                    )
                }
            } catch (e: ImportIncompletException) {
                if (e.cause?.message != "HTTP 404") throw e
                Log.w(logTag, "Collection operations_bancaires_attente absente : migration serveur requise")
            }
            importer("pret_personnel", urlBase, token, utilisateurId) { pretPersonnelDao.insertAll(it.map(PocketBaseMapper::versPretPersonnel)) }

            onProgressUpdate?.invoke(3, "Import des transactions et enveloppes...")
            importer("enveloppes", urlBase, token, utilisateurId) { enveloppeDao.insertAll(it.map(PocketBaseMapper::versEnveloppe)) }
            importer("allocations_mensuelles", urlBase, token, utilisateurId) { allocationMensuelleDao.insertAll(it.map(PocketBaseMapper::versAllocationMensuelle)) }
            importer("transactions", urlBase, token, utilisateurId) { transactionDao.insertAll(it.map(PocketBaseMapper::versTransaction)) }

            onProgressUpdate?.invoke(4, "Finalisation et synchronisation...")
            prefs()?.edit()?.putBoolean(cleImportComplet(utilisateurId), true)?.apply()
            Result.success(Unit)
        } catch (e: ImportIncompletException) {
            Log.e(logTag, "Import incomplet : ${e.collection} (${e.cause?.javaClass?.simpleName})")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(logTag, "Import impossible (${e.javaClass.simpleName})")
            Result.failure(e)
        }
    }

    /**
     * Télécharge TOUTES les pages d'une collection pour l'utilisateur, garde seulement les fiches
     * sans modification locale en attente, puis les écrit dans Room.
     * Toute erreur lève [ImportIncompletException] : jamais de liste vide « réussie ».
     */
    private suspend fun importer(
        collection: String,
        urlBase: String,
        token: String,
        utilisateurId: String,
        ecrire: suspend (List<ItemPocketBase>) -> Unit
    ) {
        val items = try {
            telechargerToutesLesPages(collection, urlBase, token, utilisateurId)
        } catch (e: Exception) {
            throw ImportIncompletException(collection, e)
        }
        val aEcrire = items
            .filter { it.utilisateur_id.isNullOrBlank() || it.utilisateur_id == utilisateurId }
            .filter { syncJobDao.compterTachesEnAttentePourFiche(it.id) == 0 }
        if (aEcrire.isNotEmpty()) ecrire(aEcrire)
        Log.d(logTag, "✅ $collection : ${items.size} fiches (${items.size - aEcrire.size} gardées en local)")
    }

    private fun telechargerToutesLesPages(
        collection: String,
        urlBase: String,
        token: String,
        utilisateurId: String
    ): List<ItemPocketBase> {
        val resultat = mutableListOf<ItemPocketBase>()
        var page = 1
        while (true) {
            val url = "$urlBase/api/collections/$collection/records".toHttpUrl().newBuilder()
                .addQueryParameter("filter", "utilisateur_id='$utilisateurId'")
                .addQueryParameter("sort", "id") // ordre stable entre les pages
                .addQueryParameter("perPage", PAR_PAGE.toString())
                .addQueryParameter("page", page.toString())
                .build()
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()
            val liste = httpClient.newCall(request).execute().use { reponse ->
                if (!reponse.isSuccessful) throw Exception("HTTP ${reponse.code}")
                val corps = reponse.body?.string() ?: throw Exception("Réponse vide")
                gson.fromJson(corps, ListeResultats::class.java)
                    ?: throw Exception("Réponse illisible")
            }
            val items = liste.items ?: throw Exception("Réponse sans liste « items »")
            resultat.addAll(items)
            if (items.size < PAR_PAGE || page >= PAGES_MAX) break
            page++
        }
        return resultat
    }

    private fun cleImportComplet(utilisateurId: String) = "import_complet_$utilisateurId"

    companion object {
        private const val PREFS_IMPORT = "import_initial"
        private const val PAR_PAGE = 500
        private const val PAGES_MAX = 1000 // garde-fou : 500 000 fiches par collection
    }
}

/**
 * Classes pour la désérialisation JSON de PocketBase
 */
data class ListeResultats(
    val items: List<ItemPocketBase>? // null si la réponse est mal formée
)

data class ItemPocketBase(
    val id: String,
    val utilisateur_id: String?,
    val nom: String?,
    val solde: String?,
    val solde_utilise: String?,
    val solde_dette: String?,
    val pret_a_placer: String?,
    val couleur: String?,
    val archive: Boolean?,
    val est_archive: Boolean?,
    val ordre: Int?,
    val limite_credit: String?,
    val taux_interet: String?,
    val paiement_minimum: String?,
    val frais_mensuels_json: Any?, // tableau JSON ou texte JSON selon la façon dont il a été enregistré
    val montant_initial: String?,
    val paiement_effectue: Int?,
    val duree_mois_pret: String?,
    val prix_total: String?,
    val collection: String?,
    val type: String?,
    val montant: String?,
    val date: String?,
    val note: String?,
    val compte_id: String?,
    val collection_compte: String?,
    val allocation_mensuelle_id: String?,
    val est_fractionnee: Boolean?,
    val sous_items: Any?, // 🎯 CORRECTION : Peut être List<*> ou un objet JSON
    val tiers_utiliser: String?,
    val created: String?,
    val updated: String?,
    val categorie_id: String?,
    val frequence_objectif: String?,
    val montant_objectif: String?,
    val date_objectif: String?,
    val date_debut_objectif: String?,
    val objectif_jour: String?,
    val reset_apres_echeance: Boolean?,
    val enveloppe_id: String?,
    val mois: String?,
    val alloue: String?,
    val depense: String?,
    val compte_source_id: String?,
    val collection_compte_source: String?,
    val collectionId: String?,
    val collectionName: String?,
    val nom_tiers: String?,
    val date_creation: String?,
    val details_carte_json: Any? = null, // Cartes de crédit (section 10)
    val details_dette_json: Any? = null, // Dettes (section 11)
    val details_investissement_json: Any? = null, // Placements (section 12)
    val libelle_brut: String? = null,
    val tiers_id: String? = null,
    val active: Boolean? = null,
    val source: String? = null,
    val id_externe: String? = null,
    val date_operation: String? = null,
    val montant_centimes: String? = null,
    val statut_bancaire: String? = null,
    val decision: String? = null,
    val verification_requise: Boolean? = null,
    val montant_final_centimes: String? = null
)
