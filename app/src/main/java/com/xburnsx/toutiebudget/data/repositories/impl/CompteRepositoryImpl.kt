// chemin/simule: /data/repositories/impl/CompteRepositoryImpl.kt
// Dépendances: PocketBaseClient, Gson, Coroutines

package com.xburnsx.toutiebudget.data.repositories.impl

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xburnsx.toutiebudget.data.modeles.*
import com.xburnsx.toutiebudget.data.repositories.CompteRepository
import com.xburnsx.toutiebudget.di.AppModule
import com.xburnsx.toutiebudget.di.PocketBaseClient
import com.xburnsx.toutiebudget.di.UrlResolver
import com.xburnsx.toutiebudget.ui.budget.BudgetEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import com.xburnsx.toutiebudget.utils.MoneyFormatter

// Classe pour désérialiser la réponse paginée de PocketBase
data class ListeResultats<T>(
    val page: Int,
    val perPage: Int,
    val totalItems: Int,
    val totalPages: Int,
    val items: List<T>
)

class CompteRepositoryImpl : CompteRepository {

    private val client = PocketBaseClient
    private val gson = Gson()
    private val httpClient = okhttp3.OkHttpClient()

    // Noms des collections dans PocketBase
    private object Collections {
        const val CHEQUE = "comptes_cheques"
        const val CREDIT = "comptes_credits"
        const val DETTE = "comptes_dettes"
        const val INVESTISSEMENT = "comptes_investissement"
    }

    override suspend fun recupererTousLesComptes(): Result<List<Compte>> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.success(emptyList())
        }
        try {
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé."))

            // Crée une coroutine pour chaque appel réseau
            val deferredCheque = async { recupererComptesDeCollection<CompteCheque>(Collections.CHEQUE, utilisateurId) }
            val deferredCredit = async { recupererComptesDeCollection<CompteCredit>(Collections.CREDIT, utilisateurId) }
            val deferredDette = async { recupererComptesDeCollection<CompteDette>(Collections.DETTE, utilisateurId) }
            val deferredInvestissement = async { recupererComptesDeCollection<CompteInvestissement>(Collections.INVESTISSEMENT, utilisateurId) }

            // Attend que tous les appels soient terminés
            val listes = awaitAll(deferredCheque, deferredCredit, deferredDette, deferredInvestissement)

            // Aplatit la liste de listes en une seule liste et trie (inclut archivés)
            val tousLesComptes = listes.flatten().sortedBy { it.ordre }

            Result.success(tousLesComptes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend inline fun <reified T : Compte> recupererComptesDeCollection(collection: String, utilisateurId: String): List<T> = withContext(Dispatchers.IO) {
        val token = client.obtenirToken() ?: throw Exception("Token d'authentification manquant.")
        val urlBase = UrlResolver.obtenirUrlActive()

        // Filtre pour ne récupérer que les enregistrements de l'utilisateur connecté
        val filtreEncode = URLEncoder.encode("utilisateur_id = '$utilisateurId'", "UTF-8")
        val url = "$urlBase/api/collections/$collection/records?filter=$filtreEncode&perPage=100"

        val requete = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        val reponse = httpClient.newCall(requete).execute()
        if (!reponse.isSuccessful) {
            throw Exception("Erreur lors de la récupération de la collection '$collection': ${reponse.code} ${reponse.body?.string()}")
        }

        val corpsReponse = reponse.body!!.string()
        val typeReponse = TypeToken.getParameterized(ListeResultats::class.java, T::class.java).type
        val resultatPagine: ListeResultats<T> = gson.fromJson(corpsReponse, typeReponse)

        // Normaliser la propriété 'collection' pour éviter des nulls provenant du JSON
        resultatPagine.items.map { compte ->
            when (compte) {
                is CompteCheque -> compte.copy(collection = Collections.CHEQUE) as T
                is CompteCredit -> compte.copy(collection = Collections.CREDIT) as T
                is CompteDette -> compte.copy(collection = Collections.DETTE) as T
                is CompteInvestissement -> compte.copy(collection = Collections.INVESTISSEMENT) as T
                else -> compte
            }
        }
    }

    override suspend fun creerCompte(compte: Compte): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val collection = obtenirCollectionPourCompte(compte)
            val utilisateurId = client.obtenirUtilisateurConnecte()?.id
                ?: return@withContext Result.failure(Exception("ID utilisateur non trouvé pour la création."))

            // Injecte l'ID de l'utilisateur dans l'objet compte avant la sérialisation
            // Pour les comptes chèque, initialise pret_a_placer avec la valeur du solde
            val compteAvecUtilisateur = when(compte) {
                is CompteCheque -> compte.copy(
                    utilisateurId = utilisateurId,
                    pretAPlacerRaw = compte.solde // Initialiser pret_a_placer avec le solde
                )
                is CompteCredit -> compte.copy(utilisateurId = utilisateurId, collection = "comptes_credits")
                is CompteDette -> compte.copy(utilisateurId = utilisateurId)
                is CompteInvestissement -> compte.copy(utilisateurId = utilisateurId)
            }

            val corpsJson = gson.toJson(compteAvecUtilisateur)
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = UrlResolver.obtenirUrlActive()

            val requete = Request.Builder()
                .url("$urlBase/api/collections/$collection/records")
                .addHeader("Authorization", "Bearer $token")
                .post(corpsJson.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(requete).execute().use { reponse ->
                if (!reponse.isSuccessful) {
                    return@withContext Result.failure(Exception("Échec de la création: ${reponse.body?.string()}"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mettreAJourCompte(compte: Compte): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val collection = obtenirCollectionPourCompte(compte)
            val corpsJson = gson.toJson(compte)
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = UrlResolver.obtenirUrlActive()

            val requete = Request.Builder()
                .url("$urlBase/api/collections/$collection/records/${compte.id}")
                .addHeader("Authorization", "Bearer $token")
                .patch(corpsJson.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(requete).execute().use { reponse ->
                if (!reponse.isSuccessful) {
                    return@withContext Result.failure(Exception("Échec de la mise à jour: ${reponse.body?.string()}"))
                }
            }

            // 🔄 DÉCLENCHER LES ÉVÉNEMENTS DE RAFRAÎCHISSEMENT
            BudgetEvents.onCompteUpdated()

            // 🔄 DÉCLENCHER LE RAFRAÎCHISSEMENT DE LA PAGE DES COMPTES
            val realtimeService = AppModule.provideRealtimeSyncService()
            realtimeService.declencherMiseAJourComptes()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun supprimerCompte(compteId: String, collection: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = UrlResolver.obtenirUrlActive()

            val requete = Request.Builder()
                .url("$urlBase/api/collections/$collection/records/$compteId")
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            httpClient.newCall(requete).execute().use { reponse ->
                if (!reponse.isSuccessful) {
                    return@withContext Result.failure(Exception("Échec de la suppression: ${reponse.body?.string()}"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCompteById(compteId: String, collection: String): Compte? = withContext(Dispatchers.IO) {
        try {
            val result = recupererCompteParId(compteId, collection)
            result.getOrNull()
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun mettreAJourSolde(compteId: String, collection: String, nouveauSolde: Double) = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() ?: throw Exception("Token manquant")
            val urlBase = UrlResolver.obtenirUrlActive()

            // 🎯 ARRONDIR AUTOMATIQUEMENT LE NOUVEAU SOLDE
            val soldeArrondi = MoneyFormatter.roundAmount(nouveauSolde)
            
            // 🔧 DÉTECTER LE TYPE DE COMPTE POUR UTILISER LE BON CHAMP
            val nomChamp = when (collection) {
                Collections.CREDIT -> "solde_utilise" // Pour les cartes de crédit
                Collections.DETTE -> "solde_dette"   // Pour les dettes
                else -> "solde" // Pour tous les autres types de comptes
            }
            
            val donneesUpdate = mapOf(nomChamp to soldeArrondi)
            val corpsRequete = gson.toJson(donneesUpdate)

            println("🔍 DEBUG MISE À JOUR SOLDE:")
            println("  URL: $urlBase/api/collections/$collection/records/$compteId")
            println("  Collection: $collection")
            println("  ID Compte: $compteId")
            println("  Champ mis à jour: $nomChamp")
            println("  Nouvelle valeur: $soldeArrondi")
            println("  Corps de la requête (JSON): $corpsRequete")

            val requete = Request.Builder()
                .url("$urlBase/api/collections/$collection/records/$compteId")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .patch(corpsRequete.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(requete).execute().use { reponse ->
                println("  Code de réponse: ${reponse.code}")
                if (!reponse.isSuccessful) {
                    val erreurBody = reponse.body?.string()
                    println("  Corps de l'erreur: $erreurBody")
                    throw Exception("Erreur lors de la mise à jour: ${reponse.code} - $erreurBody")
                }
            }

            // 🔄 DÉCLENCHER LES ÉVÉNEMENTS DE RAFRAÎCHISSEMENT
            BudgetEvents.onCompteUpdated()

            // 🔄 DÉCLENCHER LE RAFRAÎCHISSEMENT DE LA PAGE DES COMPTES
            val realtimeService = AppModule.provideRealtimeSyncService()
            realtimeService.declencherMiseAJourComptes()
        } catch (e: Exception) {
            throw e
        }
    }

    // ===== NOUVELLES MÉTHODES POUR LES TRANSACTIONS =====

    override suspend fun mettreAJourSoldeAvecVariation(compteId: String, collectionCompte: String, variationSolde: Double): Result<Unit> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.failure(Exception("Utilisateur non connecté"))
        }

        try {
            val token = client.obtenirToken() 
                ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = UrlResolver.obtenirUrlActive()

            // 1. Récupérer le solde actuel
            val resultCompte = recupererCompteParId(compteId, collectionCompte)
            if (resultCompte.isFailure) {
                throw resultCompte.exceptionOrNull() ?: Exception("Impossible de récupérer le compte")
            }

            val compte = resultCompte.getOrNull() 
                ?: throw Exception("Compte non trouvé")

            // 2. Calculer le nouveau solde
            val nouveauSolde = compte.solde + variationSolde

            // 🎯 ARRONDIR AUTOMATIQUEMENT LE NOUVEAU SOLDE
            val soldeArrondi = MoneyFormatter.roundAmount(nouveauSolde)

            // 3. Préparer les données de mise à jour (seulement le solde pour cette méthode)
            // 🔧 DÉTECTER LE TYPE DE COMPTE POUR UTILISER LE BON CHAMP
            val nomChamp = when (collectionCompte) {
                Collections.CREDIT -> "solde_utilise" // Pour les cartes de crédit
                Collections.DETTE -> "solde_dette"   // Pour les dettes
                else -> "solde" // Pour tous les autres types de comptes
            }
            val donneesUpdate = mapOf(nomChamp to soldeArrondi)
            val corpsRequete = gson.toJson(donneesUpdate)

            println("🔍 DEBUG MISE À JOUR SOLDE AVEC VARIATION:")
            println("  URL: $urlBase/api/collections/$collectionCompte/records/$compteId")
            println("  Collection: $collectionCompte")
            println("  ID Compte: $compteId")
            println("  Champ mis à jour: $nomChamp")
            println("  Nouvelle valeur: $soldeArrondi")
            println("  Corps de la requête (JSON): $corpsRequete")

            val url = "$urlBase/api/collections/$collectionCompte/records/$compteId"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .patch(corpsRequete.toRequestBody("application/json".toMediaType()))
                .build()

            val reponse = httpClient.newCall(requete).execute()
            println("  Code de réponse: ${reponse.code}")
            if (!reponse.isSuccessful) {
                val erreurBody = reponse.body?.string()
                println("  Corps de l'erreur: $erreurBody")
                throw Exception("Erreur lors de la mise à jour du solde: ${reponse.code} - $erreurBody")
            }

            // 🔄 DÉCLENCHER LES ÉVÉNEMENTS DE RAFRAÎCHISSEMENT
            BudgetEvents.onCompteUpdated()

            // 🔄 DÉCLENCHER LE RAFRAÎCHISSEMENT DE LA PAGE DES COMPTES
            val realtimeService = AppModule.provideRealtimeSyncService()
            realtimeService.declencherMiseAJourComptes()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recupererCompteParId(compteId: String, collectionCompte: String): Result<Compte> = withContext(Dispatchers.IO) {
        try {
            val token = client.obtenirToken() 
                ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = UrlResolver.obtenirUrlActive()

            val url = "$urlBase/api/collections/$collectionCompte/records/$compteId"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val reponse = httpClient.newCall(requete).execute()

            // Traiter les erreurs 404 comme des cas normaux (compte non trouvé dans cette collection)
            if (reponse.code == 404) {
                return@withContext Result.failure(Exception("Compte non trouvé dans la collection $collectionCompte"))
            }

            if (!reponse.isSuccessful) {
                throw Exception("Erreur lors de la récupération du compte: ${reponse.code}")
            }

            val corpsReponse = reponse.body!!.string()
            val compte = deserialiserCompte(corpsReponse, collectionCompte)
                ?: throw Exception("Erreur lors de la désérialisation du compte")

            return@withContext Result.success(compte)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    // ===== MÉTHODES UTILITAIRES =====

    private fun obtenirCollectionPourCompte(compte: Compte): String {
        return when (compte) {
            is CompteCheque -> Collections.CHEQUE
            is CompteCredit -> Collections.CREDIT
            is CompteDette -> Collections.DETTE
            is CompteInvestissement -> Collections.INVESTISSEMENT
        }
    }

    override suspend fun recupererCompteParIdToutesCollections(compteId: String): Result<Compte> = withContext(Dispatchers.IO) {
        val collections = listOf(
            Collections.CHEQUE,
            Collections.CREDIT,
            Collections.DETTE,
            Collections.INVESTISSEMENT
        )

        for (collection in collections) {
            val resultat = recupererCompteParId(compteId, collection)
            if (resultat.isSuccess) {
                return@withContext resultat // Retourne le compte dès qu'il est trouvé
            }
        }

        return@withContext Result.failure(Exception("Aucun compte trouvé avec l'ID $compteId dans toutes les collections."))
    }

    private fun deserialiserCompte(json: String, collection: String): Compte? {
        return try {
            val compte = when (collection) {
                Collections.CHEQUE -> gson.fromJson(json, CompteCheque::class.java)
                Collections.CREDIT -> gson.fromJson(json, CompteCredit::class.java)
                Collections.DETTE -> gson.fromJson(json, CompteDette::class.java)
                Collections.INVESTISSEMENT -> gson.fromJson(json, CompteInvestissement::class.java)
                else -> null
            }

            // IMPORTANT: Définir la collection sur le compte désérialisé
            compte?.let {
                when (it) {
                    is CompteCheque -> it.copy(collection = collection)
                    is CompteCredit -> it.copy(collection = collection)
                    is CompteDette -> it.copy(collection = collection)
                    is CompteInvestissement -> it.copy(collection = collection)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun mettreAJourSoldeAvecVariationEtPretAPlacer(
        compteId: String,
        collectionCompte: String,
        variationSolde: Double,
        mettreAJourPretAPlacer: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!client.estConnecte()) {
            return@withContext Result.failure(Exception("Utilisateur non connecté"))
        }

        try {
            val token = client.obtenirToken()
            if (token == null) {
                return@withContext Result.failure(Exception("Token manquant"))
            }

            val urlBase = UrlResolver.obtenirUrlActive()

            // 1. Récupérer le compte actuel
            val resultCompte = recupererCompteParId(compteId, collectionCompte)
            if (resultCompte.isFailure) {
                throw resultCompte.exceptionOrNull() ?: Exception("Impossible de récupérer le compte")
            }

            val compte = resultCompte.getOrNull()
            if (compte == null) {
                throw Exception("Compte non trouvé")
            }

            // 2. Calculer le nouveau solde
            val nouveauSoldeBrut = compte.solde + variationSolde
            // 🎯 ARRONDIR AUTOMATIQUEMENT LE NOUVEAU SOLDE
            val nouveauSolde = MoneyFormatter.roundAmount(nouveauSoldeBrut)

            // 3. Préparer les données de mise à jour
            val champSolde = when (collectionCompte) {
                Collections.CREDIT -> "solde_utilise"
                Collections.DETTE -> "solde_dette"
                else -> "solde"
            }

            val donneesUpdate = if (mettreAJourPretAPlacer && collectionCompte == Collections.CHEQUE && compte is CompteCheque) {
                // Pour les comptes chèque, mettre à jour aussi pret_a_placer si demandé
                val nouveauPretAPlacer = compte.pretAPlacer + variationSolde
                mapOf(
                    champSolde to nouveauSolde,
                    "pret_a_placer" to nouveauPretAPlacer
                )
            } else {
                // Sinon, mettre à jour seulement le solde (avec champ adapté au type)
                mapOf(champSolde to nouveauSolde)
            }
            val corpsRequete = gson.toJson(donneesUpdate)

            val url = "$urlBase/api/collections/$collectionCompte/records/$compteId"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .patch(corpsRequete.toRequestBody("application/json".toMediaType()))
                .build()

            val reponse = httpClient.newCall(requete).execute()

            if (!reponse.isSuccessful) {
                val messageErreur = reponse.body?.string() ?: "Erreur inconnue"
                throw Exception("Erreur lors de la mise à jour: ${reponse.code} $messageErreur")
            }

            // 🔄 DÉCLENCHER LES ÉVÉNEMENTS DE RAFRAÎCHISSEMENT
            BudgetEvents.onCompteUpdated()

            // 🔄 DÉCLENCHER LE RAFRAÎCHISSEMENT DE LA PAGE DES COMPTES
            val realtimeService = AppModule.provideRealtimeSyncService()
            realtimeService.declencherMiseAJourComptes()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mettreAJourPretAPlacerSeulement(
        compteId: String,
        variationPretAPlacer: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        
        if (!client.estConnecte()) {
            return@withContext Result.failure(Exception("Utilisateur non connecté"))
        }

        try {
            val token = client.obtenirToken()
                ?: return@withContext Result.failure(Exception("Token manquant"))
            val urlBase = UrlResolver.obtenirUrlActive()

            // 1. Récupérer le compte actuel (doit être un CompteCheque)
            val resultCompte = recupererCompteParId(compteId, Collections.CHEQUE)
            if (resultCompte.isFailure) {
                throw resultCompte.exceptionOrNull() ?: Exception("Impossible de récupérer le compte")
            }

            val compte = resultCompte.getOrNull() as? CompteCheque
                ?: throw Exception("Le compte n'est pas un compte chèque ou n'existe pas")
            
            // 2. Calculer le nouveau montant prêt à placer
            val nouveauPretAPlacer = compte.pretAPlacer + variationPretAPlacer

            // 3. Vérifier que le montant ne devient pas négatif
            if (nouveauPretAPlacer < 0) {
                throw Exception("Montant prêt à placer insuffisant")
            }

            // 4. Préparer les données de mise à jour (seulement pret_a_placer)
            val donneesUpdate = mapOf("pret_a_placer" to nouveauPretAPlacer)
            val corpsRequete = gson.toJson(donneesUpdate)

            val url = "$urlBase/api/collections/${Collections.CHEQUE}/records/$compteId"

            val requete = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .patch(corpsRequete.toRequestBody("application/json".toMediaType()))
                .build()

            val reponse = httpClient.newCall(requete).execute()
            
            if (!reponse.isSuccessful) {
                val messageErreur = reponse.body?.string() ?: "Erreur inconnue"
                throw Exception("Erreur lors de la mise à jour: ${reponse.code} $messageErreur")
            }

            // 🔄 DÉCLENCHER LES ÉVÉNEMENTS DE RAFRAÎCHISSEMENT
            BudgetEvents.onCompteUpdated()

            // 🔄 DÉCLENCHER LE RAFRAÎCHISSEMENT DE LA PAGE DES COMPTES
            val realtimeService = AppModule.provideRealtimeSyncService()
            realtimeService.declencherMiseAJourComptes()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
