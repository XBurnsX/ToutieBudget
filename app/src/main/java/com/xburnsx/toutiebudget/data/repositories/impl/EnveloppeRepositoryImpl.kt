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
import okhttp3.MediaType.Companion.toMediaType
import com.xburnsx.toutiebudget.utils.SafeDateAdapter

/**
 * Implémentation du repository des enveloppes avec PocketBase.
 * Gère la création, récupération et mise à jour des enveloppes et allocations.
 */
class EnveloppeRepositoryImpl : EnveloppeRepository {
    
    private val client = PocketBaseClient
    private val gson = com.google.gson.GsonBuilder()
        .registerTypeAdapter(Date::class.java, SafeDateAdapter())
        .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
    private val httpClient = okhttp3.OkHttpClient()

    // Noms des collections dans PocketBase
    private object Collections {
        const val ENVELOPPES = "enveloppes"
        const val ALLOCATIONS = "allocations_mensuelles"
    }

    /**
     * Convertit un TypeObjectif vers les valeurs exactes de PocketBase
     */
    private fun typeObjectifVersPocketBase(type: TypeObjectif): String {
        return when (type) {
            TypeObjectif.Aucun -> "Aucun"
            TypeObjectif.Mensuel -> "Mensuel"
            TypeObjectif.Bihebdomadaire -> "Bihebdomadaire"
            TypeObjectif.Echeance -> "Echeance"
            TypeObjectif.Annuel -> "Annuel"
        }
    }

    /**
     * Convertit les valeurs PocketBase vers TypeObjectif
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
     * Récupère les allocations mensuelles pour un mois donné.
     */
    override suspend fun recupererAllocationsPourMois(mois: Date): Result<List<AllocationMensuelle>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            val dateFormatee = formatDate(mois)
            val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId' && mois = '$dateFormatee'", "UTF-8")
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
            val allocations = deserialiserListeAllocations(corpsReponse)

            Result.success(allocations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Met à jour une allocation mensuelle.
     */
    override suspend fun mettreAJourAllocation(allocation: AllocationMensuelle): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            val corpsJson = gson.toJson(allocation)
            val url = "$urlBase/api/collections/${Collections.ALLOCATIONS}/records/${allocation.id}"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .patch(corpsJson.toRequestBody("application/json".toMediaType()))
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la mise à jour de l'allocation: ${reponse.code}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Crée une nouvelle enveloppe.
     */
    override suspend fun creerEnveloppe(enveloppe: Enveloppe): Result<Enveloppe> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()
            val url = "$urlBase/api/collections/${Collections.ENVELOPPES}/records"
            
            val donnees = mapOf(
                "utilisateur_id" to utilisateurId,
                "nom" to enveloppe.nom,
                "categorieId" to enveloppe.categorieId,
                "est_archive" to enveloppe.estArchive,
                "ordre" to enveloppe.ordre,
                "objectif_type" to typeObjectifVersPocketBase(enveloppe.objectifType),
                "objectif_montant" to enveloppe.objectifMontant,
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
            
            val responseBody = reponse.body?.string() ?: ""
            val enveloppeCreee = deserialiserEnveloppe(responseBody)
                ?: throw Exception("Erreur lors de la désérialisation de l'enveloppe créée")
            
            Result.success(enveloppeCreee)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Récupère ou crée une allocation pour une enveloppe et un mois donnés.
     */
    override suspend fun recupererOuCreerAllocation(enveloppeId: String, mois: Date): Result<AllocationMensuelle> = withContext(Dispatchers.IO) {
        try {
            // Essayer de récupérer l'allocation existante
            val resultAllocation = recupererAllocationMensuelle(enveloppeId, mois)
            if (resultAllocation.isSuccess) {
                val allocation = resultAllocation.getOrNull()
                if (allocation != null) {
                    return@withContext Result.success(allocation)
                }
            }

            // Créer une nouvelle allocation si elle n'existe pas
            val nouvelleAllocation = AllocationMensuelle(
                id = "",
                utilisateurId = "",
                enveloppeId = enveloppeId,
                mois = mois,
                solde = 0.0,
                alloue = 0.0,
                depense = 0.0,
                compteSourceId = null,
                collectionCompteSource = null
            )

            creerAllocationMensuelle(nouvelleAllocation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Met à jour une enveloppe existante.
     */
    override suspend fun mettreAJourEnveloppe(enveloppe: Enveloppe): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            val corpsJson = gson.toJson(enveloppe)
            val url = "$urlBase/api/collections/${Collections.ENVELOPPES}/records/${enveloppe.id}"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .patch(corpsJson.toRequestBody("application/json".toMediaType()))
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la mise à jour de l'enveloppe: ${reponse.code}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Supprime une enveloppe par son ID.
     */
    override suspend fun supprimerEnveloppe(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            val url = "$urlBase/api/collections/${Collections.ENVELOPPES}/records/$id"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la suppression de l'enveloppe: ${reponse.code}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ===== NOUVELLES MÉTHODES POUR LES TRANSACTIONS =====

    /**
     * Ajoute une dépense à une allocation mensuelle.
     * Soustrait le montant du solde et l'ajoute aux dépenses.
     */
    override suspend fun ajouterDepenseAllocation(allocationMensuelleId: String, montantDepense: Double): Result<Unit> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.failure(Exception("Utilisateur non connecté"))
        }
        
        try {
            val token = client.obtenirToken() 
                ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            // 1. Récupérer l'allocation actuelle
            val resultAllocation = recupererAllocationParId(allocationMensuelleId)
            if (resultAllocation.isFailure) {
                throw resultAllocation.exceptionOrNull() ?: Exception("Impossible de récupérer l'allocation")
            }
            
            val allocation = resultAllocation.getOrNull() 
                ?: throw Exception("Allocation non trouvée")
            
            // 2. Calculer les nouveaux montants
            val nouveauSolde = allocation.solde - montantDepense  // Soustraction du solde
            val nouvelleDépense = allocation.depense + montantDepense  // Addition aux dépenses
            
            // 3. Préparer les données de mise à jour
            val donneesUpdate = mapOf(
                "solde" to nouveauSolde,
                "depense" to nouvelleDépense
            )
            val corpsRequete = gson.toJson(donneesUpdate)
            
            val url = "$urlBase/api/collections/${Collections.ALLOCATIONS}/records/$allocationMensuelleId"
            
            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .patch(corpsRequete.toRequestBody("application/json".toMediaType()))
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la mise à jour de l'allocation: ${reponse.code} ${reponse.body?.string()}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Récupère une allocation mensuelle spécifique.
     */
    override suspend fun recupererAllocationMensuelle(enveloppeId: String, mois: Date): Result<AllocationMensuelle?> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(null)
        }
        
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé"))

            val token = client.obtenirToken() 
                ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            // Formater la date pour le filtre
            val dateFormatee = formatDate(mois)
            
            // Filtre pour trouver l'allocation spécifique
            val filtreEncode = URLEncoder.encode(
                "utilisateur_id = '$utilisateurId' && enveloppe_id = '$enveloppeId' && mois = '$dateFormatee'", 
                "UTF-8"
            )
            val url = "$urlBase/api/collections/${Collections.ALLOCATIONS}/records?filter=$filtreEncode&perPage=1"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la récupération de l'allocation: ${reponse.code}")
            }

            val corpsReponse = reponse.body!!.string()
            val allocations = deserialiserListeAllocations(corpsReponse)
            
            Result.success(allocations.firstOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Crée une nouvelle allocation mensuelle.
     */
    override suspend fun creerAllocationMensuelle(allocation: AllocationMensuelle): Result<AllocationMensuelle> = withContext(Dispatchers.IO) {
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé"))

            val token = client.obtenirToken() 
                ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            val donnees = mapOf(
                "utilisateur_id" to utilisateurId,
                "enveloppe_id" to allocation.enveloppeId,
                "mois" to formatDate(allocation.mois),
                "solde" to allocation.solde,
                "alloue" to allocation.alloue,
                "depense" to allocation.depense,
                "compte_source_id" to (allocation.compteSourceId ?: ""),
                "collection_compte_source" to (allocation.collectionCompteSource ?: "")
            )

            val corpsRequete = gson.toJson(donnees)
            val url = "$urlBase/api/collections/${Collections.ALLOCATIONS}/records"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(corpsRequete.toRequestBody("application/json".toMediaType()))
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la création de l'allocation: ${reponse.code} ${reponse.body?.string()}")
            }

            val corpsReponse = reponse.body!!.string()
            val allocationCreee = deserialiserAllocation(corpsReponse)
                ?: throw Exception("Erreur lors de la désérialisation de l'allocation créée")

            Result.success(allocationCreee)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Récupère une allocation par son ID.
     */
    private suspend fun recupererAllocationParId(allocationId: String): Result<AllocationMensuelle> {
        try {
            val token = client.obtenirToken() 
                ?: return Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()
            
            val url = "$urlBase/api/collections/${Collections.ALLOCATIONS}/records/$allocationId"
            
            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la récupération de l'allocation: ${reponse.code}")
            }

            val corpsReponse = reponse.body!!.string()
            val allocation = deserialiserAllocation(corpsReponse)
                ?: throw Exception("Erreur lors de la désérialisation de l'allocation")

            return Result.success(allocation)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // ===== MÉTHODES UTILITAIRES =====

    /**
     * Formate une date pour PocketBase (évite les conflits SimpleDateFormat).
     */
    private fun formatDate(date: Date): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }

    /**
     * Désérialise une enveloppe depuis JSON PocketBase.
     */
    private fun deserialiserEnveloppe(json: String): Enveloppe? {
        return try {
            val jsonObject = gson.fromJson(json, com.google.gson.JsonObject::class.java)
            
            Enveloppe(
                id = jsonObject.get("id")?.asString ?: "",
                utilisateurId = jsonObject.get("utilisateur_id")?.asString ?: "",
                nom = jsonObject.get("nom")?.asString ?: "",
                categorieId = jsonObject.get("categorieId")?.asString ?: "",
                estArchive = jsonObject.get("est_archive")?.asBoolean ?: false,
                ordre = jsonObject.get("ordre")?.asInt ?: 0,
                objectifType = pocketBaseVersTypeObjectif(jsonObject.get("objectif_type")?.asString),
                objectifMontant = jsonObject.get("objectif_montant")?.asDouble ?: 0.0,
                objectifDate = gson.fromJson(jsonObject.get("objectif_date"), Date::class.java),
                objectifJour = jsonObject.get("objectif_jour")?.asInt
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Désérialise une allocation depuis JSON PocketBase.
     */
    private fun deserialiserAllocation(json: String): AllocationMensuelle? {
        return try {
            val jsonObject = gson.fromJson(json, com.google.gson.JsonObject::class.java)
            
            AllocationMensuelle(
                id = jsonObject.get("id")?.asString ?: "",
                utilisateurId = jsonObject.get("utilisateur_id")?.asString ?: "",
                enveloppeId = jsonObject.get("enveloppe_id")?.asString ?: "",
                mois = gson.fromJson(jsonObject.get("mois"), Date::class.java) ?: Date(),
                solde = jsonObject.get("solde")?.asDouble ?: 0.0,
                alloue = jsonObject.get("alloue")?.asDouble ?: 0.0,
                depense = jsonObject.get("depense")?.asDouble ?: 0.0,
                compteSourceId = jsonObject.get("compte_source_id")?.asString?.takeIf { it.isNotBlank() },
                collectionCompteSource = jsonObject.get("collection_compte_source")?.asString?.takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Désérialise une liste d'allocations depuis JSON PocketBase.
     */
    private fun deserialiserListeAllocations(json: String): List<AllocationMensuelle> {
        return try {
            val jsonObject = gson.fromJson(json, com.google.gson.JsonObject::class.java)
            val itemsArray = jsonObject.getAsJsonArray("items")
            
            itemsArray.map { item ->
                val allocationJson = item.toString()
                deserialiserAllocation(allocationJson)
            }.filterNotNull()
        } catch (e: Exception) {
            emptyList()
        }
    }
}