package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.Gson
import com.xburnsx.toutiebudget.data.modeles.Categorie
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.net.URLEncoder

class CategorieRepositoryImpl : CategorieRepository {
    private val client = PocketBaseClient
    private val gson = Gson().newBuilder()
        .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
    private val httpClient = okhttp3.OkHttpClient()

    override suspend fun recupererToutesLesCategories(): Result<List<Categorie>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        try {
            val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))
            val urlBase = client.obtenirUrlBaseActive()
            val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId'", "UTF-8")
            val url = "$urlBase/api/collections/categorie/records?filter=$filtreEncode&perPage=100"
            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            val response = httpClient.newCall(requete).execute()
            if (!response.isSuccessful) throw Exception("Erreur PocketBase: ${response.code}")
            val body = response.body?.string() ?: throw Exception("Réponse vide")
            val items = gson.fromJson(body, CategorieListResult::class.java).items
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun creerCategorie(categorie: Categorie): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
            val urlBase = client.obtenirUrlBaseActive()
            val url = "$urlBase/api/collections/categorie/records"
            
            // Créer un map sans l'ID pour que PocketBase le génère automatiquement
            val dataMap = mapOf(
                "utilisateur_id" to categorie.utilisateurId,
                "nom" to categorie.nom
            )
            
            val json = gson.toJson(dataMap)
            println("[DEBUG] JSON envoyé à PocketBase: $json")
            
            val body = json.toRequestBody("application/json".toMediaType())
            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            val response = httpClient.newCall(requete).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                println("[DEBUG] Réponse d'erreur PocketBase: $errorBody")
                throw Exception("Erreur PocketBase: ${response.code} - $errorBody")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun supprimerCategorie(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
            val urlBase = client.obtenirUrlBaseActive()
            val url = "$urlBase/api/collections/categorie/records/$id"
            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()
            val response = httpClient.newCall(requete).execute()
            if (!response.isSuccessful) throw Exception("Erreur PocketBase: ${response.code}")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Pour désérialiser la réponse PocketBase
    data class CategorieListResult(val items: List<Categorie>)
} 