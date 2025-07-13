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
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé"))

            val token = client.obtenirToken() 
                ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId'", "UTF-8")
            val url = "$urlBase/api/collections/${Collections.ENVELOPPES}/records?filter=$filtreEncode&perPage=500"

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
            val jsonObject = gson.fromJson(corpsReponse, com.google.gson.JsonObject::class.java)
            val itemsArray = jsonObject.getAsJsonArray("items")

            val enveloppes = itemsArray.map { item ->
                val itemObject = item.asJsonObject
                Enveloppe(
                    id = itemObject.get("id")?.asString ?: "",
                    utilisateurId = itemObject.get("utilisateur_id")?.asString ?: "",
                    nom = itemObject.get("nom")?.asString ?: "",
                    categorieId = itemObject.get("categorieId")?.asString ?: "",
                    estArchive = itemObject.get("est_archive")?.asBoolean ?: false,
                    ordre = (itemObject.get("ordre")?.asDouble)?.toInt() ?: 0,
                    objectifType = pocketBaseVersTypeObjectif(itemObject.get("objectif_type")?.asString),
                    objectifMontant = itemObject.get("objectif_montant")?.asDouble ?: 0.0,
                    objectifDate = null,
                    objectifJour = (itemObject.get("objectif_jour")?.asDouble)?.toInt()
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
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé"))

            val token = client.obtenirToken() 
                ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            // Formater la date pour le filtre - CORRECTION: utilise LIKE au lieu de =
            val dateFormatee = formatDate(mois)
            println("[DEBUG] === RECHERCHE ALLOCATIONS ===")
            println("[DEBUG] Date de recherche reçue: $mois")
            println("[DEBUG] Date formatée pour recherche: '$dateFormatee'")
            println("[DEBUG] recupererAllocationsPourMois: mois=$mois, dateFormatee=$dateFormatee")
            
            // CORRECTION: Utilise ~ (LIKE) au lieu de = pour être plus permissif avec les formats de date
            val filtreEncode = URLEncoder.encode(
                "utilisateur_id = '$utilisateurId' && mois ~ '$dateFormatee'", 
                "UTF-8"
            )
            val url = "$urlBase/api/collections/${Collections.ALLOCATIONS}/records?filter=$filtreEncode&perPage=500"
            println("[DEBUG] recupererAllocationsPourMois: URL=$url")

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                val erreur = "Erreur lors de la récupération des allocations: ${reponse.code} ${reponse.body?.string()}"
                println("[DEBUG] $erreur")
                throw Exception(erreur)
            }

            val corpsReponse = reponse.body!!.string()
            println("[DEBUG] Réponse recherche: ${corpsReponse.take(300)}...")
            println("[DEBUG] recupererAllocationsPourMois: réponse=${corpsReponse.take(200)}...")
            
            val allocations = deserialiserListeAllocations(corpsReponse)
            println("[DEBUG] recupererAllocationsPourMois: ${allocations.size} allocations trouvées")
            println("[DEBUG] Allocations trouvées: ${allocations.size}")
            allocations.forEach { allocation ->
                println("[DEBUG] - Allocation trouvée: id='${allocation.id}' mois='${formatDate(allocation.mois)}' enveloppeId='${allocation.enveloppeId}' solde=${allocation.solde}")
                println("[DEBUG]  - enveloppeId=${allocation.enveloppeId}, solde=${allocation.solde}, depense=${allocation.depense}")
            }
            println("[DEBUG] ===============================")

            Result.success(allocations)
        } catch (e: Exception) {
            println("[DEBUG] recupererAllocationsPourMois: erreur - ${e.message}")
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

    /**
     * Ajoute une dépense à une allocation mensuelle.
     * Soustrait le montant du solde et l'ajoute aux dépenses.
     */
    override suspend fun ajouterDepenseAllocation(allocationMensuelleId: String, montantDepense: Double): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = client.obtenirUrlBaseActive()

            println("[DEBUG] ajouterDepenseAllocation: début - allocationId=$allocationMensuelleId, montant=$montantDepense")
            
            // 1. Récupérer l'allocation actuelle
            println("[DEBUG] Récupération allocation actuelle")
            val allocation = recupererAllocationParId(allocationMensuelleId).getOrNull()
                ?: throw Exception("Allocation non trouvée")
            
            println("[DEBUG] Allocation trouvée: solde=${allocation.solde}, depense=${allocation.depense}")
            
            // 2. Calculer les nouveaux montants
            val nouveauSolde = allocation.solde - montantDepense  // Soustraction du solde
            val nouvelleDépense = allocation.depense + montantDepense  // Addition aux dépenses existantes
            
            println("[DEBUG] Nouveaux montants calculés: solde=$nouveauSolde, depense=$nouvelleDépense")
            println("[DEBUG] Vérification calcul: ${allocation.solde} - $montantDepense = $nouveauSolde")
            println("[DEBUG] Vérification calcul: ${allocation.depense} + $montantDepense = $nouvelleDépense")
            
            // 3. Préparer les données de mise à jour
            val donneesUpdate = mapOf(
                "solde" to nouveauSolde,
                "depense" to nouvelleDépense
            )
            val corpsRequete = gson.toJson(donneesUpdate)
            
            val url = "$urlBase/api/collections/${Collections.ALLOCATIONS}/records/$allocationMensuelleId"
            println("[DEBUG] URL mise à jour: $url")
            println("[DEBUG] Données envoyées: $donneesUpdate")
            
            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .patch(corpsRequete.toRequestBody("application/json".toMediaType()))
                .build()

            val reponse = httpClient.newCall(requete).execute()
            if (!reponse.isSuccessful) {
                val erreur = "Erreur lors de la mise à jour de l'allocation: ${reponse.code} ${reponse.body?.string()}"
                println("[DEBUG] $erreur")
                throw Exception(erreur)
            }

            val corpsReponse = reponse.body?.string() ?: ""
            println("[DEBUG] Réponse mise à jour: ${corpsReponse.take(200)}...")
            println("[DEBUG] ajouterDepenseAllocation: succès")
            Result.success(Unit)
        } catch (e: Exception) {
            println("[DEBUG] ajouterDepenseAllocation: erreur - ${e.message}")
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

            // === AJOUT DEBUG ===
            val moisFormate = formatDate(allocation.mois)
            println("[DEBUG] === CRÉATION ALLOCATION ===")
            println("[DEBUG] Date reçue: ${allocation.mois}")
            println("[DEBUG] Date formatée pour PocketBase: '$moisFormate'")
            println("[DEBUG] EnveloppeId: '${allocation.enveloppeId}'")
            println("[DEBUG] ================================")

            val donnees = mapOf(
                "utilisateur_id" to utilisateurId,
                "enveloppe_id" to allocation.enveloppeId,
                "mois" to moisFormate,
                "solde" to allocation.solde,
                "alloue" to allocation.alloue,
                "depense" to allocation.depense,
                "compte_source_id" to (allocation.compteSourceId ?: ""),
                "collection_compte_source" to (allocation.collectionCompteSource ?: "")
            )

            println("[DEBUG] Données envoyées à PocketBase: $donnees")

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
            println("[DEBUG] Réponse PocketBase: ${corpsReponse.take(300)}...")
            
            val allocationCreee = deserialiserAllocation(corpsReponse)
                ?: throw Exception("Erreur lors de la désérialisation de l'allocation créée")

            println("[DEBUG] === ALLOCATION CRÉÉE ===")
            println("[DEBUG] ID créé: '${allocationCreee.id}'")
            println("[DEBUG] Date stockée: ${allocationCreee.mois}")
            println("[DEBUG] Date stockée formatée: '${formatDate(allocationCreee.mois)}'")
            println("[DEBUG] ============================")

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
     * CORRECTION: Parse manuellement la date pour éviter les problèmes de désérialisation.
     */
    private fun deserialiserAllocation(json: String): AllocationMensuelle? {
        return try {
            val jsonObject = gson.fromJson(json, com.google.gson.JsonObject::class.java)
            
            // === CORRECTION DE LA DATE ===
            val moisString = jsonObject.get("mois")?.asString ?: ""
            println("[DEBUG] deserialiserAllocation: moisString reçu='$moisString'")
            
            // Parse manuellement la date au lieu d'utiliser Gson
            val mois = if (moisString.isNotEmpty()) {
                try {
                    // PocketBase renvoie "2025-07-01 00:00:00.000Z" ou "2025-07-01 00:00:00"
                    val dateClean = moisString.replace(".000Z", "").trim()
                    println("[DEBUG] deserialiserAllocation: dateClean='$dateClean'")
                    
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    val dateParsee = sdf.parse(dateClean)
                    println("[DEBUG] deserialiserAllocation: dateParsee=$dateParsee")
                    dateParsee
                } catch (e: Exception) {
                    println("[ERROR] deserialiserAllocation: erreur parsing date: ${e.message}")
                    Date()
                }
            } else {
                Date()
            }
            
            AllocationMensuelle(
                id = jsonObject.get("id")?.asString ?: "",
                utilisateurId = jsonObject.get("utilisateur_id")?.asString ?: "",
                enveloppeId = jsonObject.get("enveloppe_id")?.asString ?: "",
                mois = mois, // ← Utilise la date parsée manuellement
                solde = jsonObject.get("solde")?.asDouble ?: 0.0,
                alloue = jsonObject.get("alloue")?.asDouble ?: 0.0,
                depense = jsonObject.get("depense")?.asDouble ?: 0.0,
                compteSourceId = jsonObject.get("compte_source_id")?.asString?.takeIf { it.isNotBlank() },
                collectionCompteSource = jsonObject.get("collection_compte_source")?.asString?.takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            println("[ERROR] deserialiserAllocation: ${e.message}")
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