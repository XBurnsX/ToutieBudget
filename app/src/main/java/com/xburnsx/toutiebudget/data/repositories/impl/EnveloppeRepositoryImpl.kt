// chemin/simule: /data/repositories/impl/EnveloppeRepositoryImpl.kt
// Dépendances: PocketBaseClient, Gson, SafeDateAdapter, OkHttp3, Modèles

package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.reflect.TypeToken
import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.repositories.EnveloppeRepository
import com.xburnsx.toutiebudget.di.PocketBaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.Date
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaType
import com.xburnsx.toutiebudget.utils.SafeDateAdapter

/**
 * Implémentation du repository des enveloppes avec PocketBase.
 * Gère la création, récupération et mise à jour des enveloppes et allocations.
 */
class EnveloppeRepositoryImpl : EnveloppeRepository {
    
    private val client = PocketBaseClient
    private val gson = com.google.gson.GsonBuilder()
        .registerTypeAdapter(java.util.Date::class.java, SafeDateAdapter())
        .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
    private val httpClient = okhttp3.OkHttpClient()

    // Noms des collections dans PocketBase
    private object Collections {
        const val ENVELOPPES = "enveloppes"
        const val ALLOCATIONS = "allocations_mensuelles"
    }

    /**
     * Récupère toutes les enveloppes de l'utilisateur connecté.
     * @return Result contenant la liste des enveloppes
     */
    override suspend fun recupererToutesLesEnveloppes(): Result<List<Enveloppe>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            // Filtre pour récupérer seulement les enveloppes de l'utilisateur
            val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId'", "UTF-8")
            val url = "$urlBase/api/collections/${Collections.ENVELOPPES}/records?filter=$filtreEncode&perPage=100&sort=ordre,nom"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la récupération des enveloppes: ${reponse.code}")
            }

            val corpsReponse = reponse.body!!.string()
            val typeReponse = TypeToken.getParameterized(ListeResultats::class.java, Enveloppe::class.java).type
            val resultatPagine: ListeResultats<Enveloppe> = gson.fromJson(corpsReponse, typeReponse)

            val enveloppes = resultatPagine.items.filter { !it.estArchive }
            println("[DEBUG] Enveloppes récupérées: ${enveloppes.size}")
            Result.success(enveloppes)
            
        } catch (e: Exception) {
            println("[ERROR] Erreur récupération enveloppes: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Crée une nouvelle enveloppe dans PocketBase.
     * @param enveloppe L'enveloppe à créer
     * @return Result contenant l'enveloppe créée avec son vrai ID
     */
    override suspend fun creerEnveloppe(enveloppe: Enveloppe): Result<Enveloppe> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()
            val url = "$urlBase/api/collections/${Collections.ENVELOPPES}/records"
            
            // Créer les données à envoyer (sans l'ID temporaire)
            val donnees = mapOf(
                "utilisateur_id" to utilisateurId,
                "nom" to enveloppe.nom,
                "categorieId" to enveloppe.categorieId, // IMPORTANT: Lien vers la catégorie
                "est_archive" to enveloppe.estArchive,
                "ordre" to enveloppe.ordre,
                "objectif_montant" to enveloppe.objectifMontant,
                "objectif_type" to enveloppe.objectifType.toString(),
                "objectif_date" to enveloppe.objectifDate,
                "objectif_jour" to enveloppe.objectifJour
            )
            
            val json = gson.toJson(donnees)
            println("[DEBUG] Création enveloppe: $json")
            
            val body = json.toRequestBody("application/json".toMediaType())
            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()

            val reponse = httpClient.newCall(requete).execute()
            
            if (!reponse.isSuccessful) {
                val errorBody = reponse.body?.string()
                throw Exception("Erreur PocketBase: ${reponse.code} - $errorBody")
            }
            
            // Récupérer l'enveloppe créée avec son vrai ID
            val responseBody = reponse.body?.string() ?: throw Exception("Réponse vide")
            val enveloppeCreee = gson.fromJson(responseBody, Enveloppe::class.java)
            
            println("[DEBUG] Enveloppe créée: ID=${enveloppeCreee.id}, nom=${enveloppeCreee.nom}, categorieId=${enveloppeCreee.categorieId}")
            Result.success(enveloppeCreee)
            
        } catch (e: Exception) {
            println("[ERROR] Erreur création enveloppe: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Met à jour une enveloppe existante.
     * @param enveloppe L'enveloppe à mettre à jour
     * @return Result indiquant le succès ou l'erreur
     */
    override suspend fun mettreAJourEnveloppe(enveloppe: Enveloppe): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: throw Exception("Token manquant")
            val urlBase = client.obtenirUrlBaseActive()
            
            // Vérifier que l'ID n'est pas temporaire
            if (enveloppe.id.startsWith("temp_")) {
                throw Exception("Impossible de mettre à jour une enveloppe avec un ID temporaire")
            }
            
            val donnees = mapOf(
                "nom" to enveloppe.nom,
                "categorieId" to enveloppe.categorieId,
                "est_archive" to enveloppe.estArchive,
                "ordre" to enveloppe.ordre,
                "objectif_type" to enveloppe.objectifType.toString(),
                "objectif_montant" to enveloppe.objectifMontant,
                "objectif_date" to enveloppe.objectifDate,
                "objectif_jour" to enveloppe.objectifJour
            )
            
            val json = gson.toJson(donnees)
            val url = "$urlBase/api/collections/${Collections.ENVELOPPES}/records/${enveloppe.id}"
            val body = json.toRequestBody("application/json".toMediaType())
            
            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .patch(body)
                .build()
                
            val response = httpClient.newCall(requete).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw Exception("Erreur PocketBase: ${response.code} - $errorBody")
            }
            
            println("[DEBUG] Enveloppe mise à jour: ${enveloppe.id}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            println("[ERROR] Erreur mise à jour enveloppe: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Supprime une enveloppe de PocketBase.
     * @param id L'ID de l'enveloppe à supprimer
     * @return Result indiquant le succès ou l'erreur
     */
    override suspend fun supprimerEnveloppe(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: throw Exception("Token manquant")
            val urlBase = client.obtenirUrlBaseActive()
            val url = "$urlBase/api/collections/${Collections.ENVELOPPES}/records/$id"
            
            println("[DEBUG] Suppression enveloppe ID: $id")
            
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
            
            println("[DEBUG] Enveloppe supprimée avec succès")
            Result.success(Unit)
            
        } catch (e: Exception) {
            println("[ERROR] Erreur suppression enveloppe: ${e.message}")
            Result.failure(e)
        }
    }

    // ===== MÉTHODES POUR LES ALLOCATIONS =====
    
    override suspend fun recupererAllocationsPourMois(mois: Date): Result<List<AllocationMensuelle>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId'", "UTF-8")
            val url = "$urlBase/api/collections/${Collections.ALLOCATIONS}/records?filter=$filtreEncode&perPage=100"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la récupération des allocations: ${reponse.code}")
            }

            val corpsReponse = reponse.body!!.string()
            val typeReponse = TypeToken.getParameterized(ListeResultats::class.java, AllocationMensuelle::class.java).type
            val resultatPagine: ListeResultats<AllocationMensuelle> = gson.fromJson(corpsReponse, typeReponse)

            Result.success(resultatPagine.items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mettreAJourAllocation(allocation: AllocationMensuelle): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val corpsJson = gson.toJson(allocation)
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            val requete = Request.Builder()
                .url("$urlBase/api/collections/${Collections.ALLOCATIONS}/records/${allocation.id}")
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

    override suspend fun recupererOuCreerAllocation(enveloppeId: String, mois: Date): Result<AllocationMensuelle> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            // Chercher allocation existante
            val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId' && enveloppe_id = '$enveloppeId'", "UTF-8")
            val url = "$urlBase/api/collections/${Collections.ALLOCATIONS}/records?filter=$filtreEncode&perPage=1"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (reponse.isSuccessful) {
                val corpsReponse = reponse.body!!.string()
                val typeReponse = TypeToken.getParameterized(ListeResultats::class.java, AllocationMensuelle::class.java).type
                val resultatPagine: ListeResultats<AllocationMensuelle> = gson.fromJson(corpsReponse, typeReponse)

                if (resultatPagine.items.isNotEmpty()) {
                    return@withContext Result.success(resultatPagine.items.first())
                }
            }

            // Créer nouvelle allocation si aucune trouvée
            val nouvelleAllocation = AllocationMensuelle(
                id = UUID.randomUUID().toString(),
                utilisateurId = utilisateurId,
                enveloppeId = enveloppeId,
                mois = mois,
                solde = 0.0,
                alloue = 0.0,
                depense = 0.0,
                compteSourceId = null,
                collectionCompteSource = null
            )

            val corpsJson = gson.toJson(nouvelleAllocation)
            val requeteCreation = Request.Builder()
                .url("$urlBase/api/collections/${Collections.ALLOCATIONS}/records")
                .addHeader("Authorization", "Bearer $token")
                .post(corpsJson.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(requeteCreation).execute().use { reponseCreation ->
                if (!reponseCreation.isSuccessful) {
                    return@withContext Result.failure(Exception("Échec de la création de l'allocation: ${reponseCreation.body?.string()}"))
                }
            }

            Result.success(nouvelleAllocation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Classe pour désérialiser les réponses paginées de PocketBase
     */
    private data class ListeResultats<T>(
        val page: Int,
        val perPage: Int,
        val totalItems: Int,
        val totalPages: Int,
        val items: List<T>
    )
}