// chemin/simule: /data/repositories/impl/CategorieRepositoryImpl.kt
// Dépendances: PocketBaseClient, Gson, SafeDateAdapter, OkHttp3

package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.GsonBuilder
import com.xburnsx.toutiebudget.data.modeles.Categorie
import com.xburnsx.toutiebudget.data.repositories.CategorieRepository
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.net.URLEncoder
import com.xburnsx.toutiebudget.utils.SafeDateAdapter

/**
 * Implémentation du repository des catégories avec PocketBase.
 * Gère la création, récupération et suppression des catégories.
 */
class CategorieRepositoryImpl : CategorieRepository {
    
    private val client = PocketBaseClient
    private val gson = com.google.gson.GsonBuilder()
        .registerTypeAdapter(java.util.Date::class.java, SafeDateAdapter())
        .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
    private val httpClient = okhttp3.OkHttpClient()

    /**
     * Récupère toutes les catégories de l'utilisateur connecté.
     * @return Result contenant la liste des catégories ou une erreur
     */
    override suspend fun recupererToutesLesCategories(): Result<List<Categorie>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        
        try {
            val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id 
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))
            val urlBase = client.obtenirUrlBaseActive()
            
            // Filtre pour récupérer seulement les catégories de l'utilisateur
            val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId'", "UTF-8")
            val url = "$urlBase/api/collections/categorie/records?filter=$filtreEncode&perPage=100&sort=nom"
            
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
            val resultat = gson.fromJson(body, CategorieListResult::class.java)
            
            println("[DEBUG] Catégories récupérées: ${resultat.items.size}")
            Result.success(resultat.items)
        } catch (e: Exception) {
            println("[ERROR] Erreur récupération catégories: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Crée une nouvelle catégorie dans PocketBase.
     * @param categorie La catégorie à créer
     * @return Result contenant la catégorie créée avec son vrai ID ou une erreur
     */
    override suspend fun creerCategorie(categorie: Categorie): Result<Categorie> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
            val urlBase = client.obtenirUrlBaseActive()
            val url = "$urlBase/api/collections/categorie/records"
            
            // Créer les données à envoyer (sans l'ID temporaire)
            val donnees = mapOf(
                "utilisateur_id" to categorie.utilisateurId,
                "nom" to categorie.nom
            )
            
            val json = gson.toJson(donnees)
            println("[DEBUG] Création catégorie avec données: $json")
            
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
            
            // Récupérer la catégorie créée avec son vrai ID
            val responseBody = response.body?.string() ?: throw Exception("Réponse vide")
            val categorieCreee = gson.fromJson(responseBody, Categorie::class.java)
            
            println("[DEBUG] Catégorie créée: ID=${categorieCreee.id}, nom=${categorieCreee.nom}")
            Result.success(categorieCreee)
            
        } catch (e: Exception) {
            println("[ERROR] Erreur création catégorie: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Supprime une catégorie de PocketBase.
     * @param id L'ID de la catégorie à supprimer
     * @return Result indiquant le succès ou l'erreur
     */
    override suspend fun supprimerCategorie(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
            val urlBase = client.obtenirUrlBaseActive()
            val url = "$urlBase/api/collections/categorie/records/$id"
            
            println("[DEBUG] Suppression catégorie ID: $id")
            
            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()
                
            val response = httpClient.newCall(requete).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw Exception("Erreur PocketBase: ${response.code} - $errorBody")
            }
            
            println("[DEBUG] Catégorie supprimée avec succès")
            Result.success(Unit)
            
        } catch (e: Exception) {
            println("[ERROR] Erreur suppression catégorie: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Classe pour désérialiser la réponse de liste de PocketBase
     */
    private data class CategorieListResult(val items: List<Categorie>)
}