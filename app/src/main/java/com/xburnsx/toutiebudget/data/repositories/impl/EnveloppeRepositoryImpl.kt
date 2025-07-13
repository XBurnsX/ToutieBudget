/**
 * Chemin: app/src/main/java/com/xburnsx/toutiebudget/data/repositories/impl/EnveloppeRepositoryImpl.kt
 * Dépendances: PocketBaseClient, Gson, SafeDateAdapter, OkHttp3, Modèles
 */

 package com.xburnsx.toutiebudget.data.repositories.impl

 import com.google.gson.JsonParser
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
 import java.text.SimpleDateFormat
 import java.util.*
 
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
     private val formateurDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
 
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
 
             val filtre = URLEncoder.encode("utilisateur_id = '$utilisateurId'", "UTF-8")
             val url = "$urlBase/api/collections/${Collections.ENVELOPPES}/records?filter=$filtre&perPage=500&sort=ordre"
 
             val requete = Request.Builder()
                 .url(url)
                 .addHeader("Authorization", "Bearer $token")
                 .get()
                 .build()
 
             val reponse = httpClient.newCall(requete).execute()
             if (!reponse.isSuccessful) {
                 throw Exception("Erreur PocketBase: ${reponse.code}")
             }
 
             val corpsReponse = reponse.body?.string() ?: ""
             val json = JsonParser.parseString(corpsReponse).asJsonObject
             val items = json.getAsJsonArray("items")
 
             val enveloppes = items.map { item ->
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
 
             // Calculer le premier jour du mois
             val calendrier = Calendar.getInstance().apply {
                 time = mois
                 set(Calendar.DAY_OF_MONTH, 1)
                 set(Calendar.HOUR_OF_DAY, 0)
                 set(Calendar.MINUTE, 0)
                 set(Calendar.SECOND, 0)
                 set(Calendar.MILLISECOND, 0)
             }
             val premierJourMois = calendrier.time
 
             println("[DEBUG] === RECHERCHE ALLOCATIONS ===")
             println("[DEBUG] Date de recherche reçue: $mois")
             println("[DEBUG] Date formatée pour recherche: '${formateurDate.format(premierJourMois)}'")
 
             val dateFormatee = formateurDate.format(premierJourMois)
             val filtre = "utilisateur_id = '$utilisateurId' && mois ~ '$dateFormatee'"
             val url = "$urlBase/api/collections/${Collections.ALLOCATIONS}/records?filter=$filtre&perPage=500"
             
             println("[DEBUG] recupererAllocationsPourMois: mois=$mois, dateFormatee=$dateFormatee")
             println("[DEBUG] recupererAllocationsPourMois: URL=$url")
 
             val requete = Request.Builder()
                 .url(url)
                 .addHeader("Authorization", "Bearer $token")
                 .get()
                 .build()
 
             val reponse = httpClient.newCall(requete).execute()
             if (!reponse.isSuccessful) {
                 throw Exception("Erreur lors de la récupération des allocations: ${reponse.code} ${reponse.body?.string()}")
             }
 
             val corpsReponse = reponse.body?.string() ?: ""
             println("[DEBUG] Réponse recherche: ${corpsReponse.take(200)}...")
 
             val json = JsonParser.parseString(corpsReponse).asJsonObject
             val items = json.getAsJsonArray("items")
 
             // Désérialiser toutes les allocations brutes
             val toutesLesAllocations = items.map { item ->
                 deserialiserAllocation(item.asJsonObject)
             }
 
             println("[DEBUG] recupererAllocationsPourMois: ${toutesLesAllocations.size} allocations trouvées")
 
             // *** CORRECTION PRINCIPALE ***
             // Grouper par enveloppeId et fusionner les doublons
             val allocationsUniques = toutesLesAllocations
                 .groupBy { it.enveloppeId }
                 .map { (enveloppeId, allocationsGroupe) ->
                     if (allocationsGroupe.size == 1) {
                         // Une seule allocation pour cette enveloppe -> OK
                         allocationsGroupe.first()
                     } else {
                         // Plusieurs allocations pour la même enveloppe -> Fusionner
                         println("[DEBUG] ⚠️ ${allocationsGroupe.size} allocations trouvées pour enveloppe $enveloppeId")
                         println("[DEBUG] Fusion automatique en cours...")
                         
                         // Calculer les totaux
                         val soldeTotal = allocationsGroupe.sumOf { it.solde }
                         val alloueTotal = allocationsGroupe.sumOf { it.alloue }
                         val depenseTotal = allocationsGroupe.sumOf { it.depense }
                         
                         // Prendre la première comme base et modifier les montants
                         val premiere = allocationsGroupe.first()
                         premiere.copy(
                             solde = soldeTotal,
                             alloue = alloueTotal,
                             depense = depenseTotal
                         )
                     }
                 }
 
             println("[DEBUG] Allocations après dédoublonnage: ${allocationsUniques.size}")
             allocationsUniques.forEach { allocation ->
                 println("[DEBUG] - Allocation: id='${allocation.id}' enveloppeId='${allocation.enveloppeId}' solde=${allocation.solde}")
                 println("[DEBUG]  - enveloppeId=${allocation.enveloppeId}, solde=${allocation.solde}, depense=${allocation.depense}")
             }
             println("[DEBUG] ===============================")
 
             Result.success(allocationsUniques)
         } catch (e: Exception) {
             println("[DEBUG] Erreur recupererAllocationsPourMois: ${e.message}")
             Result.failure(e)
         }
     }
 
     /**
      * Met à jour une allocation mensuelle.
      */
     override suspend fun mettreAJourAllocation(allocation: AllocationMensuelle): Result<Unit> = withContext(Dispatchers.IO) {
         try {
             val token = client.obtenirToken() 
                 ?: return@withContext Result.failure(Exception("Token manquant"))
             val urlBase = client.obtenirUrlBaseActive()
 
             val donneesUpdate = mapOf(
                 "solde" to allocation.solde,
                 "alloue" to allocation.alloue,
                 "depense" to allocation.depense,
                 "compte_source_id" to (allocation.compteSourceId ?: ""),
                 "collection_compte_source" to (allocation.collectionCompteSource ?: "")
             )
             val corpsRequete = gson.toJson(donneesUpdate)
 
             val requete = Request.Builder()
                 .url("$urlBase/api/collections/${Collections.ALLOCATIONS}/records/${allocation.id}")
                 .addHeader("Authorization", "Bearer $token")
                 .addHeader("Content-Type", "application/json")
                 .put(corpsRequete.toRequestBody("application/json".toMediaType()))
                 .build()
 
             val reponse = httpClient.newCall(requete).execute()
             if (!reponse.isSuccessful) {
                 throw Exception("Erreur lors de la mise à jour: ${reponse.code}")
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
             // Essayer de récupérer une allocation existante
             val allocationExistante = recupererAllocationMensuelle(enveloppeId, mois)
             if (allocationExistante.isSuccess) {
                 val allocation = allocationExistante.getOrNull()
                 if (allocation != null) {
                     return@withContext Result.success(allocation)
                 }
             }
 
             // Créer une nouvelle allocation
             val nouvelleAllocation = AllocationMensuelle(
                 id = "",
                 utilisateurId = client.obtenirUtilisateurConnecte()?.id ?: "",
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
             val token = client.obtenirToken() 
                 ?: return@withContext Result.failure(Exception("Token manquant"))
             val urlBase = client.obtenirUrlBaseActive()
 
             val donnees = mapOf(
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
             val body = json.toRequestBody("application/json".toMediaType())
             
             val requete = Request.Builder()
                 .url("$urlBase/api/collections/${Collections.ENVELOPPES}/records/${enveloppe.id}")
                 .addHeader("Authorization", "Bearer $token")
                 .patch(body)
                 .build()
 
             val reponse = httpClient.newCall(requete).execute()
             if (!reponse.isSuccessful) {
                 throw Exception("Erreur lors de la mise à jour: ${reponse.code}")
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
 
             val dateFormatee = formateurDate.format(mois)
             val filtre = "utilisateur_id = '$utilisateurId' && enveloppe_id = '$enveloppeId' && mois ~ '$dateFormatee'"
             val url = "$urlBase/api/collections/${Collections.ALLOCATIONS}/records?filter=$filtre&perPage=1"
 
             val requete = Request.Builder()
                 .url(url)
                 .addHeader("Authorization", "Bearer $token")
                 .get()
                 .build()
 
             val reponse = httpClient.newCall(requete).execute()
             if (!reponse.isSuccessful) {
                 throw Exception("Erreur recherche allocation: ${reponse.code}")
             }
 
             val corpsReponse = reponse.body?.string() ?: ""
             val json = JsonParser.parseString(corpsReponse).asJsonObject
             val items = json.getAsJsonArray("items")
 
             if (items.size() > 0) {
                 val allocation = deserialiserAllocation(items[0].asJsonObject)
                 Result.success(allocation)
             } else {
                 Result.success(null)
             }
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
 
             val moisFormate = formateurDate.format(allocation.mois)
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
             
             println("[DEBUG] === ALLOCATION CRÉÉE ===")
             println("[DEBUG] ID créé: '${allocationCreee.id}'")
             println("[DEBUG] Date stockée: ${allocationCreee.mois}")
             println("[DEBUG] Date stockée formatée: '${formateurDate.format(allocationCreee.mois)}'")
             println("[DEBUG] ============================")
             
             Result.success(allocationCreee)
         } catch (e: Exception) {
             println("[DEBUG] Erreur lors de la création de l'allocation: ${e.message}")
             Result.failure(e)
         }
     }
 
     /**
      * Récupère une allocation par son ID.
      */
     private suspend fun recupererAllocationParId(id: String): Result<AllocationMensuelle> = withContext(Dispatchers.IO) {
         try {
             val token = client.obtenirToken() ?: throw Exception("Token manquant")
             val urlBase = client.obtenirUrlBaseActive()
 
             val requete = Request.Builder()
                 .url("$urlBase/api/collections/${Collections.ALLOCATIONS}/records/$id")
                 .addHeader("Authorization", "Bearer $token")
                 .get()
                 .build()
 
             val reponse = httpClient.newCall(requete).execute()
             if (!reponse.isSuccessful) {
                 throw Exception("Allocation non trouvée: ${reponse.code}")
             }
 
             val corpsReponse = reponse.body?.string() ?: ""
             val allocation = deserialiserAllocation(corpsReponse)
             Result.success(allocation)
         } catch (e: Exception) {
             Result.failure(e)
         }
     }
 
     /**
      * Désérialise une enveloppe depuis JSON.
      */
     private fun deserialiserEnveloppe(json: String): Enveloppe? {
         return try {
             gson.fromJson(json, Enveloppe::class.java)
         } catch (e: Exception) {
             println("[DEBUG] Erreur désérialisation enveloppe: ${e.message}")
             null
         }
     }
 
     /**
      * Désérialise une allocation mensuelle depuis JSON.
      */
     private fun deserialiserAllocation(source: Any): AllocationMensuelle {
         val json = when (source) {
             is String -> JsonParser.parseString(source).asJsonObject
             is com.google.gson.JsonObject -> source
             else -> throw IllegalArgumentException("Source JSON invalide")
         }
 
         // Gestion de la date avec debug
         val moisString = json.get("mois").asString
         println("[DEBUG] deserialiserAllocation: moisString reçu='$moisString'")
         
         // Nettoyer la date (enlever le .000Z si présent)
         val dateClean = moisString.replace(".000Z", "").replace("T", " ")
         println("[DEBUG] deserialiserAllocation: dateClean='$dateClean'")
         
         val dateParsee = formateurDate.parse(dateClean) ?: Date()
         println("[DEBUG] deserialiserAllocation: dateParsee=$dateParsee")
 
         return AllocationMensuelle(
             id = json.get("id").asString,
             utilisateurId = json.get("utilisateur_id").asString,
             enveloppeId = json.get("enveloppe_id").asString,
             mois = dateParsee,
             solde = json.get("solde").asDouble,
             alloue = json.get("alloue").asDouble,
             depense = json.get("depense").asDouble,
             compteSourceId = json.get("compte_source_id")?.takeIf { !it.isJsonNull }?.asString,
             collectionCompteSource = json.get("collection_compte_source")?.takeIf { !it.isJsonNull }?.asString
         )
     }
 }