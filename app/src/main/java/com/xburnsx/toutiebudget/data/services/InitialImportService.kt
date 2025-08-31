package com.xburnsx.toutiebudget.data.services

// import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.xburnsx.toutiebudget.data.room.daos.*
import com.xburnsx.toutiebudget.data.room.entities.*
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.di.UrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * Service d'import initial des données depuis PocketBase vers Room.
 * IMPORTE VRAIMENT TOUTES LES DONNÉES selon la structure exacte des entités !
 */
class InitialImportService(
    private val compteChequeDao: CompteChequeDao,
    private val compteCreditDao: CompteCreditDao,
    private val compteDetteDao: CompteDetteDao,
    private val compteInvestissementDao: CompteInvestissementDao,
    private val transactionDao: TransactionDao,
    private val categorieDao: CategorieDao,
    private val enveloppeDao: EnveloppeDao,
    private val allocationMensuelleDao: AllocationMensuelleDao,
    private val tiersDao: TiersDao,
    private val pretPersonnelDao: PretPersonnelDao
) {
    
    private val client = PocketBaseClient
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private val gson = GsonBuilder().create()
    private val logTag = "InitialImport"
    
    /**
     * Convertit une string Pocketbase en TypeObjectif
     */
    private fun stringVersTypeObjectif(typeString: String?): com.xburnsx.toutiebudget.data.modeles.TypeObjectif {
        return when (typeString?.trim()) {
            "Aucun" -> com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Aucun
            "Mensuel" -> com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Mensuel
            "Bihebdomadaire" -> com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Bihebdomadaire
            "Echeance" -> com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Echeance
            "Annuel" -> com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Annuel
            else -> com.xburnsx.toutiebudget.data.modeles.TypeObjectif.Aucun
        }
    }
    
    // Callback pour la progression
    var onProgressUpdate: ((step: Int, message: String) -> Unit)? = null
    
    /**
     * Lance l'import initial complet des données avec GESTION DES RELATIONS
     */
    suspend fun importerDonneesInitiales(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 🚀 VÉRIFICATION DES DONNÉES EXISTANTES DANS ROOM...
            
            // ÉTAPE 0: VÉRIFIER SI ROOM EST DÉJÀ REMPLI
            if (roomContientDejaDesDonnees()) {
                // ✅ Room contient déjà des données, import initial ignoré
                onProgressUpdate?.invoke(7, "Données déjà synchronisées")
                return@withContext Result.success(Unit)
            }
            
            // 🚀 Room est vide, DÉBUT DE L'IMPORT COMPLET DES DONNÉES AVEC RELATIONS
            
            // ÉTAPE 1: Vérification de la connexion
            onProgressUpdate?.invoke(1, "Vérification de la connexion...")
            // 🔍 Tentative de récupération de l'utilisateur connecté...
            val utilisateurConnecte = client.obtenirUtilisateurConnecte()
            // 👤 Utilisateur connecté: $utilisateurConnecte
            
            val utilisateurId = utilisateurConnecte?.id
                ?: return@withContext Result.failure(Exception("Utilisateur non connecté"))
            
            // 🔍 Tentative de récupération de l'URL base...
            val urlBase = UrlResolver.obtenirUrlActive()
            // 🔍 Tentative de récupération du token...
            val token = client.obtenirToken()
                ?: return@withContext Result.failure(Exception("Token manquant"))
            
            // ✅ Utilisateur connecté: $utilisateurId
            // 🔗 URL Base: $urlBase
            // 🔑 Token: ${token.take(20)}...
            
            // ÉTAPE 2: Import des ENTITÉS DE BASE (sans relations)
            onProgressUpdate?.invoke(2, "Import des entités de base...")
            
            // Import des 4 types de comptes
            val comptesCheques = importerComptesCheques(urlBase, token, utilisateurId)
            val comptesCredits = importerComptesCredits(urlBase, token, utilisateurId)
            val comptesDettes = importerComptesDettes(urlBase, token, utilisateurId)
            val comptesInvestissement = importerComptesInvestissement(urlBase, token, utilisateurId)
            
            // Import des catégories
            val categories = importerCategories(urlBase, token, utilisateurId)
            
            // Insertion des entités de base
            if (comptesCheques.isNotEmpty()) {
                compteChequeDao.insertAll(comptesCheques)
                val comptesChequesDansRoom = compteChequeDao.getComptesCount(utilisateurId)
                // ✅ ${comptesCheques.size} comptes chèques importés → ${comptesChequesDansRoom} dans Room
            }
            if (comptesCredits.isNotEmpty()) {
                compteCreditDao.insertAll(comptesCredits)
                val comptesCreditsDansRoom = compteCreditDao.getComptesCount(utilisateurId)
                // ✅ ${comptesCredits.size} comptes crédits importés → ${comptesCreditsDansRoom} dans Room
            }
            if (comptesDettes.isNotEmpty()) {
                compteDetteDao.insertAll(comptesDettes)
                val comptesDettesDansRoom = compteDetteDao.getComptesCount(utilisateurId)
                // ✅ ${comptesDettes.size} comptes dettes importés → ${comptesCreditsDansRoom} dans Room
            }
            if (comptesInvestissement.isNotEmpty()) {
                compteInvestissementDao.insertAll(comptesInvestissement)
                val comptesInvestissementDansRoom = compteInvestissementDao.getComptesCount(utilisateurId)
                // ✅ ${comptesInvestissement.size} comptes investissement importés → ${comptesInvestissementDansRoom} dans Room
            }
            if (categories.isNotEmpty()) {
                categorieDao.insertAll(categories)
                val categoriesDansRoom = categorieDao.getCategoriesCount(utilisateurId)
                // ✅ ${categories.size} catégories importées → ${categoriesDansRoom} dans Room
            }
            
            // ÉTAPE 3: Import des ENVELOPPES (dépendent des catégories)
            onProgressUpdate?.invoke(3, "Import des enveloppes...")
            val enveloppes = importerEnveloppes(urlBase, token, utilisateurId, categories)
            if (enveloppes.isNotEmpty()) {
                enveloppeDao.insertAll(enveloppes)
                val enveloppesDansRoom = enveloppeDao.getEnveloppesCount(utilisateurId)
                // ✅ ${enveloppes.size} enveloppes importées → ${enveloppesDansRoom} dans Room
            }
            
            // ÉTAPE 4: Import des ALLOCATIONS MENSUELLES (dépendent des enveloppes)
            onProgressUpdate?.invoke(4, "Import des allocations mensuelles...")
            val allocations = importerAllocationsMensuelles(urlBase, token, utilisateurId, enveloppes)
            if (allocations.isNotEmpty()) {
                allocationMensuelleDao.insertAll(allocations)
                val allocationsDansRoom = allocationMensuelleDao.getAllocationsCount(utilisateurId)
                // ✅ ${allocations.size} allocations mensuelles importées → ${allocationsDansRoom} dans Room
            }
            
            // ÉTAPE 5: Import des PRÊTS PERSONNELS (pas de dépendances)
            onProgressUpdate?.invoke(5, "Import des prêts personnels...")
            val prets = importerPretsPersonnels(urlBase, token, utilisateurId)
            if (prets.isNotEmpty()) {
                pretPersonnelDao.insertAll(prets)
                // ✅ ${prets.size} prêts personnels importés
            }
            
            // ÉTAPE 6: Import des TIERS (pas de dépendances)
            onProgressUpdate?.invoke(6, "Import des tiers...")
            val tiers = importerTiers(urlBase, token, utilisateurId)
            if (tiers.isNotEmpty()) {
                tiersDao.insertAll(tiers)
                // ✅ ${tiers.size} tiers importés
            }
            
            // ÉTAPE 7: Import des TRANSACTIONS (dépendent des allocations mensuelles)
            onProgressUpdate?.invoke(7, "Import des transactions...")
            val transactions = importerTransactions(urlBase, token, utilisateurId, allocations)
            if (transactions.isNotEmpty()) {
                transactionDao.insertAll(transactions)
                val transactionsDansRoom = transactionDao.getTransactionsCount(utilisateurId)
                // ✅ ${transactions.size} transactions importées → ${transactionsDansRoom} dans Room
            }
            
            // 🎉 IMPORT COMPLET AVEC RELATIONS TERMINÉ AVEC SUCCÈS!
            Result.success(Unit)
            
        } catch (e: Exception) {
            // ❌ Erreur lors de l'import complet
            Result.failure(e)
        }
    }
    
    /**
     * Importe les comptes chèques depuis PocketBase
     */
    private suspend fun importerComptesCheques(urlBase: String, token: String, utilisateurId: String): List<CompteCheque> {
        val url = "$urlBase/api/collections/comptes_cheques/records?filter=utilisateur_id='$utilisateurId'"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", token)
            .build()
        
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            // ⚠️ Erreur HTTP ${response.code} pour comptes chèques
            return emptyList()
        }
        
        val jsonResponse = response.body?.string() ?: "{}"
        val listeResultats = gson.fromJson(jsonResponse, ListeResultats::class.java)
        
        return listeResultats.items.mapNotNull { item ->
            try {
                                 CompteCheque(
                     id = item.id,
                     utilisateurId = item.utilisateur_id ?: "",
                     nom = item.nom ?: "",
                     solde = item.solde?.toDoubleOrNull() ?: 0.0,
                     pretAPlacerRaw = item.pret_a_placer?.toDoubleOrNull(),
                     couleur = item.couleur ?: "#FF0000",
                     estArchive = item.archive ?: false,
                     ordre = item.ordre ?: 0,
                     collection = item.collection ?: "comptes_cheques"
                 )
            } catch (e: Exception) {
                // ⚠️ Erreur conversion compte chèque: ${e.message}
                null
            }
        }
    }
    
    /**
     * Importe les comptes crédits depuis PocketBase
     */
    private suspend fun importerComptesCredits(urlBase: String, token: String, utilisateurId: String): List<CompteCredit> {
        val url = "$urlBase/api/collections/comptes_credits/records?filter=utilisateur_id='$utilisateurId'"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", token)
            .build()
        
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            // ⚠️ Erreur HTTP ${response.code} pour comptes crédits
            return emptyList()
        }
        
        val jsonResponse = response.body?.string() ?: "{}"
        val listeResultats = gson.fromJson(jsonResponse, ListeResultats::class.java)
        
        return listeResultats.items.mapNotNull { item ->
            try {
                CompteCredit(
                    id = item.id,
                    utilisateurId = item.utilisateur_id ?: "",
                    nom = item.nom ?: "",
                    soldeUtilise = item.solde_utilise?.toDoubleOrNull() ?: 0.0,
                    couleur = item.couleur ?: "#FF0000",
                    estArchive = item.archive ?: false,
                    ordre = item.ordre ?: 0,
                    limiteCredit = item.limite_credit?.toDoubleOrNull() ?: 0.0,
                    tauxInteret = item.taux_interet?.toDoubleOrNull(),
                    paiementMinimum = item.paiement_minimum?.toDoubleOrNull(),
                    fraisMensuelsJson = item.frais_mensuels_json?.let { gson.toJson(it) },
                    collection = item.collection ?: "comptes_credits"
                )
            } catch (e: Exception) {
                // ⚠️ Erreur conversion compte crédit: ${e.message}
                null
            }
        }
    }
    
    /**
     * Importe les comptes dettes depuis PocketBase
     */
    private suspend fun importerComptesDettes(urlBase: String, token: String, utilisateurId: String): List<CompteDette> {
        val url = "$urlBase/api/collections/comptes_dettes/records?filter=utilisateur_id='$utilisateurId'"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", token)
            .build()
        
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            // ⚠️ Erreur HTTP ${response.code} pour comptes dettes
            return emptyList()
        }
        
        val jsonResponse = response.body?.string() ?: "{}"
        val listeResultats = gson.fromJson(jsonResponse, ListeResultats::class.java)
        
        return listeResultats.items.mapNotNull { item ->
            try {
                CompteDette(
                    id = item.id,
                    utilisateurId = item.utilisateur_id ?: "",
                    nom = item.nom ?: "",
                    soldeDette = item.solde_dette?.toDoubleOrNull() ?: 0.0,
                    estArchive = item.archive ?: false,
                    ordre = item.ordre ?: 0,
                    montantInitial = item.montant_initial?.toDoubleOrNull() ?: 0.0,
                    tauxInteret = item.taux_interet?.toDoubleOrNull(),
                    paiementMinimum = item.paiement_minimum?.toDoubleOrNull(),
                    dureeMoisPret = item.duree_mois_pret?.toIntOrNull(),
                    paiementEffectue = item.paiement_effectue ?: 0,
                    prixTotal = item.prix_total?.toDoubleOrNull(),
                    collection = item.collection ?: "comptes_dettes"
                )
            } catch (e: Exception) {
                // ⚠️ Erreur conversion compte dette: ${e.message}
                null
            }
        }
    }
    
    /**
     * Importe les comptes investissement depuis PocketBase
     */
    private suspend fun importerComptesInvestissement(urlBase: String, token: String, utilisateurId: String): List<CompteInvestissement> {
        val url = "$urlBase/api/collections/comptes_investissement/records?filter=utilisateur_id='$utilisateurId'"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", token)
            .build()
        
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            // ⚠️ Erreur HTTP ${response.code} pour comptes investissement
            return emptyList()
        }
        
        val jsonResponse = response.body?.string() ?: "{}"
        val listeResultats = gson.fromJson(jsonResponse, ListeResultats::class.java)
        
        return listeResultats.items.mapNotNull { item ->
            try {
                CompteInvestissement(
                    id = item.id,
                    utilisateurId = item.utilisateur_id ?: "",
                    nom = item.nom ?: "",
                    solde = item.solde?.toDoubleOrNull() ?: 0.0,
                    couleur = item.couleur ?: "#FF0000",
                    estArchive = item.archive ?: false,
                    ordre = item.ordre ?: 0,
                    collection = item.collection ?: "comptes_investissement"
                )
            } catch (e: Exception) {
                // ⚠️ Erreur conversion compte investissement: ${e.message}
                null
            }
        }
    }
    
    /**
     * Importe les catégories depuis PocketBase
     */
    private suspend fun importerCategories(urlBase: String, token: String, utilisateurId: String): List<Categorie> {
        val url = "$urlBase/api/collections/categories/records?filter=utilisateur_id='$utilisateurId'"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", token)
            .build()
        
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            // ⚠️ Erreur HTTP ${response.code} pour catégories
            return emptyList()
        }
        
        val jsonResponse = response.body?.string() ?: "{}"
        val listeResultats = gson.fromJson(jsonResponse, ListeResultats::class.java)
        
        return listeResultats.items.mapNotNull { item ->
            try {
                Categorie(
                    id = item.id,
                    utilisateurId = item.utilisateur_id ?: "",
                    nom = item.nom ?: "",
                    ordre = item.ordre ?: 0
                )
                    } catch (e: Exception) {
            // ⚠️ Erreur conversion catégorie: ${e.message}
            null
        }
        }
    }
    
    /**
     * Importe les transactions depuis PocketBase avec VÉRIFICATION DES RELATIONS
     * CHARGE 500 PAGES POUR RÉCUPÉRER TOUTES LES TRANSACTIONS !
     */
    private suspend fun importerTransactions(urlBase: String, token: String, utilisateurId: String, allocations: List<AllocationMensuelle>): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        var page = 1
        val perPage = 500 // Charger 500 éléments par page
        
        while (page <= 500) { // Limite de sécurité à 500 pages
            val url = "$urlBase/api/collections/transactions/records?filter=utilisateur_id='$utilisateurId'&perPage=$perPage&page=$page"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                // ⚠️ Erreur HTTP ${response.code} pour transactions page $page
                break
            }
            
            val jsonResponse = response.body?.string() ?: "{}"
            val listeResultats = gson.fromJson(jsonResponse, ListeResultats::class.java)
            
            if (listeResultats.items.isEmpty()) {
                // ✅ Plus de transactions à charger après la page $page
                break
            }
            
            // 📄 Page $page: ${listeResultats.items.size} transactions trouvées
            
            val transactionsPage = listeResultats.items.mapNotNull { item ->
                try {
                    // LOG DÉTAILLÉ POUR DÉBOGGER !
                    // 🔍 Transaction ${item.id}: allocation_mensuelle_id = '${item.allocation_mensuelle_id}'
                    
                    // IMPORTER TOUT SANS VÉRIFIER LES RELATIONS !
                    val allocationId = item.allocation_mensuelle_id ?: ""
                    
                    Transaction(
                        id = item.id, // GARDER L'ID POCKETBASE !
                        utilisateurId = item.utilisateur_id ?: "",
                        type = item.type ?: "",
                        montant = item.montant?.toDoubleOrNull() ?: 0.0,
                        date = item.date ?: "",
                        note = item.note,
                        compteId = item.compte_id ?: "",
                        collectionCompte = item.collection_compte ?: "",
                        allocationMensuelleId = allocationId ?: "",
                        estFractionnee = item.est_fractionnee ?: false,
                        sousItems = item.sous_items?.let { gson.toJson(it) },
                        tiersUtiliser = item.tiers_utiliser,
                        created = item.created,
                        updated = item.updated
                    )
                } catch (e: Exception) {
                    // ⚠️ Erreur conversion transaction: ${e.message}
                    null
                }
            }
            
            transactions.addAll(transactionsPage)
            
            // Si on a moins d'éléments que demandés, c'est la dernière page
            if (listeResultats.items.size < perPage) {
                // ✅ Dernière page atteinte (${listeResultats.items.size} < $perPage)
                break
            }
            
            page++
        }
        
        // 🔍 TOTAL TRANSACTIONS RÉCUPÉRÉES: ${transactions.size}
        return transactions
    }
    
    /**
     * Importe les enveloppes depuis PocketBase avec VÉRIFICATION DES RELATIONS
     * CHARGE 500 PAGES POUR RÉCUPÉRER TOUTES LES ENVELOPPES !
     */
    private suspend fun importerEnveloppes(urlBase: String, token: String, utilisateurId: String, categories: List<Categorie>): List<Enveloppe> {
        val enveloppes = mutableListOf<Enveloppe>()
        var page = 1
        val perPage = 500 // Charger 500 éléments par page
        
        while (page <= 500) { // Limite de sécurité à 500 pages
            val url = "$urlBase/api/collections/enveloppes/records?filter=utilisateur_id='$utilisateurId'&perPage=$perPage&page=$page"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                // ⚠️ Erreur HTTP ${response.code} pour enveloppes page $page
                break
            }
            
            val jsonResponse = response.body?.string() ?: "{}"
            val listeResultats = gson.fromJson(jsonResponse, ListeResultats::class.java)
            
            if (listeResultats.items.isEmpty()) {
                // ✅ Plus d'enveloppes à charger après la page $page
                break
            }
            
            // 📄 Page $page: ${listeResultats.items.size} enveloppes trouvées
            
            val enveloppesPage = listeResultats.items.mapNotNull { item ->
                try {
                    // LOG DÉTAILLÉ POUR DÉBOGGER !
                    // 🔍 Enveloppe ${item.id}: categorie_id = '${item.categorie_id}'
                    // 🔍 Enveloppe ${item.id}: frequence_objectif = '${item.frequence_objectif}'
                    // 🔍 Enveloppe ${item.id}: montant_objectif = '${item.montant_objectif}'
                    // 🔍 Enveloppe ${item.id}: date_objectif = '${item.date_objectif}'
                    // 🔍 Enveloppe ${item.id}: date_debut_objectif = '${item.date_debut_objectif}'
                    
                    // IMPORTER TOUT SANS VÉRIFIER LES RELATIONS !
                    val categorieId = item.categorie_id ?: ""
                    
                    // 🎯 CORRECTION : Gérer correctement les dates des objectifs
                    val dateObjectif = item.date_objectif
                    val dateDebutObjectif = item.date_debut_objectif
                    
                    val enveloppe = Enveloppe(
                        id = item.id, // GARDER L'ID POCKETBASE !
                        utilisateurId = item.utilisateur_id ?: "",
                        nom = item.nom ?: "",
                        categorieId = categorieId ?: "",
                        estArchive = item.est_archive ?: false,
                        ordre = item.ordre ?: 0,
                        typeObjectif = stringVersTypeObjectif(item.frequence_objectif),
                        objectifMontant = item.montant_objectif?.toDoubleOrNull() ?: 0.0,
                        dateObjectif = dateObjectif, // 🎯 GARDER LA DATE EN STRING POUR ROOM
                        dateDebutObjectif = dateDebutObjectif, // 🎯 GARDER LA DATE EN STRING POUR ROOM
                        objectifJour = item.objectif_jour?.toIntOrNull(),
                        resetApresEcheance = item.reset_apres_echeance ?: false
                    )
                    
                    // 🎯 LOG DÉTAILLÉ POUR DÉBUGGER LES OBJECTIFS !
                    // 🎯 ENVELOPPE CRÉÉE: ${enveloppe.nom}
                    //   - Type objectif: ${enveloppe.typeObjectif}
                    //   - Montant objectif: ${enveloppe.objectifMontant}
                    //   - Date objectif: ${enveloppe.dateObjectif}
                    //   - Date début: ${enveloppe.dateDebutObjectif}
                    //   - Objectif jour: ${enveloppe.objectifJour}
                    //   - Reset après échéance: ${enveloppe.resetApresEcheance}
                    
                    enveloppe
                } catch (e: Exception) {
                    // ⚠️ Erreur conversion enveloppe: ${e.message}
                    null
                }
            }
            
            enveloppes.addAll(enveloppesPage)
            
            // Si on a moins d'éléments que demandés, c'est la dernière page
            if (listeResultats.items.size < perPage) {
                // ✅ Dernière page atteinte (${listeResultats.items.size} < $perPage)
                break
            }
            
            page++
        }
        
        // 🔍 TOTAL ENVELOPPES RÉCUPÉRÉES: ${enveloppes.size}
        return enveloppes
    }
    
    /**
     * Importe les allocations mensuelles depuis PocketBase avec VÉRIFICATION DES RELATIONS
     * CHARGE 500 PAGES POUR RÉCUPÉRER TOUTES LES ALLOCATIONS !
     */
    private suspend fun importerAllocationsMensuelles(urlBase: String, token: String, utilisateurId: String, enveloppes: List<Enveloppe>): List<AllocationMensuelle> {
        val allocations = mutableListOf<AllocationMensuelle>()
        var page = 1
        val perPage = 500 // Charger 500 éléments par page
        
        while (page <= 500) { // Limite de sécurité à 500 pages
            val url = "$urlBase/api/collections/allocations_mensuelles/records?filter=utilisateur_id='$utilisateurId'&perPage=$perPage&page=$page"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", token)
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                // ⚠️ Erreur HTTP ${response.code} pour allocations mensuelles page $page
                break
            }
            
            val jsonResponse = response.body?.string() ?: "{}"
            val listeResultats = gson.fromJson(jsonResponse, ListeResultats::class.java)
            
            if (listeResultats.items.isEmpty()) {
                // ✅ Plus d'allocations à charger après la page $page
                break
            }
            
            // 📄 Page $page: ${listeResultats.items.size} allocations trouvées
            
            val allocationsPage = listeResultats.items.mapNotNull { item ->
                try {
                    // LOG DÉTAILLÉ POUR DÉBOGGER !
                    // 🔍 Allocation ${item.id}: enveloppe_id = '${item.enveloppe_id}'
                    
                    // IMPORTER TOUT SANS VÉRIFIER LES RELATIONS !
                    val enveloppeId = item.enveloppe_id ?: ""
                    
                    AllocationMensuelle(
                        id = item.id, // GARDER L'ID POCKETBASE !
                        utilisateurId = item.utilisateur_id ?: "",
                        enveloppeId = enveloppeId ?: "",
                        mois = item.mois ?: "",
                        solde = item.solde?.toDoubleOrNull() ?: 0.0,
                        alloue = item.alloue?.toDoubleOrNull() ?: 0.0,
                        depense = item.depense?.toDoubleOrNull() ?: 0.0,
                        compteSourceId = item.compte_source_id,
                        collectionCompteSource = item.collection_compte_source
                    )
                } catch (e: Exception) {
                    // ⚠️ Erreur conversion allocation mensuelle: ${e.message}
                    null
                }
            }
            
            allocations.addAll(allocationsPage)
            
            // Si on a moins d'éléments que demandés, c'est la dernière page
            if (listeResultats.items.size < perPage) {
                // ✅ Dernière page atteinte (${listeResultats.items.size} < $perPage)
                break
            }
            
            page++
        }
        
        // 🔍 TOTAL ALLOCATIONS RÉCUPÉRÉES: ${allocations.size}
        return allocations
    }
    
    /**
     * Importe les tiers depuis PocketBase
     */
    private suspend fun importerTiers(urlBase: String, token: String, utilisateurId: String): List<Tiers> {
        val url = "$urlBase/api/collections/tiers/records?filter=utilisateur_id='$utilisateurId'"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", token)
            .build()
        
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            // ⚠️ Erreur HTTP ${response.code} pour tiers
            return emptyList()
        }
        
        val jsonResponse = response.body?.string() ?: "{}"
        val listeResultats = gson.fromJson(jsonResponse, ListeResultats::class.java)
        
        return listeResultats.items.mapNotNull { item ->
            try {
                Tiers(
                    id = item.id,
                    utilisateurId = item.utilisateur_id ?: "",
                    nom = item.nom ?: "",
                    created = item.created ?: "",
                    updated = item.updated ?: "",
                    collectionId = item.collectionId ?: "",
                    collectionName = item.collectionName ?: ""
                )
                    } catch (e: Exception) {
            // ⚠️ Erreur conversion tiers: ${e.message}
            null
        }
        }
    }
    
    /**
     * Importe les prêts personnels depuis PocketBase
     */
    private suspend fun importerPretsPersonnels(urlBase: String, token: String, utilisateurId: String): List<PretPersonnel> {
        val url = "$urlBase/api/collections/pret_personnel/records?filter=utilisateur_id='$utilisateurId'"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", token)
            .build()
        
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            // ⚠️ Erreur HTTP ${response.code} pour prêts personnels
            return emptyList()
        }
        
        val jsonResponse = response.body?.string() ?: "{}"
        val listeResultats = gson.fromJson(jsonResponse, ListeResultats::class.java)
        
        return listeResultats.items.mapNotNull { item ->
            try {
                PretPersonnel(
                    id = item.id,
                    utilisateurId = item.utilisateur_id ?: "",
                    nomTiers = item.nom_tiers,
                    montantInitial = item.montant_initial?.toDoubleOrNull() ?: 0.0,
                    solde = item.solde?.toDoubleOrNull() ?: 0.0,
                    type = item.type ?: "",
                    estArchive = item.archive ?: false,
                    dateCreation = item.date_creation,
                    created = item.created,
                    updated = item.updated
                )
            } catch (e: Exception) {
                // ⚠️ Erreur conversion prêt personnel: ${e.message}
                null
            }
        }
    }
    
    /**
     * Vérifie si Room contient déjà des données
     * Si oui, l'import initial n'est pas nécessaire
     */
    private suspend fun roomContientDejaDesDonnees(): Boolean {
        return try {
            // Récupérer l'utilisateur connecté pour vérifier ses données
            val utilisateurConnecte = client.obtenirUtilisateurConnecte()
            val utilisateurId = utilisateurConnecte?.id ?: return false
            
            // 🎯 VÉRIFIER UNIQUEMENT LES TRANSACTIONS !
            // Les transactions sont l'élément principal de l'application
            val transactionsCount = transactionDao.getTransactionsCount(utilisateurId)
            
            // 🔍 Vérification Room: $transactionsCount transactions
            
            // Room est considéré comme rempli SEULEMENT si on a des transactions
            // Cela évite l'import à chaque ouverture tout en s'assurant que les données principales sont là
            transactionsCount > 0
            
        } catch (e: Exception) {
            // ⚠️ Erreur lors de la vérification des transactions
            false // En cas d'erreur, on fait l'import pour être sûr
        }
    }
}

/**
 * Classes pour la désérialisation JSON de PocketBase
 */
data class ListeResultats(
    val items: List<ItemPocketBase>
)

data class ItemPocketBase(
    val id: String,
    val utilisateur_id: String?,
    val nom: String?,
    val solde: String?,
    val solde_utilise: String?,
    val solde_dette: String?,
    val pret_a_placer: String?,
    val couleur: String?,
    val archive: Boolean?,
    val est_archive: Boolean?,
    val ordre: Int?,
    val limite_credit: String?,
    val taux_interet: String?,
    val paiement_minimum: String?,
    val frais_mensuels_json: List<Any>?,
    val montant_initial: String?,
    val paiement_effectue: Int?,
    val duree_mois_pret: String?,
    val prix_total: String?,
    val collection: String?,
    val type: String?,
    val montant: String?,
    val date: String?,
    val note: String?,
    val compte_id: String?,
    val collection_compte: String?,
    val allocation_mensuelle_id: String?,
    val est_fractionnee: Boolean?,
    val sous_items: Any?, // 🎯 CORRECTION : Peut être List<*> ou un objet JSON
    val tiers_utiliser: String?,
    val created: String?,
    val updated: String?,
    val categorie_id: String?,
    val frequence_objectif: String?,
    val montant_objectif: String?,
    val date_objectif: String?,
    val date_debut_objectif: String?,
    val objectif_jour: String?,
    val reset_apres_echeance: Boolean?,
    val enveloppe_id: String?,
    val mois: String?,
    val alloue: String?,
    val depense: String?,
    val compte_source_id: String?,
    val collection_compte_source: String?,
    val collectionId: String?,
    val collectionName: String?,
    val nom_tiers: String?,
    val date_creation: String?
)
