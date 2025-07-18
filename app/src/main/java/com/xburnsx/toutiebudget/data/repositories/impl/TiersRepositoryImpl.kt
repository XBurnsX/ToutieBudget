// filepath: c:\Users\XBurnsX\Desktop\Project\Kotlin\ToutieBudget2\app\src\main\java\com\xburnsx\toutiebudget\data\repositories\impl\TiersRepositoryImpl.kt
// chemin/simule: /data/repositories/impl/TiersRepositoryImpl.kt
// Dépendances: PocketBaseClient, Gson, Coroutines

package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xburnsx.toutiebudget.data.modeles.Tiers
import com.xburnsx.toutiebudget.data.repositories.TiersRepository
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.di.UrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

// Classe pour désérialiser la réponse paginée de PocketBase (locale à ce fichier)
private data class ListeResultatsTiers<T>(
    val page: Int,
    val perPage: Int,
    val totalItems: Int,
    val totalPages: Int,
    val items: List<T>
)

class TiersRepositoryImpl : TiersRepository {

    private val client = PocketBaseClient
    private val gson = Gson()
    private val httpClient = okhttp3.OkHttpClient()

    // Nom de la collection dans PocketBase
    private object Collections {
        const val TIERS = "tiers"
    }

    override suspend fun recupererTousLesTiers(): Result<List<Tiers>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
            val urlBase = UrlResolver.obtenirUrlActive()

            val filtreEncode = URLEncoder.encode("utilisateur_id='$utilisateurId'", "UTF-8")
            val url = "$urlBase/api/collections/${Collections.TIERS}/records?filter=$filtreEncode&sort=nom&perPage=100"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val typeToken = object : TypeToken<ListeResultatsTiers<Tiers>>() {}.type
                val resultat: ListeResultatsTiers<Tiers> = gson.fromJson(responseBody, typeToken)
                Result.success(resultat.items)
            } else {
                Result.failure(Exception("Erreur lors de la récupération des tiers: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun creerTiers(tiers: Tiers): Result<Tiers> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.failure(Exception("Utilisateur non connecté"))
        }
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
            val urlBase = UrlResolver.obtenirUrlActive()
            val url = "$urlBase/api/collections/${Collections.TIERS}/records"

            val tiersData = mapOf(
                "nom" to tiers.nom,
                "utilisateur_id" to utilisateurId
            )

            println("[DEBUG] TiersRepository - Création tiers: $tiersData")
            println("[DEBUG] TiersRepository - URL: $url")

            val jsonBody = gson.toJson(tiersData)
            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = httpClient.newCall(request).execute()

            println("[DEBUG] TiersRepository - Response code: ${response.code}")

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                println("[DEBUG] TiersRepository - Response body: $responseBody")

                val tiersCreated = gson.fromJson(responseBody, Tiers::class.java)
                println("[DEBUG] TiersRepository - Tiers créé: $tiersCreated")

                Result.success(tiersCreated)
            } else {
                val errorBody = response.body?.string()
                println("[DEBUG] TiersRepository - Erreur: ${response.code} - $errorBody")
                Result.failure(Exception("Erreur lors de la création du tiers: ${response.code} - $errorBody"))
            }
        } catch (e: Exception) {
            println("[DEBUG] TiersRepository - Exception: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun rechercherTiersParNom(recherche: String): Result<List<Tiers>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
            val urlBase = UrlResolver.obtenirUrlActive()

            val filtreEncode = URLEncoder.encode("utilisateur_id='$utilisateurId' && nom~'$recherche'", "UTF-8")
            val url = "$urlBase/api/collections/${Collections.TIERS}/records?filter=$filtreEncode&sort=nom&perPage=100"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val typeToken = object : TypeToken<ListeResultatsTiers<Tiers>>() {}.type
                val resultat: ListeResultatsTiers<Tiers> = gson.fromJson(responseBody, typeToken)
                Result.success(resultat.items)
            } else {
                Result.failure(Exception("Erreur lors de la recherche des tiers: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mettreAJourTiers(tiers: Tiers): Result<Unit> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.failure(Exception("Utilisateur non connecté"))
        }
        try {
            val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
            val urlBase = UrlResolver.obtenirUrlActive()
            val url = "$urlBase/api/collections/${Collections.TIERS}/records/${tiers.id}"

            val tiersData = mapOf(
                "nom" to tiers.nom
            )

            val jsonBody = gson.toJson(tiersData)
            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .patch(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Erreur lors de la mise à jour du tiers: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun supprimerTiers(tiersId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.failure(Exception("Utilisateur non connecté"))
        }
        try {
            val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
            val urlBase = UrlResolver.obtenirUrlActive()
            val url = "$urlBase/api/collections/${Collections.TIERS}/records/$tiersId"

            val request = Request.Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Erreur lors de la suppression du tiers: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
