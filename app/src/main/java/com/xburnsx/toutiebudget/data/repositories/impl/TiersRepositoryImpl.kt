// chemin/simule: /data/repositories/impl/TiersRepositoryImpl.kt
// Dépendances: PocketBaseClient, Gson, SafeDateAdapter, OkHttp3

package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.GsonBuilder
import com.xburnsx.toutiebudget.data.modeles.Tiers
import com.xburnsx.toutiebudget.data.repositories.TiersRepository
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.di.UrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.net.URLEncoder
import com.xburnsx.toutiebudget.utils.SafeDateAdapter

/**
 * Implémentation du repository des tiers avec PocketBase.
 * Gère la création, récupération et recherche des tiers.
 */
class TiersRepositoryImpl : TiersRepository {
    
    private val client = PocketBaseClient
    private val gson = com.google.gson.GsonBuilder()
        .registerTypeAdapter(java.util.Date::class.java, SafeDateAdapter())
        .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
    private val httpClient = okhttp3.OkHttpClient()

    /**
     * Récupère tous les tiers de l'utilisateur connecté.
     */
    override suspend fun recupererTousLesTiers(): Result<List<Tiers>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        
        try {
            val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id 
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))
            val urlBase = UrlResolver.obtenirUrlActive()
            
            // Filtre pour récupérer seulement les tiers de l'utilisateur
            val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId'", "UTF-8")
            val url = "$urlBase/api/collections/tiers/records?filter=$filtreEncode&perPage=500&sort=nom"
            
            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
                
            val response = httpClient.newCall(requete).execute()
            if (!response.isSuccessful) {
                throw Exception("Erreur PocketBase: ${response.code}")
            }
            
            val body = response.body?.string() ?: throw Exception("Réponse vide")
            val resultat = gson.fromJson(body, TiersListResult::class.java)
            
            Result.success(resultat.items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Crée un nouveau tiers dans PocketBase.
     */
    override suspend fun creerTiers(tiers: Tiers): Result<Tiers> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id 
                ?: throw Exception("ID utilisateur non trouvé.")
            val urlBase = UrlResolver.obtenirUrlActive()
            val url = "$urlBase/api/collections/tiers/records"
            
            // Créer les données à envoyer (sans l'ID temporaire)
            val donnees = mapOf(
                "utilisateur_id" to utilisateurId,
                "nom" to tiers.nom
            )
            
            val json = gson.toJson(donnees)
            
            val body = json.toRequestBody("application/json".toMediaType())
            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
                
            val response = httpClient.newCall(requete).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw Exception("Erreur PocketBase: ${response.code} - $errorBody")
            }
            
            // Récupérer le tiers créé avec son vrai ID
            val responseBody = response.body?.string() ?: throw Exception("Réponse vide")
            val tiersCreee = gson.fromJson(responseBody, Tiers::class.java)
            Result.success(tiersCreee)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Recherche des tiers par nom (insensible à la casse).
     */
    override suspend fun rechercherTiers(recherche: String): Result<List<Tiers>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte() || recherche.isBlank()) {
            return@withContext Result.success(emptyList())
        }
        
        try {
            val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id 
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))
            val urlBase = UrlResolver.obtenirUrlActive()
            
            // Filtre pour recherche insensible à la casse
            val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId' && nom ~ '$recherche'", "UTF-8")
            val url = "$urlBase/api/collections/tiers/records?filter=$filtreEncode&perPage=100&sort=nom"
            
            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
                
            val response = httpClient.newCall(requete).execute()
            if (!response.isSuccessful) {
                throw Exception("Erreur PocketBase: ${response.code}")
            }
            
            val body = response.body?.string() ?: throw Exception("Réponse vide")
            val resultat = gson.fromJson(body, TiersListResult::class.java)
            
            Result.success(resultat.items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Classe pour désérialiser la réponse de liste de PocketBase
     */
    private data class TiersListResult(val items: List<Tiers>)
}