// chemin/simule: /data/repositories/impl/EnveloppeRepositoryImpl.kt
// Dépendances: PocketBaseClient, Gson, SafeDateAdapter, OkHttp3, Modèles

package com.xburnsx.toutiebudget.data.repositories.impl

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
     * ✅ CORRECTION FINALE : Convertit un TypeObjectif vers les valeurs exactes de PocketBase
     */
    private fun typeObjectifVersPocketBase(type: TypeObjectif): String {
        return when (type) {
            TypeObjectif.Aucun -> "Aucun"                    // ✅ EXACTEMENT comme dans PocketBase
            TypeObjectif.Mensuel -> "Mensuel"               // ✅ EXACTEMENT comme dans PocketBase
            TypeObjectif.Bihebdomadaire -> "Bihebdomadaire"  // ✅ EXACTEMENT comme dans PocketBase
            TypeObjectif.Echeance -> "Echeance"             // ✅ EXACTEMENT comme dans PocketBase
            TypeObjectif.Annuel -> "Annuel"                 // ✅ EXACTEMENT comme dans PocketBase
        }
    }

    /**
     * ✅ CORRECTION FINALE : Convertit les valeurs PocketBase vers TypeObjectif
     */
    private fun pocketBaseVersTypeObjectif(str: String?): TypeObjectif {
        return when (str) {
            "Aucun" -> TypeObjectif.Aucun
            "Mensuel" -> TypeObjectif.Mensuel
            "Bihebdomadaire" -> TypeObjectif.Bihebdomadaire
            "Echeance" -> TypeObjectif.Echeance
            "Annuel" -> TypeObjectif.Annuel
            else -> {
                println("[DEBUG] Valeur objectif_type inconnue: '$str', utilisation de AUCUN par défaut")
                TypeObjectif.Aucun
            }
        }
    }

    /**
     * Récupère toutes les enveloppes de l'utilisateur connecté.
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
            val typeReponse = TypeToken.getParameterized(ListeResultats::class.java, Map::class.java).type
            val resultatPagine: ListeResultats<Map<String, Any>> = gson.fromJson(corpsReponse, typeReponse)

            val enveloppes = resultatPagine.items.map { item ->
                Enveloppe(
                    id = item["id"] as? String ?: "",
                    utilisateurId = item["utilisateur_id"] as? String ?: "",
                    nom = item["nom"] as? String ?: "",
                    categorieId = item["categorieId"] as? String ?: "",
                    estArchive = item["est_archive"] as? Boolean ?: false,
                    ordre = (item["ordre"] as? Double)?.toInt() ?: 0,
                    objectifType = pocketBaseVersTypeObjectif(item["objectif_type"] as? String),
                    objectifMontant = item["objectif_montant"] as? Double ?: 0.0,
                    objectifDate = null,
                    objectifJour = (item["objectif_jour"] as? Double)?.toInt()
                )
            }.filter { !it.estArchive }

            println("[DEBUG] Enveloppes récupérées: ${enveloppes.size}")
            Result.success(enveloppes)
            
        } catch (e: Exception) {
            println("[ERROR] Erreur récupération enveloppes: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * ✅ CRÉATION CORRIGÉE : Crée une nouvelle enveloppe dans PocketBase
     */
    override suspend fun creerEnveloppe(enveloppe: Enveloppe): Result<Enveloppe> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()
            val url = "$urlBase/api/collections/${Collections.ENVELOPPES}/records"
            
            // ✅ DONNÉES AVEC LES BONNES VALEURS POCKETBASE
            val donnees = mapOf(
                "utilisateur_id" to utilisateurId,
                "nom" to enveloppe.nom,
                "categorieId" to enveloppe.categorieId,
                "est_archive" to enveloppe.estArchive,
                "ordre" to enveloppe.ordre,
                "objectif_type" to typeObjectifVersPocketBase(enveloppe.objectifType), // ✅ CORRECTION ICI
                "objectif_montant" to enveloppe.objectifMontant,
                "objectif_date" to enveloppe.objectifDate,
                "objectif_jour" to enveloppe.objectifJour
            )
            
            val json = gson.toJson(donnees)
            println("[DEBUG] ✅ Création enveloppe CORRIGÉE: $json")
            
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
            
            val responseBody = reponse.body?.string() ?: throw Exception("Réponse vide")
            val itemData = gson.fromJson(responseBody, Map::class.java) as Map<String, Any>
            
            val enveloppeCreee = Enveloppe(
                id = itemData["id"] as? String ?: "",
                utilisateurId = itemData["utilisateur_id"] as? String ?: "",
                nom = itemData["nom"] as? String ?: "",
                categorieId = itemData["categorieId"] as? String ?: "",
                estArchive = itemData["est_archive"] as? Boolean ?: false,
                ordre = (itemData["ordre"] as? Double)?.toInt() ?: 0,
                objectifType = pocketBaseVersTypeObjectif(itemData["objectif_type"] as? String),
                objectifMontant = itemData["objectif_montant"] as? Double ?: 0.0,
                objectifDate = null,
                objectifJour = (itemData["objectif_jour"] as? Double)?.toInt()
            )
            
            println("[DEBUG] ✅ Enveloppe créée avec succès: ID=${enveloppeCreee.id}, nom=${enveloppeCreee.nom}")
            Result.success(enveloppeCreee)
            
        } catch (e: Exception) {
            println("[ERROR] Erreur création enveloppe: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * ✅ MISE À JOUR CORRIGÉE : Met à jour une enveloppe existante
     */
    override suspend fun mettreAJourEnveloppe(enveloppe: Enveloppe): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: throw Exception("Token manquant")
            val urlBase = client.obtenirUrlBaseActive()
            
            if (enveloppe.id.startsWith("temp_")) {
                throw Exception("Impossible de mettre à jour une enveloppe avec un ID temporaire")
            }
            
            val donnees = mapOf(
                "nom" to enveloppe.nom,
                "categorieId" to enveloppe.categorieId,
                "est_archive" to enveloppe.estArchive,
                "ordre" to enveloppe.ordre,
                "objectif_type" to typeObjectifVersPocketBase(enveloppe.objectifType), // ✅ CORRECTION ICI
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
            
            println("[DEBUG] ✅ Enveloppe mise à jour: ${enveloppe.id}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            println("[ERROR] Erreur mise à jour enveloppe: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Supprime une enveloppe de PocketBase.
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

    private data class ListeResultats<T>(
        val page: Int,
        val perPage: Int,
        val totalItems: Int,
        val totalPages: Int,
        val items: List<T>
    )
}