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
                return@withContext null
            }
            
            val urlBase = UrlResolver.obtenirUrlActive()
            
            val requete = Request.Builder()
                .url("$urlBase/api/collections/$COLLECTION/records/$id")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
                
            val reponse = httpClient.newCall(requete).execute()
            
            if (!reponse.isSuccessful) {
                return@withContext null
            }
            
            val corpsReponse = reponse.body?.string()
            if (corpsReponse == null) {
                return@withContext null
            }
            
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
            val token = client.obtenirToken() ?: throw Exception("Token manquant")
            val urlBase = UrlResolver.obtenirUrlActive()
            
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
            
            val requete = Request.Builder()
                .url("$urlBase/api/collections/$COLLECTION/records/${allocation.id}")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .patch(bodyJson.toRequestBody("application/json".toMediaType())) // ← PATCH au lieu de PUT
                .build()
                
            val reponse = httpClient.newCall(requete).execute()
            
            if (!reponse.isSuccessful) {
                val erreur = "Erreur HTTP ${reponse.code}: ${reponse.body?.string()}"
                throw Exception(erreur)
            }
            
            reponse.close()
        } catch (e: Exception) {
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
        
        // 🔥 OBTENIR L'UTILISATEUR CONNECTÉ SANS FORCER LE 1ER DU MOIS !
        val utilisateurId = client.obtenirUtilisateurConnecte()?.id 
            ?: throw Exception("Utilisateur non connecté")

        // 2. Chercher les allocations existantes pour cette enveloppe et ce mois
        val allocationsExistantes = recupererAllocationsPourEnveloppeEtMois(utilisateurId, enveloppeId, mois)
         
         when {
            // Cas 1: Aucune allocation trouvée -> Créer une nouvelle
            allocationsExistantes.isEmpty() -> {
                val nouvelleAllocation = AllocationMensuelle(
                    id = "",
                    utilisateurId = utilisateurId,
                    enveloppeId = enveloppeId,
                    mois = mois,
                    solde = 0.0,
                    alloue = 0.0,
                    depense = 0.0,
                    compteSourceId = null,
                    collectionCompteSource = null
                )
                creerAllocationMensuelleInterne(nouvelleAllocation)
            }
            
            // ✅ CORRECTION : TOUJOURS FUSIONNER même s'il y en a qu'une seule !
            // Cas 2 & 3: Une ou plusieurs allocations -> FUSIONNER SYSTÉMATIQUEMENT
            else -> {
                fusionnerEtNettoyerAllocations(allocationsExistantes, enveloppeId, mois)
            }
         }
     }
 
     /**
      * Récupère toutes les allocations pour une enveloppe et un mois donnés.
      */
     private suspend fun recupererAllocationsPourEnveloppeEtMois(
         utilisateurId: String,
         enveloppeId: String, 
         mois: Date
     ): List<AllocationMensuelle> = withContext(Dispatchers.IO) {
         
         try {
             val token = client.obtenirToken() 
                 ?: throw Exception("Token manquant")
             val urlBase = UrlResolver.obtenirUrlActive()
 
                         // 🔥 SIMPLE ! JUSTE LE MOIS TABARNACK !
            val moisString = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault()).format(mois)
            
            // ✅ FILTRE SIMPLE COMME TU VEUX !
            val filtre = java.net.URLEncoder.encode(
                "utilisateur_id = '$utilisateurId' && enveloppe_id='$enveloppeId' && mois ~ '$moisString'",
                "UTF-8"
            )
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
            
            try {
                // 🔥 PARSING SIMPLE COMME ENVELOPEREPO !
                val jsonObject = com.google.gson.JsonParser.parseString(data).asJsonObject
                val itemsArray = jsonObject.getAsJsonArray("items")
                
                val allocations = mutableListOf<AllocationMensuelle>()
                for (i in 0 until itemsArray.size()) {
                    val item = itemsArray[i].asJsonObject
                    
                    val allocation = AllocationMensuelle(
                        id = item.get("id")?.asString ?: "",
                        utilisateurId = item.get("utilisateur_id")?.asString ?: "",
                        enveloppeId = item.get("enveloppe_id")?.asString ?: "",
                        mois = DATE_FORMAT.parse(item.get("mois")?.asString?.replace(Regex("\\.[0-9]+Z$"), "")?.replace(" ", "T") + ".000Z"),
                        solde = item.get("solde")?.asDouble ?: 0.0,
                        alloue = item.get("alloue")?.asDouble ?: 0.0,
                        depense = item.get("depense")?.asDouble ?: 0.0,
                        compteSourceId = item.get("compte_source_id")?.asString?.takeIf { it.isNotEmpty() },
                        collectionCompteSource = item.get("collection_compte_source")?.asString?.takeIf { it.isNotEmpty() }
                    )
                    allocations.add(allocation)
                }

                allocations
            } catch (e: Exception) {
                emptyList()
            }
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
        mois: Date
    ): AllocationMensuelle = withContext(Dispatchers.IO) {
        
        // 1. Calculer les totaux de toutes les allocations
        val soldeTotal = allocations.sumOf { it.solde }
        val alloueTotal = allocations.sumOf { it.alloue }
        val depenseTotal = allocations.sumOf { it.depense }
        
        // 2. Prendre les informations de la première allocation (pour les métadonnées)
        val premiereAllocation = allocations.first()
        
        // ✅ LOGIQUE PROVENANCE SIMPLE ! (comme demandé par l'utilisateur)
        val compteProvenanceFinal = if (soldeTotal < 0.1) {
            null // Reset provenance si plus d'argent
        } else {
            // Trouver la provenance dominante (allocation avec le plus gros solde positif)
            val allocationDominante = allocations
                .filter { it.solde > 0.0 }
                .maxByOrNull { it.solde }
            allocationDominante?.compteSourceId
        }
 
        // 3. Créer une nouvelle allocation fusionnée
        val allocationFusionnee = AllocationMensuelle(
            id = "", // Sera généré lors de la création
            utilisateurId = premiereAllocation.utilisateurId,
            enveloppeId = enveloppeId,
            mois = mois,
            solde = soldeTotal,
            alloue = alloueTotal,
            depense = depenseTotal,
            compteSourceId = compteProvenanceFinal,
            collectionCompteSource = if (compteProvenanceFinal != null) premiereAllocation.collectionCompteSource else null
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
            
            val dateParsee = try {
                // Essayer d'abord le format complet
                DATE_FORMAT.parse(moisString)
            } catch (e: Exception) {
                try {
                    // Nettoyer TOUTES les millisecondes + Z et ajouter le T manquant
                    val dateClean = moisString.replace(Regex("\\.[0-9]+Z$"), "").replace(" ", "T")
                    DATE_FORMAT_CLEAN.parse(dateClean)
                } catch (e2: Exception) {
                    Date() // Fallback vers la date actuelle
                }
            }

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
        
        val dateParsee = try {
            // Essayer d'abord le format complet
            DATE_FORMAT.parse(moisString)
        } catch (e: Exception) {
            try {
                // Nettoyer TOUTES les millisecondes + Z et ajouter le T manquant
                val dateClean = moisString.replace(Regex("\\.[0-9]+Z$"), "").replace(" ", "T")
                DATE_FORMAT_CLEAN.parse(dateClean)
            } catch (e2: Exception) {
                Date() // Fallback vers la date actuelle
            }
        }

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