/**
 * Chemin: app/src/main/java/com/xburnsx/toutiebudget/data/repositories/impl/AllocationMensuelleRepositoryImpl.kt
 * Dépendances: PocketBaseClient, UrlResolver, AllocationMensuelle, Gson, OkHttp
 */

 package com.xburnsx.toutiebudget.data.repositories.impl

 import com.google.gson.Gson
 import com.google.gson.JsonParser
 import com.xburnsx.toutiebudget.data.modeles.AllocationMensuelle
 import com.xburnsx.toutiebudget.data.repositories.AllocationMensuelleRepository
 import com.xburnsx.toutiebudget.di.PocketBaseClient
 import com.xburnsx.toutiebudget.di.UrlResolver
 import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
 import kotlinx.coroutines.Dispatchers
 import kotlinx.coroutines.withContext
 import okhttp3.MediaType.Companion.toMediaType
 import okhttp3.OkHttpClient
 import okhttp3.Request
 import okhttp3.RequestBody.Companion.toRequestBody
 import java.text.SimpleDateFormat
 import java.util.*
 
 /**
  * Implémentation du repository d'allocations mensuelles.
  * Effectue des appels REST à PocketBase pour gérer les allocations.
  * CORRECTION : Gère maintenant les doublons d'allocations automatiquement.
  */
 class AllocationMensuelleRepositoryImpl : AllocationMensuelleRepository {
 
     private val client = PocketBaseClient
     private val gson = Gson()
     private val httpClient = OkHttpClient()
     
     private companion object {
         const val COLLECTION = "allocations_mensuelles"
         private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
     }
 
     private val formateurDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
 
     /**
      * Récupère une allocation mensuelle par son ID.
      */
     override suspend fun getAllocationById(id: String): AllocationMensuelle? = withContext(Dispatchers.IO) {
         try {
             val token = client.obtenirToken() ?: return@withContext null
             val urlBase = UrlResolver.obtenirUrlActive()
             
             val requete = Request.Builder()
                 .url("$urlBase/api/collections/$COLLECTION/records/$id")
                 .addHeader("Authorization", "Bearer $token")
                 .get()
                 .build()
                 
             val reponse = httpClient.newCall(requete).execute()
             if (!reponse.isSuccessful) return@withContext null
             
             val corpsReponse = reponse.body?.string() ?: return@withContext null
             deserialiserAllocation(corpsReponse)
         } catch (e: Exception) {
             null
         }
     }
 
     /**
      * Met à jour une allocation mensuelle (version simplifiée).
      */
     override suspend fun mettreAJourAllocation(
         id: String,
         nouveauSolde: Double,
         nouvelleDepense: Double
     ) = withContext(Dispatchers.IO) {
         try {
             val token = client.obtenirToken() ?: return@withContext
             val urlBase = UrlResolver.obtenirUrlActive()
             
             val bodyJson = "{\"solde\":$nouveauSolde,\"depense\":$nouvelleDepense}"
             val requete = Request.Builder()
                 .url("$urlBase/api/collections/$COLLECTION/records/$id")
                 .addHeader("Authorization", "Bearer $token")
                 .patch(bodyJson.toRequestBody("application/json".toMediaType()))
                 .build()

             httpClient.newCall(requete).execute().close()
         } catch (e: Exception) {

         }
     }
 
     /**
      * Met à jour une allocation mensuelle complète.
      */
     override suspend fun mettreAJourAllocation(allocation: AllocationMensuelle) = withContext(Dispatchers.IO) {
         try {
             val token = client.obtenirToken() ?: return@withContext
             val urlBase = UrlResolver.obtenirUrlActive()
             
             val bodyJson = gson.toJson(allocation)
             val requete = Request.Builder()
                 .url("$urlBase/api/collections/$COLLECTION/records/${allocation.id}")
                 .addHeader("Authorization", "Bearer $token")
                 .put(bodyJson.toRequestBody("application/json".toMediaType()))
                 .build()
                 
             httpClient.newCall(requete).execute().close()
         } catch (e: Exception) {

         }
     }
 
     /**
      * Met à jour le compte source d'une allocation.
      */
     override suspend fun mettreAJourCompteSource(
         id: String,
         compteSourceId: String,
         collectionCompteSource: String
     ) = withContext(Dispatchers.IO) {
         try {
             val token = client.obtenirToken() ?: return@withContext
             val urlBase = UrlResolver.obtenirUrlActive()
             
             val bodyJson = "{\"compte_source_id\":\"$compteSourceId\",\"collection_compte_source\":\"$collectionCompteSource\"}"
             val requete = Request.Builder()
                 .url("$urlBase/api/collections/$COLLECTION/records/$id")
                 .addHeader("Authorization", "Bearer $token")
                 .patch(bodyJson.toRequestBody("application/json".toMediaType()))
                 .build()
                 
             httpClient.newCall(requete).execute().close()
         } catch (e: Exception) {

         }
     }
 
     /**
      * CORRECTION PRINCIPALE : Récupère OU crée une allocation mensuelle unique par enveloppe/mois.
      * Résout le problème de duplication des allocations.
      */
     override suspend fun getOrCreateAllocationMensuelle(
         enveloppeId: String, 
         mois: Date
     ): AllocationMensuelle = withContext(Dispatchers.IO) {
         
         // 1. Calculer le premier jour du mois pour la recherche
         val calendrier = Calendar.getInstance().apply {
             time = mois
             set(Calendar.DAY_OF_MONTH, 1)
             set(Calendar.HOUR_OF_DAY, 0)
             set(Calendar.MINUTE, 0)
             set(Calendar.SECOND, 0)
             set(Calendar.MILLISECOND, 0)
         }
         val premierJourMois = calendrier.time
 
         // 2. Chercher les allocations existantes pour cette enveloppe et ce mois
         val allocationsExistantes = recupererAllocationsPourEnveloppeEtMois(enveloppeId, premierJourMois)
         
         when {
             // Cas 1: Aucune allocation trouvée -> Créer une nouvelle
             allocationsExistantes.isEmpty() -> {

                 creerNouvelleAllocation(enveloppeId, premierJourMois)
             }
             
             // Cas 2: Une seule allocation trouvée -> La retourner
             allocationsExistantes.size == 1 -> {

                 allocationsExistantes.first()
             }
             
             // Cas 3: PROBLÈME - Plusieurs allocations trouvées -> Fusionner et nettoyer
             else -> {
                 fusionnerEtNettoyerAllocations(allocationsExistantes, enveloppeId, premierJourMois)
             }
         }
     }
 
     /**
      * Récupère toutes les allocations pour une enveloppe et un mois donnés.
      */
     private suspend fun recupererAllocationsPourEnveloppeEtMois(
         enveloppeId: String, 
         premierJourMois: Date
     ): List<AllocationMensuelle> = withContext(Dispatchers.IO) {
         
         try {
             val utilisateurId = client.obtenirUtilisateurConnecte()?.id 
                 ?: throw Exception("Utilisateur non connecté")
             val token = client.obtenirToken() 
                 ?: throw Exception("Token manquant")
             val urlBase = UrlResolver.obtenirUrlActive()
 
             val moisIso = DATE_FORMAT.format(premierJourMois)
             
             // Filtre précis pour cette enveloppe ET ce mois
             val filtre = java.net.URLEncoder.encode("enveloppe_id='$enveloppeId' && mois='$moisIso'", "UTF-8")
             val url = "$urlBase/api/collections/$COLLECTION/records?filter=$filtre&perPage=500"
             

 
             val requete = Request.Builder()
                 .url(url)
                 .addHeader("Authorization", "Bearer $token")
                 .get()
                 .build()
 
             val reponse = httpClient.newCall(requete).execute()
             if (!reponse.isSuccessful) {
                 throw Exception("Erreur lors de la recherche: ${reponse.code}")
             }
 
             val data = reponse.body!!.string()
             val listType = com.google.gson.reflect.TypeToken.getParameterized(java.util.List::class.java, AllocationMensuelle::class.java).type
             val allocations: List<AllocationMensuelle> = gson.fromJson(data, listType)
 

             allocations.forEach { allocation ->

             }
 
             allocations
         } catch (e: Exception) {

             emptyList()
         }
     }
 
     /**
      * NOUVELLE FONCTION : Fusionne plusieurs allocations en une seule et supprime les doublons.
      */
     private suspend fun fusionnerEtNettoyerAllocations(
         allocations: List<AllocationMensuelle>,
         enveloppeId: String,
         premierJourMois: Date
     ): AllocationMensuelle = withContext(Dispatchers.IO) {
         
         // 1. Calculer les totaux de toutes les allocations
         val soldeTotal = allocations.sumOf { it.solde }
         val alloueTotal = allocations.sumOf { it.alloue }
         val depenseTotal = allocations.sumOf { it.depense }
         
         // 2. Prendre les informations de la première allocation (pour les métadonnées)
         val premiereAllocation = allocations.first()
         

 
         // 3. Créer une nouvelle allocation fusionnée
         val allocationFusionnee = AllocationMensuelle(
             id = "", // Sera généré lors de la création
             utilisateurId = premiereAllocation.utilisateurId,
             enveloppeId = enveloppeId,
             mois = premierJourMois,
             solde = soldeTotal,
             alloue = alloueTotal,
             depense = depenseTotal,
             compteSourceId = premiereAllocation.compteSourceId,
             collectionCompteSource = premiereAllocation.collectionCompteSource
         )
 
         try {
             // 4. Supprimer toutes les anciennes allocations

             allocations.forEach { allocation ->
                 supprimerAllocation(allocation.id)
             }
 
             // 5. Créer la nouvelle allocation fusionnée

             val nouvelleAllocation = creerAllocationMensuelleInterne(allocationFusionnee)
             nouvelleAllocation
             
         } catch (e: Exception) {

             // En cas d'erreur, retourner la première allocation
             premiereAllocation
         }
     }
 
     /**
      * Supprime une allocation par son ID.
      */
     private suspend fun supprimerAllocation(allocationId: String) = withContext(Dispatchers.IO) {
         try {
             val token = client.obtenirToken() ?: return@withContext
             val urlBase = UrlResolver.obtenirUrlActive()
 
             val url = "$urlBase/api/collections/$COLLECTION/records/$allocationId"
             val requete = Request.Builder()
                 .url(url)
                 .addHeader("Authorization", "Bearer $token")
                 .delete()
                 .build()
 
             val reponse = httpClient.newCall(requete).execute()
             if (reponse.isSuccessful) {

             } else {

             }
         } catch (e: Exception) {

         }
     }
 
     /**
      * Crée une nouvelle allocation mensuelle.
      */
     private suspend fun creerNouvelleAllocation(
         enveloppeId: String,
         premierJourMois: Date
     ): AllocationMensuelle = withContext(Dispatchers.IO) {
         
         val nouvelleAllocation = AllocationMensuelle(
             id = "",
             utilisateurId = client.obtenirUtilisateurConnecte()?.id ?: "",
             enveloppeId = enveloppeId,
             mois = premierJourMois,
             solde = 0.0,
             alloue = 0.0,
             depense = 0.0,
             compteSourceId = null,
             collectionCompteSource = null
         )
 
         creerAllocationMensuelleInterne(nouvelleAllocation)
     }
 
     /**
      * Crée une allocation mensuelle (version interne pour éviter les conflits de noms).
      */
     private suspend fun creerAllocationMensuelleInterne(allocation: AllocationMensuelle): AllocationMensuelle = withContext(Dispatchers.IO) {
         val utilisateurId = client.obtenirUtilisateurConnecte()?.id ?: throw Exception("Utilisateur manquant")
         val token = client.obtenirToken() ?: throw Exception("Token manquant")
         val urlBase = UrlResolver.obtenirUrlActive()
 
         // Préparer les données pour PocketBase avec le bon format (comme TransactionRepositoryImpl)
         val donneesAllocation = mapOf(
             "utilisateur_id" to utilisateurId,
             "enveloppe_id" to allocation.enveloppeId,
             "mois" to DATE_FORMAT.format(allocation.mois),
             "solde" to allocation.solde,
             "alloue" to allocation.alloue,
             "depense" to allocation.depense,
             "compte_source_id" to allocation.compteSourceId,
             "collection_compte_source" to allocation.collectionCompteSource
         )

         val bodyJson = gson.toJson(donneesAllocation)
         

 
         val createReq = Request.Builder()
             .url("$urlBase/api/collections/$COLLECTION/records")
             .addHeader("Authorization", "Bearer $token")
             .post(bodyJson.toRequestBody("application/json".toMediaType()))
             .build()
             
         httpClient.newCall(createReq).execute().use { resp ->
             if (!resp.isSuccessful) throw Exception("Erreur création allocation: ${resp.code} ${resp.body?.string()}")
             
             val corpsReponse = resp.body!!.string()

             // Parser manuellement pour gérer le format de date de PocketBase (comme dans TransactionRepositoryImpl)
             val jsonObject = gson.fromJson(corpsReponse, com.google.gson.JsonObject::class.java)

             // Nettoyer la date (enlever .000Z)
             val moisString = jsonObject.get("mois").asString
             val dateClean = moisString.replace(".000Z", "")
             val dateParsee = DATE_FORMAT.parse(dateClean)

             val allocationCreee = AllocationMensuelle(
                 id = jsonObject.get("id").asString,
                 utilisateurId = jsonObject.get("utilisateur_id").asString,
                 enveloppeId = jsonObject.get("enveloppe_id").asString,
                 mois = dateParsee,
                 solde = jsonObject.get("solde").asDouble,
                 alloue = jsonObject.get("alloue").asDouble,
                 depense = jsonObject.get("depense").asDouble,
                 compteSourceId = jsonObject.get("compte_source_id")?.asString ?: "",
                 collectionCompteSource = jsonObject.get("collection_compte_source")?.asString ?: ""
             )
             

             
             return@withContext allocationCreee
         }
     }
 
     /**
      * Désérialise une allocation mensuelle depuis JSON (string seulement dans cette version).
      */
     private fun deserialiserAllocation(source: String): AllocationMensuelle {
         return gson.fromJson(source, AllocationMensuelle::class.java)
     }

     override suspend fun creerNouvelleAllocation(allocation: AllocationMensuelle): AllocationMensuelle {
         return creerAllocationMensuelleInterne(allocation)
     }

     /**
      * Récupère toutes les allocations mensuelles pour une enveloppe donnée.
      */
     override suspend fun recupererAllocationsEnveloppe(enveloppeId: String): List<AllocationMensuelle> = withContext(Dispatchers.IO) {
         try {
             val utilisateurId = client.obtenirUtilisateurConnecte()?.id 
                 ?: throw Exception("Utilisateur non connecté")
             val token = client.obtenirToken() 
                 ?: throw Exception("Token manquant")
             val urlBase = UrlResolver.obtenirUrlActive()

             // Filtre pour cette enveloppe
             val filtre = java.net.URLEncoder.encode("enveloppe_id='$enveloppeId'", "UTF-8")
             val url = "$urlBase/api/collections/$COLLECTION/records?filter=$filtre&perPage=500&sort=-mois"
             
             val requete = Request.Builder()
                 .url(url)
                 .addHeader("Authorization", "Bearer $token")
                 .get()
                 .build()

             val reponse = httpClient.newCall(requete).execute()
             if (!reponse.isSuccessful) {
                 throw Exception("Erreur lors de la recherche: ${reponse.code}")
             }

             val data = reponse.body!!.string()
             val listType = com.google.gson.reflect.TypeToken.getParameterized(java.util.List::class.java, AllocationMensuelle::class.java).type
             val allocations: List<AllocationMensuelle> = gson.fromJson(data, listType)

             allocations
         } catch (e: Exception) {
             emptyList()
         }
     }
 }