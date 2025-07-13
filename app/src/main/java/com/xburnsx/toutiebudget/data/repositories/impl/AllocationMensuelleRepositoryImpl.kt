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
             println("[DEBUG] Erreur getAllocationById: ${e.message}")
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
             println("[DEBUG] Erreur mettreAJourAllocation: ${e.message}")
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
             println("[DEBUG] Erreur mettreAJourAllocation complète: ${e.message}")
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
             println("[DEBUG] Erreur mettreAJourCompteSource: ${e.message}")
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
 
         println("[DEBUG] === RECHERCHE/CRÉATION ALLOCATION ===")
         println("[DEBUG] EnveloppeId: '$enveloppeId'")
         println("[DEBUG] Mois demandé: $mois")
         println("[DEBUG] Premier jour calculé: $premierJourMois")
 
         // 2. Chercher les allocations existantes pour cette enveloppe et ce mois
         val allocationsExistantes = recupererAllocationsPourEnveloppeEtMois(enveloppeId, premierJourMois)
         
         when {
             // Cas 1: Aucune allocation trouvée -> Créer une nouvelle
             allocationsExistantes.isEmpty() -> {
                 println("[DEBUG] Aucune allocation trouvée, création d'une nouvelle")
                 creerNouvelleAllocation(enveloppeId, premierJourMois)
             }
             
             // Cas 2: Une seule allocation trouvée -> La retourner
             allocationsExistantes.size == 1 -> {
                 println("[DEBUG] Une allocation trouvée, utilisation de celle-ci")
                 allocationsExistantes.first()
             }
             
             // Cas 3: PROBLÈME - Plusieurs allocations trouvées -> Fusionner et nettoyer
             else -> {
                 println("[DEBUG] ⚠️ PROBLÈME: ${allocationsExistantes.size} allocations trouvées pour la même enveloppe/mois")
                 println("[DEBUG] Fusion et nettoyage en cours...")
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
             
             println("[DEBUG] URL recherche: $url")
 
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
 
             println("[DEBUG] ${allocations.size} allocations trouvées pour enveloppe '$enveloppeId'")
             allocations.forEach { allocation ->
                 println("[DEBUG] - ID: ${allocation.id}, solde: ${allocation.solde}, dépense: ${allocation.depense}")
             }
 
             allocations
         } catch (e: Exception) {
             println("[DEBUG] Erreur lors de la recherche: ${e.message}")
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
         
         println("[DEBUG] === FUSION DES ALLOCATIONS ===")
         
         // 1. Calculer les totaux de toutes les allocations
         val soldeTotal = allocations.sumOf { it.solde }
         val alloueTotal = allocations.sumOf { it.alloue }
         val depenseTotal = allocations.sumOf { it.depense }
         
         // 2. Prendre les informations de la première allocation (pour les métadonnées)
         val premiereAllocation = allocations.first()
         
         println("[DEBUG] Fusion de ${allocations.size} allocations:")
         println("[DEBUG] - Solde total: $soldeTotal")
         println("[DEBUG] - Alloué total: $alloueTotal") 
         println("[DEBUG] - Dépense total: $depenseTotal")
 
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
             println("[DEBUG] Suppression des ${allocations.size} allocations existantes...")
             allocations.forEach { allocation ->
                 supprimerAllocation(allocation.id)
             }
 
             // 5. Créer la nouvelle allocation fusionnée
             println("[DEBUG] Création de l'allocation fusionnée...")
             val nouvelleAllocation = creerAllocationMensuelleInterne(allocationFusionnee)
             
             println("[DEBUG] ✅ Allocation fusionnée créée avec succès: ${nouvelleAllocation.id}")
             println("[DEBUG] - Solde final: ${nouvelleAllocation.solde}")
             println("[DEBUG] - Dépense finale: ${nouvelleAllocation.depense}")
             nouvelleAllocation
             
         } catch (e: Exception) {
             println("[DEBUG] ❌ Erreur lors de la fusion: ${e.message}")
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
                 println("[DEBUG] Allocation $allocationId supprimée")
             } else {
                 println("[DEBUG] Erreur suppression: ${reponse.code}")
             }
         } catch (e: Exception) {
             println("[DEBUG] Erreur suppression allocation $allocationId: ${e.message}")
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
 
         val moisIso = DATE_FORMAT.format(allocation.mois)
         println("[DEBUG] === CRÉATION ALLOCATION ===")
         println("[DEBUG] Date reçue: ${allocation.mois}")
         println("[DEBUG] Date formatée pour PocketBase: '$moisIso'")
         println("[DEBUG] EnveloppeId: '${allocation.enveloppeId}'")
         println("[DEBUG] ================================")
 
         val bodyJson = gson.toJson(
             AllocationMensuelle(
                 id = "", // PocketBase en généra un
                 utilisateurId = utilisateurId,
                 enveloppeId = allocation.enveloppeId,
                 mois = allocation.mois,
                 solde = allocation.solde,
                 alloue = allocation.alloue,
                 depense = allocation.depense,
                 compteSourceId = allocation.compteSourceId,
                 collectionCompteSource = allocation.collectionCompteSource
             )
         )
         
         println("[DEBUG] Données envoyées à PocketBase: $bodyJson")
 
         val createReq = Request.Builder()
             .url("$urlBase/api/collections/$COLLECTION/records")
             .addHeader("Authorization", "Bearer $token")
             .post(bodyJson.toRequestBody("application/json".toMediaType()))
             .build()
             
         httpClient.newCall(createReq).execute().use { resp ->
             if (!resp.isSuccessful) throw Exception("Erreur création allocation: ${resp.code} ${resp.body?.string()}")
             
             val corpsReponse = resp.body!!.string()
             println("[DEBUG] Réponse PocketBase: ${corpsReponse.take(300)}...")
             
             val allocationCreee = gson.fromJson(corpsReponse, AllocationMensuelle::class.java)
             
             println("[DEBUG] === ALLOCATION CRÉÉE ===")
             println("[DEBUG] ID créé: '${allocationCreee.id}'")
             println("[DEBUG] Date stockée: ${allocationCreee.mois}")
             println("[DEBUG] ============================")
             
             return@withContext allocationCreee
         }
     }
 
     /**
      * Désérialise une allocation mensuelle depuis JSON (string seulement dans cette version).
      */
     private fun deserialiserAllocation(source: String): AllocationMensuelle {
         return gson.fromJson(source, AllocationMensuelle::class.java)
     }
 }