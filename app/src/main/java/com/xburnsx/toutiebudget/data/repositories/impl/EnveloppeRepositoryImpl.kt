// chemin/simule: /data/repositories/impl/EnveloppeRepositoryImpl.kt
package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
import com.xburnsx.toutiebudget.data.modeles.Enveloppe
import com.xburnsx.toutiebudget.data.modeles.TypeObjectif
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

    override suspend fun recupererToutesLesEnveloppes(): Result<List<Enveloppe>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            // Filtre pour ne récupérer que les enregistrements de l'utilisateur connecté
            val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId'", "UTF-8")
            val url = "$urlBase/api/collections/${Collections.ENVELOPPES}/records?filter=$filtreEncode&perPage=100"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la récupération des enveloppes: ${reponse.code} ${reponse.body?.string()}")
            }

            val corpsReponse = reponse.body!!.string()
            println("[DEBUG] Réponse brute de PocketBase pour enveloppes: $corpsReponse")
            val typeReponse = TypeToken.getParameterized(ListeResultats::class.java, Enveloppe::class.java).type
            val resultatPagine: ListeResultats<Enveloppe> = gson.fromJson(corpsReponse, typeReponse)

            val enveloppes = resultatPagine.items.filter { !it.estArchive }.sortedBy { it.ordre }
            println("[DEBUG] Enveloppes parsées: ${enveloppes.map { "${it.nom} (categorieId: ${it.categorieId})" }}")
            Result.success(enveloppes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recupererAllocationsPourMois(mois: Date): Result<List<AllocationMensuelle>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            // Filtre pour ne récupérer que les allocations de l'utilisateur connecté
            val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId'", "UTF-8")
            val url = "$urlBase/api/collections/${Collections.ALLOCATIONS}/records?filter=$filtreEncode&perPage=100"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la récupération des allocations: ${reponse.code} ${reponse.body?.string()}")
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

    override suspend fun creerEnveloppe(enveloppe: Enveloppe): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé pour la création."))

            // Créer un map avec les champs nécessaires pour PocketBase
            val dataMap = mapOf(
                "utilisateur_id" to utilisateurId,
                "nom" to enveloppe.nom,
                "categorieId" to enveloppe.categorieId,
                "est_archive" to enveloppe.estArchive,
                "ordre" to enveloppe.ordre,
                // Inclure les objectifs s'ils sont définis
                "objectif_montant" to enveloppe.objectifMontant,
                "objectif_type" to enveloppe.objectifType.toString(),
                "objectif_date" to enveloppe.objectifDate,
                "objectif_jour" to enveloppe.objectifJour
            )
            
            val corpsJson = gson.toJson(dataMap)
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()
            val url = "$urlBase/api/collections/${Collections.ENVELOPPES}/records"
            
            println("[DEBUG] Création d'enveloppe avec les données: $corpsJson")
            println("[DEBUG] URL: $url")

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(corpsJson.toRequestBody("application/json".toMediaType()))
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                val errorBody = reponse.body?.string()
                println("[ERROR] Échec de la création d'enveloppe: $errorBody")
                return@withContext Result.failure(Exception("Échec de la création: $errorBody"))
            }
            
            // Récupérer l'ID de l'enveloppe nouvellement créée
            val responseBody = reponse.body?.string()
            if (responseBody != null) {
                try {
                    val enveloppeCreee = gson.fromJson(responseBody, EnveloppeResponse::class.java)
                    println("[DEBUG] Enveloppe créée avec succès, ID: ${enveloppeCreee.id}, nom: ${enveloppeCreee.nom}, categorieId: ${enveloppeCreee.categorieId}")
                } catch (e: Exception) {
                    println("[DEBUG] Impossible de parser la réponse: $responseBody")
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            println("[ERROR] Erreur lors de la création de l'enveloppe: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    // Classe pour désérialiser la réponse de création
    data class EnveloppeResponse(val id: String, val nom: String, val categorieId: String)

    override suspend fun recupererOuCreerAllocation(enveloppeId: String, mois: Date): Result<AllocationMensuelle> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            // D'abord, essayer de récupérer l'allocation existante
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            // Filtre pour trouver l'allocation existante
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

            // Si aucune allocation n'existe, en créer une nouvelle
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

    override suspend fun mettreAJourEnveloppe(enveloppe: Enveloppe): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
            val urlBase = client.obtenirUrlBaseActive()
            
            // Vérifier si l'ID est temporaire (pour les enveloppes créées localement mais pas encore synchronisées)
            if (enveloppe.id.startsWith("temp_")) {
                throw Exception("Impossible de mettre à jour une enveloppe avec un ID temporaire")
            }
            
            // Créer une map avec les champs à mettre à jour
            // Cela permet d'éviter les problèmes de sérialisation et de n'envoyer que ce qui est nécessaire
            val dataMap = mapOf(
                "nom" to enveloppe.nom,
                "categorieId" to enveloppe.categorieId,
                "est_archive" to enveloppe.estArchive,
                "ordre" to enveloppe.ordre,
                "objectif_type" to enveloppe.objectifType.toString(),
                "objectif_montant" to enveloppe.objectifMontant,
                "objectif_date" to enveloppe.objectifDate,
                "objectif_jour" to enveloppe.objectifJour
            )
            
            val json = gson.toJson(dataMap)
            val url = "$urlBase/api/collections/enveloppes/records/${enveloppe.id}"
            val body = json.toRequestBody("application/json".toMediaType())
            
            println("[DEBUG] Mise à jour de l'enveloppe ${enveloppe.id} avec les données: $json")
            println("[DEBUG] URL: $url")
            
            val requete = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .patch(body)
                .build()
                
            val response = httpClient.newCall(requete).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw Exception("Erreur PocketBase: ${response.code} - $errorBody")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            println("[ERROR] Erreur lors de la mise à jour de l'enveloppe: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    override suspend fun supprimerEnveloppe(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
            val urlBase = client.obtenirUrlBaseActive()
            val url = "$urlBase/api/collections/${Collections.ENVELOPPES}/records/$id"
            
            println("[DEBUG] Suppression de l'enveloppe avec ID: $id")
            println("[DEBUG] URL: $url")
            
            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()
                
            val response = httpClient.newCall(requete).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                println("[ERROR] Échec de la suppression de l'enveloppe: $errorBody")
                throw Exception("Erreur PocketBase: ${response.code} - $errorBody")
            }
            
            println("[DEBUG] Enveloppe supprimée avec succès: $id")
            Result.success(Unit)
        } catch (e: Exception) {
            println("[ERROR] Erreur lors de la suppression de l'enveloppe: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
