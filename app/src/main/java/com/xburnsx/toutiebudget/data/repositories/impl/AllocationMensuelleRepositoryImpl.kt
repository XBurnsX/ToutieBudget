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
        private val DATE_FORMAT_CLEAN = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US) // Pour parser après nettoyage
    }

    // ✅ Suppression du formateurDate en doublon - on utilise seulement DATE_FORMAT
 
     /**
      * Récupère une allocation mensuelle par son ID.
      */
         override suspend fun getAllocationById(id: String): AllocationMensuelle? = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken()
            if (token == null) {
                println("[DEBUG] ❌ getAllocationById: Token manquant")
                return@withContext null
            }
            
            val urlBase = UrlResolver.obtenirUrlActive()
            
            println("[DEBUG] 🔍 getAllocationById: Recherche ID='$id'")
            println("[DEBUG] 🌐 URL: $urlBase/api/collections/$COLLECTION/records/$id")
            
            val requete = Request.Builder()
                .url("$urlBase/api/collections/$COLLECTION/records/$id")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
                
            val reponse = httpClient.newCall(requete).execute()
            
            if (!reponse.isSuccessful) {
                val erreur = "HTTP ${reponse.code}: ${reponse.body?.string()}"
                println("[DEBUG] ❌ getAllocationById: $erreur")
                return@withContext null
            }
            
            val corpsReponse = reponse.body?.string()
            if (corpsReponse == null) {
                println("[DEBUG] ❌ getAllocationById: Corps de réponse vide")
                return@withContext null
            }
            
            println("[DEBUG] ✅ getAllocationById: Allocation trouvée")
            deserialiserAllocation(corpsReponse)
        } catch (e: Exception) {
            println("[DEBUG] ❌ getAllocationById: Exception ${e.message}")
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
            val token = client.obtenirToken() ?: throw Exception("Token manquant")
            val urlBase = UrlResolver.obtenirUrlActive()
            
            println("[DEBUG] 🔄 Mise à jour allocation ID=${allocation.id}, solde=${allocation.solde}")
            println("[DEBUG] 🌐 URL: $urlBase/api/collections/$COLLECTION/records/${allocation.id}")
            
            // ✅ Utiliser le bon format au lieu de gson.toJson() bugué
            val donneesUpdate = mapOf(
                "utilisateur_id" to allocation.utilisateurId, // ← AJOUT ! Peut-être requis pour l'autorisation
                "solde" to allocation.solde,
                "alloue" to allocation.alloue,
                "depense" to allocation.depense,
                "mois" to DATE_FORMAT.format(allocation.mois),
                "compte_source_id" to (allocation.compteSourceId ?: ""),
                "collection_compte_source" to (allocation.collectionCompteSource ?: "")
            )
            
            val bodyJson = gson.toJson(donneesUpdate)
            println("[DEBUG] 📤 Données envoyées: $bodyJson")
            
            val requete = Request.Builder()
                .url("$urlBase/api/collections/$COLLECTION/records/${allocation.id}")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .patch(bodyJson.toRequestBody("application/json".toMediaType())) // ← PATCH au lieu de PUT
                .build()
                
            val reponse = httpClient.newCall(requete).execute()
            
            if (!reponse.isSuccessful) {
                val erreur = "Erreur HTTP ${reponse.code}: ${reponse.body?.string()}"
                println("[DEBUG] ❌ $erreur")
                throw Exception(erreur)
            }
            
            println("[DEBUG] ✅ Allocation mise à jour avec succès")
            reponse.close()
        } catch (e: Exception) {
            println("[DEBUG] ❌ ERREUR dans mettreAJourAllocation: ${e.message}")
            throw e // ← IMPORTANT : Remonter l'erreur !
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
        override suspend fun recupererOuCreerAllocation(
        enveloppeId: String, 
        mois: Date
    ): AllocationMensuelle = withContext(Dispatchers.IO) {
        
        // 1. Utiliser la date telle qu'elle est (plus de normalisation forcée)
        println("[DEBUG] 📅 Recherche allocation pour enveloppe=$enveloppeId, mois=$mois")

        // 2. Chercher les allocations existantes pour cette enveloppe et ce mois
        val allocationsExistantes = recupererAllocationsPourEnveloppeEtMois(enveloppeId, mois)
         
         when {
                         // Cas 1: Aucune allocation trouvée -> Créer une nouvelle
            allocationsExistantes.isEmpty() -> {
                println("[DEBUG] ✨ Aucune allocation trouvée, création d'une nouvelle")
                creerNouvelleAllocation(enveloppeId, mois)
            }
            
            // Cas 2: Une seule allocation trouvée -> La retourner
            allocationsExistantes.size == 1 -> {
                println("[DEBUG] ✅ Allocation existante trouvée: ${allocationsExistantes.first().id}")
                allocationsExistantes.first()
            }
            
            // Cas 3: PROBLÈME - Plusieurs allocations trouvées -> Fusionner et nettoyer
            else -> {
                println("[DEBUG] ⚠️ ${allocationsExistantes.size} allocations trouvées, fusion nécessaire")
                fusionnerEtNettoyerAllocations(allocationsExistantes, enveloppeId, mois)
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

                         // Parsing intelligent de date PocketBase
            val moisString = jsonObject.get("mois").asString
            println("[DEBUG] Date reçue de PocketBase: '$moisString'")
            
            val dateParsee = try {
                // Essayer d'abord le format complet
                DATE_FORMAT.parse(moisString)
            } catch (e: Exception) {
                try {
                    // Nettoyer TOUTES les millisecondes + Z et ajouter le T manquant
                    val dateClean = moisString.replace(Regex("\\.[0-9]+Z$"), "").replace(" ", "T")
                    println("[DEBUG] Date nettoyée: '$dateClean'")
                    DATE_FORMAT_CLEAN.parse(dateClean)
                } catch (e2: Exception) {
                    println("[DEBUG] ❌ Impossible de parser '$moisString', utilisation de Date()")
                    Date() // Fallback vers la date actuelle
                }
            }
            
            println("[DEBUG] Date parsée avec succès: $dateParsee")

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
     * Désérialise une allocation mensuelle depuis JSON avec parsing intelligent des dates.
     */
    private fun deserialiserAllocation(source: String): AllocationMensuelle {
        // Parser manuellement pour gérer le format de date de PocketBase
        val jsonObject = gson.fromJson(source, com.google.gson.JsonObject::class.java)

        // Parsing intelligent de date PocketBase (même logique que creerAllocationMensuelleInterne)
        val moisString = jsonObject.get("mois").asString
        println("[DEBUG] 📅 deserialiserAllocation - Date reçue: '$moisString'")
        
        val dateParsee = try {
            // Essayer d'abord le format complet
            DATE_FORMAT.parse(moisString)
        } catch (e: Exception) {
            try {
                // Nettoyer TOUTES les millisecondes + Z et ajouter le T manquant
                val dateClean = moisString.replace(Regex("\\.[0-9]+Z$"), "").replace(" ", "T")
                println("[DEBUG] 📅 deserialiserAllocation - Date nettoyée: '$dateClean'")
                DATE_FORMAT_CLEAN.parse(dateClean)
            } catch (e2: Exception) {
                println("[DEBUG] ❌ deserialiserAllocation - Impossible de parser '$moisString', utilisation de Date()")
                Date() // Fallback vers la date actuelle
            }
        }
        
        println("[DEBUG] ✅ deserialiserAllocation - Date parsée: $dateParsee")

        return AllocationMensuelle(
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