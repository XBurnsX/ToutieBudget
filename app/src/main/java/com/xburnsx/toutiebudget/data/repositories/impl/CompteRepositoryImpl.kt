// chemin/simule: /data/repositories/impl/CompteRepositoryImpl.kt
// Dépendances: PocketBaseClient, Gson, Coroutines

package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.di.UrlResolver
import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

// Classe pour désérialiser la réponse paginée de PocketBase
data class ListeResultats<T>(
    val page: Int,
    val perPage: Int,
    val totalItems: Int,
    val totalPages: Int,
    val items: List<T>
)

class CompteRepositoryImpl : CompteRepository {

    private val client = PocketBaseClient
    private val gson = Gson()
    private val httpClient = okhttp3.OkHttpClient()

    // Noms des collections dans PocketBase
    private object Collections {
        const val CHEQUE = "comptes_cheque"
        const val CREDIT = "comptes_credit"
        const val DETTE = "comptes_dette"
        const val INVESTISSEMENT = "comptes_investissement"
    }

    override suspend fun recupererTousLesComptes(): Result<List<Compte>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            // Crée une coroutine pour chaque appel réseau
            val deferredCheque = async { recupererComptesDeCollection<CompteCheque>(Collections.CHEQUE, utilisateurId) }
            val deferredCredit = async { recupererComptesDeCollection<CompteCredit>(Collections.CREDIT, utilisateurId) }
            val deferredDette = async { recupererComptesDeCollection<CompteDette>(Collections.DETTE, utilisateurId) }
            val deferredInvestissement = async { recupererComptesDeCollection<CompteInvestissement>(Collections.INVESTISSEMENT, utilisateurId) }

            // Attend que tous les appels soient terminés
            val listes = awaitAll(deferredCheque, deferredCredit, deferredDette, deferredInvestissement)

            // Aplatit la liste de listes en une seule liste et trie
            val tousLesComptes = listes.flatten().filter { !it.estArchive }.sortedBy { it.ordre }

            Result.success(tousLesComptes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend inline fun <reified T : Compte> recupererComptesDeCollection(collection: String, utilisateurId: String): List<T> = withContext(Dispatchers.IO) {
        val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
        val urlBase = UrlResolver.obtenirUrlActive()

        // Filtre pour ne récupérer que les enregistrements de l'utilisateur connecté
        val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId'", "UTF-8")
        val url = "$urlBase/api/collections/$collection/records?filter=$filtreEncode&perPage=100"

        val requete = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        val reponse = httpClient.newCall(requete).execute()
        if (!reponse.isSuccessful) {
            throw Exception("Erreur lors de la récupération de la collection '$collection': ${reponse.code} ${reponse.body?.string()}")
        }

        val corpsReponse = reponse.body!!.string()
        val typeReponse = TypeToken.getParameterized(ListeResultats::class.java, T::class.java).type
        val resultatPagine: ListeResultats<T> = gson.fromJson(corpsReponse, typeReponse)

        resultatPagine.items
    }

    override suspend fun creerCompte(compte: Compte): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val collection = obtenirCollectionPourCompte(compte)
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé pour la création."))

            // Injecte l'ID de l'utilisateur dans l'objet compte avant la sérialisation
            // Pour les comptes chèque, initialise pret_a_placer avec la valeur du solde
            val compteAvecUtilisateur = when(compte) {
                is CompteCheque -> compte.copy(
                    utilisateurId = utilisateurId,
                    pretAPlacerRaw = compte.solde // Initialiser pret_a_placer avec le solde
                )
                is CompteCredit -> compte.copy(utilisateurId = utilisateurId)
                is CompteDette -> compte.copy(utilisateurId = utilisateurId)
                is CompteInvestissement -> compte.copy(utilisateurId = utilisateurId)
            }

            val corpsJson = gson.toJson(compteAvecUtilisateur)
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = UrlResolver.obtenirUrlActive()

            val requete = Request.Builder()
                .url("$urlBase/api/collections/$collection/records")
                .addHeader("Authorization", "Bearer $token")
                .post(corpsJson.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(requete).execute().use { reponse ->
                if (!reponse.isSuccessful) {
                    return@withContext Result.failure(Exception("Échec de la création: ${reponse.body?.string()}"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mettreAJourCompte(compte: Compte): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val collection = obtenirCollectionPourCompte(compte)
            val corpsJson = gson.toJson(compte)
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = UrlResolver.obtenirUrlActive()

            val requete = Request.Builder()
                .url("$urlBase/api/collections/$collection/records/${compte.id}")
                .addHeader("Authorization", "Bearer $token")
                .patch(corpsJson.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(requete).execute().use { reponse ->
                if (!reponse.isSuccessful) {
                    return@withContext Result.failure(Exception("Échec de la mise à jour: ${reponse.body?.string()}"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun supprimerCompte(compteId: String, collection: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = UrlResolver.obtenirUrlActive()

            val requete = Request.Builder()
                .url("$urlBase/api/collections/$collection/records/$compteId")
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            httpClient.newCall(requete).execute().use { reponse ->
                if (!reponse.isSuccessful) {
                    return@withContext Result.failure(Exception("Échec de la suppression: ${reponse.body?.string()}"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCompteById(compteId: String, collection: String): Compte? = withContext(Dispatchers.IO) {
        try {
            val result = recupererCompteParId(compteId, collection)
            result.getOrNull()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun mettreAJourSolde(compteId: String, collection: String, nouveauSolde: Double) = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: throw Exception("Token manquant")
            val urlBase = UrlResolver.obtenirUrlActive()

            val donneesUpdate = mapOf("solde" to nouveauSolde)
            val corpsRequete = gson.toJson(donneesUpdate)

            val requete = Request.Builder()
                .url("$urlBase/api/collections/$collection/records/$compteId")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .patch(corpsRequete.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(requete).execute().use { reponse ->
                if (!reponse.isSuccessful) {
                    throw Exception("Erreur lors de la mise à jour: ${reponse.code}")
                }
            }
        } catch (e: Exception) {
            throw e
        }
    }

    // ===== NOUVELLES MÉTHODES POUR LES TRANSACTIONS =====

    override suspend fun mettreAJourSoldeAvecVariation(compteId: String, collectionCompte: String, variationSolde: Double): Result<Unit> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.failure(Exception("Utilisateur non connecté"))
        }

        try {
            val token = client.obtenirToken() 
                ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = UrlResolver.obtenirUrlActive()

            // 1. Récupérer le solde actuel
            val resultCompte = recupererCompteParId(compteId, collectionCompte)
            if (resultCompte.isFailure) {
                throw resultCompte.exceptionOrNull() ?: Exception("Impossible de récupérer le compte")
            }

            val compte = resultCompte.getOrNull() 
                ?: throw Exception("Compte non trouvé")

            // 2. Calculer le nouveau solde
            val nouveauSolde = compte.solde + variationSolde

            // 3. Préparer les données de mise à jour (seulement le solde pour cette méthode)
            val donneesUpdate = mapOf("solde" to nouveauSolde)
            val corpsRequete = gson.toJson(donneesUpdate)

            val url = "$urlBase/api/collections/$collectionCompte/records/$compteId"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .patch(corpsRequete.toRequestBody("application/json".toMediaType()))
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la mise à jour du solde: ${reponse.code} ${reponse.body?.string()}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recupererCompteParId(compteId: String, collectionCompte: String): Result<Compte> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() 
                ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = UrlResolver.obtenirUrlActive()

            val url = "$urlBase/api/collections/$collectionCompte/records/$compteId"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la récupération du compte: ${reponse.code}")
            }

            val corpsReponse = reponse.body!!.string()
            val compte = deserialiserCompte(corpsReponse, collectionCompte)
                ?: throw Exception("Erreur lors de la désérialisation du compte")

            return@withContext Result.success(compte)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    // ===== MÉTHODES UTILITAIRES =====

    private fun obtenirCollectionPourCompte(compte: Compte): String {
        return when (compte) {
            is CompteCheque -> Collections.CHEQUE
            is CompteCredit -> Collections.CREDIT
            is CompteDette -> Collections.DETTE
            is CompteInvestissement -> Collections.INVESTISSEMENT
            else -> throw Exception("Type de compte non supporté")
        }
    }

    /**
     * Désérialise un compte depuis JSON PocketBase selon sa collection.
     */
    private fun deserialiserCompte(json: String, collection: String): Compte? {
        return try {
            when (collection) {
                Collections.CHEQUE -> gson.fromJson(json, CompteCheque::class.java)
                Collections.CREDIT -> gson.fromJson(json, CompteCredit::class.java)
                Collections.DETTE -> gson.fromJson(json, CompteDette::class.java)
                Collections.INVESTISSEMENT -> gson.fromJson(json, CompteInvestissement::class.java)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun mettreAJourSoldeAvecVariationEtPretAPlacer(
        compteId: String,
        collectionCompte: String,
        variationSolde: Double,
        mettreAJourPretAPlacer: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            println("[DEBUG] mettreAJourSoldeAvecVariationEtPretAPlacer - ERREUR: Utilisateur non connecté")
            return@withContext Result.failure(Exception("Utilisateur non connecté"))
        }

        try {
            val token = client.obtenirToken()
            if (token == null) {
                println("[DEBUG] mettreAJourSoldeAvecVariationEtPretAPlacer - ERREUR: Token manquant")
                return@withContext Result.failure(Exception("Token manquant"))
            }

            val urlBase = UrlResolver.obtenirUrlActive()
            println("[DEBUG] mettreAJourSoldeAvecVariationEtPretAPlacer - URL: $urlBase, compteId=$compteId, variation=$variationSolde")

            // 1. Récupérer le compte actuel
            val resultCompte = recupererCompteParId(compteId, collectionCompte)
            if (resultCompte.isFailure) {
                println("[DEBUG] mettreAJourSoldeAvecVariationEtPretAPlacer - ERREUR: Impossible de récupérer le compte: ${resultCompte.exceptionOrNull()?.message}")
                throw resultCompte.exceptionOrNull() ?: Exception("Impossible de récupérer le compte")
            }

            val compte = resultCompte.getOrNull()
            if (compte == null) {
                println("[DEBUG] mettreAJourSoldeAvecVariationEtPretAPlacer - ERREUR: Compte non trouvé")
                throw Exception("Compte non trouvé")
            }

            println("[DEBUG] mettreAJourSoldeAvecVariationEtPretAPlacer - Compte récupéré: solde actuel=${compte.solde}")

            // 2. Calculer le nouveau solde
            val nouveauSolde = compte.solde + variationSolde
            println("[DEBUG] mettreAJourSoldeAvecVariationEtPretAPlacer - Nouveau solde calculé: $nouveauSolde (${compte.solde} + $variationSolde)")

            // 3. Préparer les données de mise à jour
            val donneesUpdate = if (mettreAJourPretAPlacer && collectionCompte == Collections.CHEQUE && compte is CompteCheque) {
                // Pour les comptes chèque, mettre à jour aussi pret_a_placer si demandé
                val nouveauPretAPlacer = compte.pretAPlacer + variationSolde
                println("[DEBUG] mettreAJourSoldeAvecVariationEtPretAPlacer - Mise à jour avec prêt à placer: $nouveauPretAPlacer")
                mapOf(
                    "solde" to nouveauSolde,
                    "pret_a_placer" to nouveauPretAPlacer
                )
            } else {
                // Sinon, mettre à jour seulement le solde
                println("[DEBUG] mettreAJourSoldeAvecVariationEtPretAPlacer - Mise à jour solde seulement")
                mapOf("solde" to nouveauSolde)
            }
            val corpsRequete = gson.toJson(donneesUpdate)
            println("[DEBUG] mettreAJourSoldeAvecVariationEtPretAPlacer - Corps de la requête: $corpsRequete")

            val url = "$urlBase/api/collections/$collectionCompte/records/$compteId"
            println("[DEBUG] mettreAJourSoldeAvecVariationEtPretAPlacer - URL de mise à jour: $url")

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .patch(corpsRequete.toRequestBody("application/json".toMediaType()))
                .build()

            val reponse = httpClient.newCall(requete).execute()
            println("[DEBUG] mettreAJourSoldeAvecVariationEtPretAPlacer - Code de réponse: ${reponse.code}")

            if (!reponse.isSuccessful) {
                val messageErreur = reponse.body?.string() ?: "Erreur inconnue"
                println("[DEBUG] mettreAJourSoldeAvecVariationEtPretAPlacer - ERREUR HTTP: ${reponse.code} - $messageErreur")
                throw Exception("Erreur lors de la mise à jour: ${reponse.code} $messageErreur")
            }

            println("[DEBUG] mettreAJourSoldeAvecVariationEtPretAPlacer - Mise à jour réussie")

            // 🔄 DÉCLENCHER LES ÉVÉNEMENTS DE RAFRAÎCHISSEMENT
            BudgetEvents.onCompteUpdated(compteId)
            println("[DEBUG] mettreAJourSoldeAvecVariationEtPretAPlacer - Événement budget déclenché pour compte: $compteId")

            // 🔄 DÉCLENCHER LE RAFRAÎCHISSEMENT DE LA PAGE DES COMPTES
            val realtimeService = AppModule.provideRealtimeSyncService()
            realtimeService.declencherMiseAJourComptes()
            println("[DEBUG] mettreAJourSoldeAvecVariationEtPretAPlacer - Événement comptes déclenché pour compte: $compteId")

            Result.success(Unit)
        } catch (e: Exception) {
            println("[DEBUG] mettreAJourSoldeAvecVariationEtPretAPlacer - EXCEPTION: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun mettreAJourPretAPlacerSeulement(
        compteId: String,
        variationPretAPlacer: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.failure(Exception("Utilisateur non connecté"))
        }

        try {
            val token = client.obtenirToken()
                ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = UrlResolver.obtenirUrlActive()

            // 1. Récupérer le compte actuel (doit être un CompteCheque)
            val resultCompte = recupererCompteParId(compteId, Collections.CHEQUE)
            if (resultCompte.isFailure) {
                throw resultCompte.exceptionOrNull() ?: Exception("Impossible de récupérer le compte")
            }

            val compte = resultCompte.getOrNull() as? CompteCheque
                ?: throw Exception("Le compte n'est pas un compte chèque ou n'existe pas")

            // 2. Calculer le nouveau montant prêt à placer
            val nouveauPretAPlacer = compte.pretAPlacer + variationPretAPlacer

            // 3. Vérifier que le montant ne devient pas négatif
            if (nouveauPretAPlacer < 0) {
                throw Exception("Montant prêt à placer insuffisant")
            }

            // 4. Préparer les données de mise à jour (seulement pret_a_placer)
            val donneesUpdate = mapOf("pret_a_placer" to nouveauPretAPlacer)
            val corpsRequete = gson.toJson(donneesUpdate)

            val url = "$urlBase/api/collections/${Collections.CHEQUE}/records/$compteId"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .patch(corpsRequete.toRequestBody("application/json".toMediaType()))
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la mise à jour: ${reponse.code} ${reponse.body?.string()}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}