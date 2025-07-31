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
 import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
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
         println("[DEBUG] pocketBaseVersTypeObjectif - Valeur reçue: '$str'")
         return when (str) {
             "Aucun" -> TypeObjectif.Aucun
             "Mensuel" -> TypeObjectif.Mensuel
             "Bihebdomadaire" -> TypeObjectif.Bihebdomadaire
             "Echeance" -> TypeObjectif.Echeance
             "Annuel" -> TypeObjectif.Annuel
             else -> {
                 println("[DEBUG] pocketBaseVersTypeObjectif - Valeur inconnue '$str', utilise Aucun par défaut")
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

                 // Récupérer la date d'objectif depuis PocketBase
                 val objectifDateString = itemObject.get("objectif_date")?.asString
                 val objectifTypeString = itemObject.get("frequence_objectif")?.asString  // CORRIGÉ !
                 val nom = itemObject.get("nom")?.asString ?: ""

                println("[DEBUG] Enveloppe '$nom' - frequence_objectif brut: '$objectifTypeString', objectif_date brut: '$objectifDateString'")

                 val objectifDate = if (objectifDateString != null && objectifDateString.isNotBlank()) {
                     try {
                         // Si c'est juste un nombre (jour), créer une date avec ce jour
                         if (objectifDateString.matches(Regex("\\d+"))) {
                             val jour = objectifDateString.toInt()
                             val calendar = Calendar.getInstance()
                             calendar.set(Calendar.DAY_OF_MONTH, jour)
                             val dateCalculee = calendar.time
                             println("[DEBUG] Enveloppe '$nom' - Date calculée pour jour $jour: $dateCalculee")
                             dateCalculee
                         } else {
                             // Sinon, essayer de parser comme une date complète
                             val dateParsee = formateurDate.parse(objectifDateString)
                             println("[DEBUG] Enveloppe '$nom' - Date parsée: $dateParsee")
                             dateParsee
                         }
                     } catch (e: Exception) {
                         println("[DEBUG] Erreur parsing date '$objectifDateString': ${e.message}")
                         null
                     }
                 } else {
                     println("[DEBUG] Enveloppe '$nom' - Pas de date d'objectif (objectif_date vide ou null)")
                     null
                 }

                 Enveloppe(
                     id = itemObject.get("id")?.asString ?: "",
                     utilisateurId = itemObject.get("utilisateur_id")?.asString ?: "",
                     nom = itemObject.get("nom")?.asString ?: "",
                     categorieId = itemObject.get("categorie_id")?.asString ?: "",
                     estArchive = itemObject.get("est_archive")?.asBoolean ?: false,
                     ordre = (itemObject.get("ordre")?.asDouble)?.toInt() ?: 0,
                     typeObjectif = pocketBaseVersTypeObjectif(itemObject.get("frequence_objectif")?.asString), // Utilise frequence_objectif
                     objectifMontant = itemObject.get("montant_objectif")?.asDouble ?: 0.0, // Utilise montant_objectif
                     dateObjectif = itemObject.get("date_objectif")?.asString, // String pour la date d'objectif
                     dateDebutObjectif = if (itemObject.get("date_debut_objectif")?.asString != null) {
                         try {
                             formateurDate.parse(itemObject.get("date_debut_objectif")?.asString)
                         } catch (e: Exception) {
                             null
                         }
                     } else null,
                     objectifJour = (itemObject.get("objectif_jour")?.asDouble)?.toInt()
                 )
             }.filter { !it.estArchive }
 

             Result.success(enveloppes)
             
         } catch (e: Exception) {

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

             // Calculer le dernier jour du mois pour un filtrage exact
             val dernierJourMois = Calendar.getInstance().apply {
                 time = premierJourMois
                 add(Calendar.MONTH, 1)
                 add(Calendar.DAY_OF_MONTH, -1)
                 set(Calendar.HOUR_OF_DAY, 23)
                 set(Calendar.MINUTE, 59)
                 set(Calendar.SECOND, 59)
                 set(Calendar.MILLISECOND, 999)
             }.time

             val dateDebutFormatee = formateurDate.format(premierJourMois)
             val dateFinFormatee = formateurDate.format(dernierJourMois)

             // Filtre EXACT pour le mois spécifique seulement
             val filtre = URLEncoder.encode(
                 "utilisateur_id = '$utilisateurId' && mois >= '$dateDebutFormatee' && mois <= '$dateFinFormatee'",
                 "UTF-8"
             )
             val url = "$urlBase/api/collections/${Collections.ALLOCATIONS}/records?filter=$filtre&perPage=500"
             
             println("[REPO] 🔍 Filtre exact pour mois: $dateDebutFormatee à $dateFinFormatee")
             println("[REPO] 🌐 URL: $url")


 
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

 
             val json = JsonParser.parseString(corpsReponse).asJsonObject
             val items = json.getAsJsonArray("items")
 
             // Désérialiser toutes les allocations brutes
             val toutesLesAllocations = items.map { item ->
                 deserialiserAllocation(item.asJsonObject)
             }
 

 
             // *** CORRECTION PRINCIPALE ***
             // Grouper par enveloppeId et fusionner les doublons
             val allocationsUniques = toutesLesAllocations
                 .groupBy { it.enveloppeId }
                 .map { (enveloppeId, allocationsGroupe) ->
                     if (allocationsGroupe.size == 1) {
                         // Une seule allocation pour cette enveloppe -> OK
                         allocationsGroupe.first()
                     } else {

                         
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
 

             allocationsUniques.forEach { allocation ->

             }

 
             Result.success(allocationsUniques)
         } catch (e: Exception) {

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
 
             // Notification système d'événements après mise à jour
             BudgetEvents.onAllocationUpdated(allocation.id)

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
                 "categorie_id" to enveloppe.categorieId,
                 "est_archive" to enveloppe.estArchive,
                 "ordre" to enveloppe.ordre,
                 "frequence_objectif" to typeObjectifVersPocketBase(enveloppe.typeObjectif), // Utilise nouveau nom
                 "montant_objectif" to enveloppe.objectifMontant, // Utilise nouveau nom
                 "date_objectif" to enveloppe.dateObjectif, // String au lieu de Date formatée
                 "date_debut_objectif" to if (enveloppe.dateDebutObjectif != null) {
                     formateurDate.format(enveloppe.dateDebutObjectif)
                 } else null,
                 "objectif_jour" to enveloppe.objectifJour
             )
             
             val json = gson.toJson(donnees)

             
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
                 "frequence_objectif" to typeObjectifVersPocketBase(enveloppe.typeObjectif), // CORRIGÉ !
                 "montant_objectif" to enveloppe.objectifMontant,
                 "date_objectif" to enveloppe.dateObjectif,
                 "date_debut_objectif" to if (enveloppe.dateDebutObjectif != null) {
                     formateurDate.format(enveloppe.dateDebutObjectif)
                 } else {
                     null
                 },
                 "objectif_jour" to enveloppe.objectifJour
             )

             // 🔥 DEBUG: Afficher les données qui vont être envoyées à PocketBase
             println("[DEBUG POCKETBASE] === MISE À JOUR ENVELOPPE ===")
             println("[DEBUG POCKETBASE] ID: ${enveloppe.id}")
             println("[DEBUG POCKETBASE] date_objectif: ${enveloppe.dateObjectif}")
             println("[DEBUG POCKETBASE] date_debut_objectif brut: ${enveloppe.dateDebutObjectif}")
             println("[DEBUG POCKETBASE] date_debut_objectif formaté: ${if (enveloppe.dateDebutObjectif != null) formateurDate.format(enveloppe.dateDebutObjectif) else null}")
             println("[DEBUG POCKETBASE] Données complètes: $donnees")
             println("[DEBUG POCKETBASE] ==========================================")

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
 
             val allocation = recupererAllocationParId(allocationMensuelleId).getOrNull()
                 ?: throw Exception("Allocation non trouvée")
             
             // 2. Calculer les nouveaux montants
             val nouveauSoldeBrut = allocation.solde - montantDepense  // Soustraction du solde
             // Si le solde est très proche de zéro (positif ou négatif), le mettre à 0
             val nouveauSolde = if (kotlin.math.abs(nouveauSoldeBrut) < 0.001) 0.0 else nouveauSoldeBrut
             val nouvelleDépense = allocation.depense + montantDepense  // Addition aux dépenses existantes
             
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
                 val erreur = "Erreur lors de la mise à jour de l'allocation: ${reponse.code} ${reponse.body?.string()}"
                 throw Exception(erreur)
             }
 
             val corpsReponse = reponse.body?.string() ?: ""

             // 🔄 DÉCLENCHER L'ÉVÉNEMENT DE RAFRAÎCHISSEMENT
             BudgetEvents.onAllocationUpdated(allocationMensuelleId)
             println("[DEBUG] ajouterDepenseAllocation - Événement de rafraîchissement déclenché pour allocation: $allocationMensuelleId")

             Result.success(Unit)
         } catch (e: Exception) {

                          Result.failure(e)
         }
     }

     /**
      * Annule une dépense sur une allocation mensuelle.
      * Ajoute le montant au solde et le soustrait des dépenses.
      */
     override suspend fun annulerDepenseAllocation(allocationMensuelleId: String, montantDepense: Double): Result<Unit> = withContext(Dispatchers.IO) {
         try {
             val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
             val urlBase = client.obtenirUrlBaseActive()

             val allocation = recupererAllocationParId(allocationMensuelleId).getOrNull()
                 ?: throw Exception("Allocation non trouvée")
             
             // 2. Calculer les nouveaux montants
             val nouveauSoldeBrut = allocation.solde + montantDepense  // Addition au solde
             // Si le solde est très proche de zéro (positif ou négatif), le mettre à 0
             val nouveauSolde = if (kotlin.math.abs(nouveauSoldeBrut) < 0.001) 0.0 else nouveauSoldeBrut
             val nouvelleDépense = allocation.depense - montantDepense  // Soustraction des dépenses existantes
             
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
                 val erreur = "Erreur lors de la mise à jour de l'allocation: ${reponse.code} ${reponse.body?.string()}"
                 throw Exception(erreur)
             }

             val corpsReponse = reponse.body?.string() ?: ""

             // 🔄 DÉCLENCHER L'ÉVÉNEMENT DE RAFRAÎCHISSEMENT
             BudgetEvents.onAllocationUpdated(allocationMensuelleId)
             println("[DEBUG] annulerDepenseAllocation - Événement de rafraîchissement déclenché pour allocation: $allocationMensuelleId")

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
 
             // Calculer le premier jour du mois et le dernier jour du mois
             val calendrier = Calendar.getInstance().apply {
                 time = mois
                 set(Calendar.DAY_OF_MONTH, 1)
                 set(Calendar.HOUR_OF_DAY, 0)
                 set(Calendar.MINUTE, 0)
                 set(Calendar.SECOND, 0)
                 set(Calendar.MILLISECOND, 0)
             }
             val premierJourMois = calendrier.time

             val dernierJourMois = Calendar.getInstance().apply {
                 time = premierJourMois
                 add(Calendar.MONTH, 1)
                 add(Calendar.DAY_OF_MONTH, -1)
                 set(Calendar.HOUR_OF_DAY, 23)
                 set(Calendar.MINUTE, 59)
                 set(Calendar.SECOND, 59)
                 set(Calendar.MILLISECOND, 999)
             }.time

             val dateDebutFormatee = formateurDate.format(premierJourMois)
             val dateFinFormatee = formateurDate.format(dernierJourMois)

             // Utiliser un filtre exact avec des plages de dates au lieu de l'opérateur ~
             val filtre = "utilisateur_id = '$utilisateurId' && enveloppe_id = '$enveloppeId' && mois >= '$dateDebutFormatee' && mois <= '$dateFinFormatee'"
             val url = "$urlBase/api/collections/${Collections.ALLOCATIONS}/records?filter=${URLEncoder.encode(filtre, "UTF-8")}&perPage=1"

             println("[DEBUG] recupererAllocationMensuelle - Recherche allocation pour enveloppeId: $enveloppeId")
             println("[DEBUG] recupererAllocationMensuelle - Filtre CORRIGÉ: $filtre")
             println("[DEBUG] recupererAllocationMensuelle - URL: $url")

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
 
             println("[DEBUG] recupererAllocationMensuelle - Nombre d'allocations trouvées: ${items.size()}")

             if (items.size() > 0) {
                 val allocation = deserialiserAllocation(items[0].asJsonObject)
                 println("[DEBUG] recupererAllocationMensuelle - Allocation trouvée: ID=${allocation.id}, enveloppeId=${allocation.enveloppeId}")

                 // Vérification de sécurité : s'assurer que l'allocation appartient bien à la bonne enveloppe
                 if (allocation.enveloppeId == enveloppeId) {
                     Result.success(allocation)
                 } else {
                     println("[DEBUG] recupererAllocationMensuelle - ERREUR: L'allocation trouvée appartient à une autre enveloppe!")
                     Result.success(null)
                 }
             } else {
                 println("[DEBUG] recupererAllocationMensuelle - Aucune allocation trouvée")
                 Result.success(null)
             }
         } catch (e: Exception) {
             println("[DEBUG] recupererAllocationMensuelle - Erreur: ${e.message}")
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
             

             
             Result.success(allocationCreee)
         } catch (e: Exception) {

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

         
         // Nettoyer la date (enlever le .000Z si présent)
         val dateClean = moisString.replace(".000Z", "").replace("T", " ")

         
         val dateParsee = formateurDate.parse(dateClean) ?: Date()

 
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

     /**
      * Récupère toutes les allocations mensuelles pour une enveloppe donnée.
      */
     override suspend fun recupererAllocationsEnveloppe(enveloppeId: String): Result<List<AllocationMensuelle>> = withContext(Dispatchers.IO) {
         try {
             val token = client.obtenirToken() ?: throw Exception("Token manquant")
             val urlBase = client.obtenirUrlBaseActive()

             // Filtre pour cette enveloppe
             val filtre = URLEncoder.encode("enveloppe_id = '$enveloppeId'", "UTF-8")
             val url = "$urlBase/api/collections/${Collections.ALLOCATIONS}/records?filter=$filtre&perPage=500&sort=-mois"
             
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
             val jsonObject = JsonParser.parseString(data).asJsonObject
             val items = jsonObject.getAsJsonArray("items")

             val allocations = mutableListOf<AllocationMensuelle>()
             items.forEach { item ->
                 val allocation = deserialiserAllocation(item.asJsonObject.toString())
                 allocations.add(allocation)
             }

             Result.success(allocations)
         } catch (e: Exception) {
             Result.failure(e)
         }
     }
 }
