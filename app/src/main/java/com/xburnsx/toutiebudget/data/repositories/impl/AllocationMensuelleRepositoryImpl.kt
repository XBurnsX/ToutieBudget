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
// import supprimé: BudgetEvents non utilisé
 import kotlinx.coroutines.Dispatchers
 import kotlinx.coroutines.withContext
 import okhttp3.MediaType.Companion.toMediaType
 import okhttp3.OkHttpClient
 import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.xburnsx.toutiebudget.utils.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.*
import java.net.URLEncoder
 
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
                
            httpClient.newCall(requete).execute().use { reponse ->
                if (!reponse.isSuccessful) return@withContext null
                val corpsReponse = reponse.body?.string() ?: return@withContext null
                deserialiserAllocation(corpsReponse)
            }
        } catch (_: Exception) {
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
                "solde" to MoneyFormatter.roundAmount(allocation.solde),
                "alloue" to MoneyFormatter.roundAmount(allocation.alloue),
                "depense" to MoneyFormatter.roundAmount(allocation.depense),
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
                
            httpClient.newCall(requete).execute().use { reponse ->
                if (!reponse.isSuccessful) {
                    val erreur = "Erreur HTTP ${reponse.code}: ${reponse.body?.string()}"
                    throw Exception(erreur)
                }
            }
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
            val moisString = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(mois)
            
            // ✅ FILTRE SIMPLE COMME TU VEUX !
            val filtre = URLEncoder.encode(
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
                 val jsonObject = JsonParser.parseString(data).asJsonObject
                val itemsArray = jsonObject.getAsJsonArray("items")
                
                val allocations = mutableListOf<AllocationMensuelle>()
                for (i in 0 until itemsArray.size()) {
                    val item = itemsArray[i].asJsonObject
                    
                    val moisString = item.get("mois")?.asString
                    val moisParsed: Date = try {
                        DATE_FORMAT.parse(moisString?.replace(Regex("\\.[0-9]+Z$"), "")?.replace(" ", "T") + ".000Z")
                            ?: Date()
                    } catch (_: Exception) {
                        Date()
                    }

                    val allocation = AllocationMensuelle(
                        id = item.get("id")?.asString ?: "",
                        utilisateurId = item.get("utilisateur_id")?.asString ?: "",
                        enveloppeId = item.get("enveloppe_id")?.asString ?: "",
                        mois = moisParsed,
                        solde = item.get("solde")?.asDouble ?: 0.0,
                        alloue = item.get("alloue")?.asDouble ?: 0.0,
                        depense = item.get("depense")?.asDouble ?: 0.0,
                        compteSourceId = item.get("compte_source_id")?.asString?.takeIf { it.isNotEmpty() },
                        collectionCompteSource = item.get("collection_compte_source")?.asString?.takeIf { it.isNotEmpty() }
                    )
                    allocations.add(allocation)
                }

                allocations
            } catch (_: Exception) {
                emptyList()
            }
         } catch (_: Exception) {
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
        // 1) Calculer les totaux à fusionner
        val soldeTotal = allocations.sumOf { it.solde }
        val alloueTotal = allocations.sumOf { it.alloue }
        val depenseTotal = allocations.sumOf { it.depense }

        // 2) Choisir une allocation CANONIQUE à conserver (garder l'ID pour ne PAS casser les références)
        val allocationCanonique = allocations.first()

        // 3) Déterminer la provenance finale (compte source et collection)
        val allocationDominante = allocations
            .filter { it.solde > 0.0 }
            .maxByOrNull { it.solde }
        val compteSourceFinal = if (soldeTotal < 0.01) null else allocationDominante?.compteSourceId
        val collectionCompteSourceFinal = if (soldeTotal < 0.01) null else allocationDominante?.collectionCompteSource

        // 4) Construire l'objet fusionné avec le MÊME ID (celui de l'allocation canonique)
        val allocationFusionnee = allocationCanonique.copy(
            mois = mois,
            solde = soldeTotal,
            alloue = alloueTotal,
            depense = depenseTotal,
            compteSourceId = compteSourceFinal,
            collectionCompteSource = collectionCompteSourceFinal
        )

        try {
            // 5) Mettre à jour l'allocation canonique avec les totaux
            mettreAJourAllocation(allocationFusionnee)

            // 6) Avant de supprimer les doublons, réassigner leurs transactions vers l'ID canonique
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: throw Exception("Utilisateur non connecté")
            val token = client.obtenirToken() ?: throw Exception("Token manquant")
            val urlBase = UrlResolver.obtenirUrlActive()

            allocations.drop(1).forEach { doublon ->
                if (doublon.id == allocationCanonique.id) return@forEach

                // 6.a) Récupérer les transactions liées à ce doublon
                val filtre = java.net.URLEncoder.encode(
                    "utilisateur_id = '$utilisateurId' && allocation_mensuelle_id = '${doublon.id}'",
                    "UTF-8"
                )
                val urlRecherche = "$urlBase/api/collections/transactions/records?filter=$filtre&perPage=500"
                val reqRecherche = okhttp3.Request.Builder()
                    .url(urlRecherche)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                httpClient.newCall(reqRecherche).execute().use { respList ->
                    if (respList.isSuccessful) {
                        val body = respList.body?.string().orEmpty()
                        val json = JsonParser.parseString(body).asJsonObject
                        val items = json.getAsJsonArray("items")
                        for (i in 0 until items.size()) {
                            val item = items[i].asJsonObject
                            val transactionId = item.get("id").asString
                            // 6.b) PATCH de la transaction pour pointer vers l'allocation canonique
                            val patchJson = gson.toJson(mapOf(
                                "allocation_mensuelle_id" to allocationCanonique.id
                            ))
                            val urlPatch = "$urlBase/api/collections/transactions/records/$transactionId"
                            val reqPatch = okhttp3.Request.Builder()
                                .url(urlPatch)
                                .addHeader("Authorization", "Bearer $token")
                                .addHeader("Content-Type", "application/json")
                                .patch(patchJson.toRequestBody("application/json".toMediaType()))
                                .build()
                            httpClient.newCall(reqPatch).execute().use { _ -> }
                        }
                    }
                }

                // 6.c) Supprimer le doublon
                supprimerAllocation(doublon.id)
            }

            // 7) Retourner l'allocation fusionnée conservant le même ID
            allocationFusionnee
        } catch (e: Exception) {
            // En cas d'erreur, au moins retourner l'allocation canonique inchangée
            allocationCanonique
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
 
              httpClient.newCall(requete).execute().use { _ -> }
          } catch (_: Exception) {

         }
     }
 
     /**
      * Crée une nouvelle allocation mensuelle.
      */
      // Supprimé: fonction interne non utilisée
 
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
              "solde" to com.xburnsx.toutiebudget.utils.MoneyFormatter.roundAmount(allocation.solde),
              "alloue" to com.xburnsx.toutiebudget.utils.MoneyFormatter.roundAmount(allocation.alloue),
              "depense" to com.xburnsx.toutiebudget.utils.MoneyFormatter.roundAmount(allocation.depense),
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
              val token = client.obtenirToken() 
                 ?: throw Exception("Token manquant")
             val urlBase = UrlResolver.obtenirUrlActive()

             // Filtre pour cette enveloppe
              val filtre = URLEncoder.encode("enveloppe_id='$enveloppeId'", "UTF-8")
             val url = "$urlBase/api/collections/$COLLECTION/records?filter=$filtre&perPage=500&sort=-mois"
             
             val requete = Request.Builder()
                 .url(url)
                 .addHeader("Authorization", "Bearer $token")
                 .get()
                 .build()

             val data = httpClient.newCall(requete).execute().use { resp ->
                 if (!resp.isSuccessful) {
                     throw Exception("Erreur lors de la recherche: ${resp.code}")
                 }
                 resp.body!!.string()
             }
             val listType = com.google.gson.reflect.TypeToken.getParameterized(java.util.List::class.java, AllocationMensuelle::class.java).type
             val allocations: List<AllocationMensuelle> = gson.fromJson(data, listType)

             allocations
         } catch (_: Exception) {
             emptyList()
         }
     }
 }